package org.fordes.adfs.handler;

import bloomfilter.mutable.BloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adfs.config.AdFSProperties;
import org.fordes.adfs.constant.Constants;
import org.fordes.adfs.enums.HandleType;
import org.fordes.adfs.handler.dns.DnsDetector;
import org.fordes.adfs.handler.fetch.Fetcher;
import org.fordes.adfs.handler.rule.Handler;
import org.fordes.adfs.model.Rule;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.fordes.adfs.config.AdFSProperties.Config;
import static org.fordes.adfs.config.AdFSProperties.InputProperties;

@Slf4j
@Component
public class Parser {

    protected final BloomFilter<Rule> filter;
    protected final Config config;
    protected final DnsDetector detector;
    protected final Tracker tracker;

    public Parser(AdFSProperties properties, Optional<DnsDetector> detector,
                  Optional<Tracker> tracker) {

        this.config = properties.getConfig();
        this.detector = detector.orElse(null);
        this.tracker = tracker.orElse(null);
        this.filter = BloomFilter.apply(config.expectedQuantity(), config.faultTolerance(), rule -> rule.hashCode());
    }

    public Flux<Rule> handle(InputProperties prop) {
        if (prop.path().startsWith("http")) {
            return this.handle(prop, HandleType.REMOTE);
        }
        return this.handle(prop, HandleType.LOCAL);
    }

    public Flux<Rule> handle(InputProperties prop, HandleType type) {

        AtomicLong total = new AtomicLong(0L);
        AtomicLong invalid = new AtomicLong(0L);
        AtomicLong repeat = new AtomicLong(0L);
        AtomicLong effective = new AtomicLong(0L);
        Set<String> exclude = Optional.ofNullable(config.exclude()).orElseGet(Set::of);
        long start = System.currentTimeMillis();

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
                    total.incrementAndGet();
                    if (Rule.EMPTY.equals(rule)) {
                        invalid.incrementAndGet();
                        log.debug("[{}] parse fail: {}", prop.name(), line);
                        if (tracker != null) {
                            tracker.writeSync(Constants.Collector.PARSER, prop.name(), line);
                        }
                        return Mono.empty();
                    }

                    rule.setSourceName(prop.name());
                    return Mono.just(rule);
                })
                .flatMap(e -> {

                    if (e.getTarget() != null && exclude.contains(e.getTarget())) {
                        log.info("exclude rule: {}", e.getOrigin());
                        return Mono.empty();
                    }

                    if (filter.mightContain(e)) {
                        log.debug("already exists rule: {}", e.getOrigin());
                        repeat.incrementAndGet();
                        return Mono.empty();
                    }

                    if (e.getOrigin().length() <= config.warnLimit()) {
                        log.warn("[{}] suspicious rule => {}", prop.name(), e.getOrigin());
                    }

                    return Mono.just(e);

                })
                .onErrorResume(ex -> {
                    log.error("Parse {} failed", prop.name(), ex);
                    return Mono.empty();
                })
                .flatMap(rule -> {

                    /**
                     * 假设有规则 ||example.org^
                     * 通过DNS查询 example.org 是否存在 A/AAAA/CNAME 记录作为判断依据
                     * 不可避免的误判是，example.org 没有有效记录，而其存在有效子域如 test.example.org
                     */
                    if (detector != null && Rule.Type.BASIC.equals(rule.getType())
                            && Rule.Scope.DOMAIN.equals(rule.getScope())) {

                        return Flux.just(rule.getTarget())
                                .flatMap(e -> detector.lookup(e), 1)
                                .flatMap(e -> {
                                    if (!e) {
                                        invalid.incrementAndGet();
                                        log.debug("[{}] dns check invalid rule => {}", prop.name(), rule.getOrigin());
                                        if (tracker != null) {
                                            tracker.writeSync(Constants.Collector.DNS_CHECK,prop.name(), rule.getOrigin());
                                        }
                                        return Mono.empty();
                                    }
                                    return Mono.just(rule);
                                });
                    }
                    return Mono.just(rule);
                }, config.domainDetect().concurrency())
                .flatMap(e -> {
                    filter.add(e);
                    effective.incrementAndGet();
                    return Mono.just(e);

                })
                .doFinally(signal -> {
                    log.info("[{}] parsing cost {} ms, total: {}, effective: {}, repeat: {}, invalid: {}",
                            prop.name(), System.currentTimeMillis() - start,
                            total.get(), effective.get(), repeat.get(), invalid.get());
                });

    }
}
