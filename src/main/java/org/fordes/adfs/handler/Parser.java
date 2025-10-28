package org.fordes.adfs.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adfs.config.Config;
import org.fordes.adfs.config.InputProperties;
import org.fordes.adfs.enums.HandleType;
import org.fordes.adfs.handler.dns.DnsChecker;
import org.fordes.adfs.handler.fetch.Fetcher;
import org.fordes.adfs.handler.rule.Handler;
import org.fordes.adfs.model.Rule;
import org.fordes.adfs.util.BloomFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@AllArgsConstructor
public class Parser {

    protected final BloomFilter<Rule> filter;
    protected Config config;
    protected DnsChecker dnsChecker;

    public Flux<Rule> handle(InputProperties.Prop prop, HandleType type) {

        AtomicLong invalid = new AtomicLong(0L);
        AtomicLong repeat = new AtomicLong(0L);
        AtomicLong effective = new AtomicLong(0L);
        Set<String> exclude = Optional.ofNullable(config.getExclude()).orElseGet(Set::of);

        return Flux.just(prop)
                .flatMap(p -> {
                    Fetcher fetcher = Fetcher.getFetcher(type);
                    return fetcher.fetch(p.path());
                })
                .filter(StringUtils::hasText)
                .flatMap(line -> {
                    Handler handler = Handler.getHandler(prop.type());

                    if (handler.isComment(line)) {
                        return Mono.empty();
                    }

                    Rule rule = handler.parse(line);
                    if (Rule.EMPTY.equals(rule)) {
                        invalid.incrementAndGet();
                        log.debug("parse fail: {}", line);
                        return Mono.empty();
                    }

                    return Mono.just(rule);
                })
                .flatMap(e -> {

                    if (e.getTarget() != null && exclude.contains(e.getTarget())) {
                        log.info("exclude rule: {}", e.getOrigin());
                        return Mono.empty();
                    }

                    if (filter.contains(e)) {
                        log.debug("already exists rule: {}", e.getOrigin());
                        repeat.incrementAndGet();
                        return Mono.empty();
                    }

                    if (e.getOrigin().length() <= config.getWarnLimit()) {
                        log.warn("[{}] Suspicious rule => {}", prop.name(), e.getOrigin());
                    }

                    return Mono.just(e);

                })
                .onErrorResume(ex -> {
                    log.error(ex.getMessage(), ex);
                    return Mono.empty();
                })
                .flatMap(rule -> {

                    /**
                     * 假设有规则 ||example.org^
                     * 通过DNS查询 example.org 是否存在 A/AAAA/CNAME 记录作为判断依据
                     * 不可避免的误判是，example.org 没有有效记录，而其存在有效子域如 test.example.org
                     */
                    if (dnsChecker.getConfig().enable() && Rule.Type.BASIC.equals(rule.getType())
                            && Rule.Scope.DOMAIN.equals(rule.getScope())) {

                        return Flux.just(rule.getTarget())
                                .flatMap(e -> dnsChecker.lookup(e), 1)
                                .flatMap(e -> {
                                    if (!e) {
                                        log.debug("[{}] dns check invalid rule => {}", prop.name(), rule.getOrigin());
                                        invalid.incrementAndGet();
                                        return Mono.empty();
                                    }
                                    return Mono.just(rule);
                                });
                    }
                    return Mono.just(rule);
                }, dnsChecker.getConfig().concurrency())
                .flatMap(e -> {
                    filter.add(e);
                    effective.incrementAndGet();
                    return Mono.just(e);

                })
                .doFinally(signal -> {
                    log.info("[{}]  parser done => invalid: {}, repeat: {}, effective: {}", prop.name(),
                            invalid.get(), repeat.get(), effective.get());
                });

    }
}
