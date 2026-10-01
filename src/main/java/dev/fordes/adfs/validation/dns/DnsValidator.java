package dev.fordes.adfs.validation.dns;

import dev.fordes.adfs.config.EffectiveConfig.DnsConfig;
import dev.fordes.adfs.error.DnsException;
import dev.fordes.adfs.format.RuleConsumer;
import dev.fordes.adfs.report.ProcessingMetrics;
import dev.fordes.adfs.rule.dedup.RuleDeduplicator;
import dev.fordes.adfs.rule.model.*;
import dev.fordes.adfs.rule.spool.RuleSpool;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;

@Singleton
@Slf4j
public final class DnsValidator {


    private static final DnsResolver FAILED_RESOLVER = new DnsResolver() {
        @Override
        public DnsResult resolve(DomainName domain) {
            return DnsResult.FAILED;
        }

        @Override
        public int cacheSize() {
            return 0;
        }

    };
    private final Function<DnsConfig, DnsResolver> resolverFactory;

    public DnsValidator() {
        this(DnsjavaResolver::new);
    }

    DnsValidator(Function<DnsConfig, DnsResolver> resolverFactory) {
        this.resolverFactory = resolverFactory;
    }

    public void validate(
            RuleSpool spool,
            DnsConfig config,
            RuleDeduplicator deduplicator,
            Predicate<RuleEntry> filter,
            RuleConsumer consumer,
            ProcessingMetrics metrics) {
        DnsResolver resolver = createResolver(config);
        Semaphore permits = new Semaphore(config.concurrency(), true);
        ConcurrentHashMap<DomainName, CompletableFuture<DnsResult>> inFlight = new ConcurrentHashMap<>();
        Queue<PendingRule> window = new ArrayDeque<>(config.concurrency());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            spool.replay(entry -> {
                if (!filter.test(entry)) {
                    return;
                }
                if (!deduplicator.add(entry)) {
                    metrics.duplicate();
                    log.debug("重复规则, 已跳过:  {} --> 规则去重 | {}",
                            MDC.get(RuleSpool.INPUT), MDC.get(RuleSpool.INPUT_RULE));
                    return;
                }
                metrics.unique();
                if (window.size() == config.concurrency()) {
                    flushFirst(window, consumer, metrics);
                }
                Optional<DomainName> domain = domain(entry, config.strictMode());
                if (domain.isEmpty()) {
                    metrics.dnsSkipped();
                    window.add(new PendingRule(entry, CompletableFuture.completedFuture(DnsResult.SKIPPED),
                            log.isDebugEnabled() ? MDC.getCopyOfContextMap() : Map.of()));
                    return;
                }
                metrics.dnsChecked();
                window.add(new PendingRule(entry,
                        resolve(domain.orElseThrow(), resolver, executor, permits, inFlight, metrics),
                        log.isDebugEnabled() ? MDC.getCopyOfContextMap() : Map.of()));
            });
            while (!window.isEmpty()) {
                flushFirst(window, consumer, metrics);
            }
        }
        metrics.finishDns(resolver.cacheSize());
    }

    private DnsResolver createResolver(DnsConfig config) {
        try {
            return resolverFactory.apply(config);
        } catch (RuntimeException exception) {
            log.debug("DNS 检测初始化失败, 所有待检测规则将保留", exception);
            return FAILED_RESOLVER;
        }
    }

    private static CompletableFuture<DnsResult> resolve(
            DomainName domain,
            DnsResolver resolver,
            ExecutorService executor,
            Semaphore permits,
            ConcurrentHashMap<DomainName, CompletableFuture<DnsResult>> inFlight,
            ProcessingMetrics metrics) {
        CompletableFuture<DnsResult> promise = new CompletableFuture<>();
        CompletableFuture<DnsResult> existing = inFlight.putIfAbsent(domain, promise);
        if (existing != null) {
            metrics.dnsMerged();
            return existing;
        }
        executor.submit(() -> {
            boolean acquired = false;
            try {
                permits.acquire();
                acquired = true;
                promise.complete(resolver.resolve(domain));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                promise.complete(DnsResult.FAILED);
            } catch (RuntimeException _) {
                promise.complete(DnsResult.FAILED);
            } finally {
                if (acquired) {
                    permits.release();
                }
                inFlight.remove(domain, promise);
            }
        });
        return promise;
    }

    private static Optional<DomainName> domain(RuleEntry entry, boolean strictMode) {
        return switch (entry) {
            case SafariRule safari -> domain(safari.rule(), strictMode);
            case DomainRule(var pattern, var action) -> switch (pattern) {
                case ExactDomain(DomainName domain) when action == dev.fordes.adfs.rule.model.RuleAction.BLOCK -> Optional.of(domain);
                default -> Optional.empty();
            };
            case AdblockNetworkRule rule when !strictMode
                    && rule.action() == dev.fordes.adfs.rule.model.RuleAction.BLOCK
                    && rule.pattern().kind() == AdblockPattern.Kind.DOMAIN_ANCHOR ->
                    DomainName.tryParse(rule.pattern().value());
            case AdblockNetworkRule _ -> Optional.empty();
            case DnsAddressRule _, CosmeticRule _, HostMappingRule _, IpCidrRule _, RouteRule _, OpaqueRule _ -> Optional.empty();
        };
    }

    private static void flushFirst(Queue<PendingRule> window, RuleConsumer consumer, ProcessingMetrics metrics) {
        PendingRule pending = window.remove();
        DnsResult result;
        try {
            result = pending.result().join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof DnsException dnsException) {
                throw dnsException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new DnsException("DNS 验证任务异常", cause);
        }
        boolean logContext = log.isDebugEnabled();
        Map<String, String> previousContext = logContext ? MDC.getCopyOfContextMap() : null;
        if (logContext) {
            MDC.setContextMap(pending.logContext());
        }
        try {
            log.trace("DNS 检测结果:  {} --> DNS 检测 | {} --> {}",
                    MDC.get(RuleSpool.INPUT), MDC.get(RuleSpool.INPUT_RULE), result);
            switch (result) {
                case VALID -> {
                    metrics.dnsValid();
                    consumer.accept(pending.entry());
                }
                case INVALID -> metrics.dnsInvalid();
                case FAILED -> {
                    metrics.dnsFailed();
                    consumer.accept(pending.entry());
                }
                case SKIPPED -> consumer.accept(pending.entry());
            }
        } finally {
            if (logContext) {
                if (previousContext == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previousContext);
                }
            }
        }
    }

    record PendingRule(RuleEntry entry, CompletableFuture<DnsResult> result, Map<String, String> logContext) {
    }
}
