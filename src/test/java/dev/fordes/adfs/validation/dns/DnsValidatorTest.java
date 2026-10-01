package dev.fordes.adfs.validation.dns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.fordes.adfs.config.EffectiveConfig.DnsCacheConfig;
import dev.fordes.adfs.config.EffectiveConfig.DnsConfig;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.report.ProcessingMetrics;
import dev.fordes.adfs.rule.dedup.CanonicalStore;
import dev.fordes.adfs.rule.dedup.RuleDeduplicator;
import dev.fordes.adfs.rule.model.AdblockNetworkRule;
import dev.fordes.adfs.rule.model.AdblockPattern;
import dev.fordes.adfs.rule.model.DomainEnvelope;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.DomainRule;
import dev.fordes.adfs.rule.model.ExactDomain;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.PartyConstraint;
import dev.fordes.adfs.rule.model.RuleAction;
import dev.fordes.adfs.rule.model.RuleEntry;
import dev.fordes.adfs.rule.model.SuffixDomain;
import dev.fordes.adfs.rule.spool.RuleSpool;

final class DnsValidatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void preservesOrderSkipsSuffixAndDropsInvalidDomains() {
        CountDownLatch release = new CountDownLatch(1);
        ControlledResolver resolver = new ControlledResolver(release);
        DnsValidator validator = new DnsValidator(_ -> resolver);
        DnsConfig config = config(false);
        ProcessingMetrics metrics = new ProcessingMetrics(1);
        List<RuleEntry> accepted = new ArrayList<>();
        Path spoolPath = temporaryDirectory.resolve("rules.bin");
        Path canonicalPath = temporaryDirectory.resolve("canonical.bin");
        try (RuleSpool spool = new RuleSpool(spoolPath, 1_024);
                CanonicalStore store = new CanonicalStore(canonicalPath)) {
            spool.accept(new DomainRule(new ExactDomain(new DomainName("valid.example")), RuleAction.BLOCK));
            spool.accept(new DomainRule(new SuffixDomain(new DomainName("valid.example")), RuleAction.BLOCK));
            spool.accept(new DomainRule(new ExactDomain(new DomainName("invalid.example")), RuleAction.BLOCK));
            spool.accept(new DomainRule(new ExactDomain(new DomainName("failure.example")), RuleAction.BLOCK));
            spool.accept(new OpaqueRule(RuleType.ADBLOCK, RuleDialect.ADGUARD,
                    DomainEnvelope.UNKNOWN, "/opaque/"));
            validator.validate(spool, config, new RuleDeduplicator(store), _ -> true, accepted::add, metrics);
        }

        assertEquals(4, accepted.size());
        assertEquals("valid.example", ((DomainRule) accepted.get(0)).pattern().value());
        assertEquals("valid.example", ((DomainRule) accepted.get(1)).pattern().value());
        assertEquals("failure.example", ((DomainRule) accepted.get(2)).pattern().value());
        assertTrue(accepted.get(3) instanceof OpaqueRule);
        assertEquals(3, metrics.snapshot().dns().checked());
        assertEquals(1, metrics.snapshot().dns().valid());
        assertEquals(1, metrics.snapshot().dns().invalid());
        assertEquals(1, metrics.snapshot().dns().failed());
        assertEquals(2, metrics.snapshot().dns().skipped());
        assertEquals(0, metrics.snapshot().dns().merged());
        assertEquals(3, resolver.calls());
    }

    @Test
    void validatesAdblockDomainAnchorsOnlyOutsideStrictMode() {
        AdblockNetworkRule blocking = adblockRule(AdblockPattern.Kind.DOMAIN_ANCHOR,
                "ads.example", RuleAction.BLOCK);
        AdblockNetworkRule allowed = adblockRule(AdblockPattern.Kind.DOMAIN_ANCHOR,
                "allow.example", RuleAction.ALLOW);
        AdblockNetworkRule urlRule = adblockRule(AdblockPattern.Kind.URL,
                "https://url.example", RuleAction.BLOCK);

        ValidationResult relaxed = validate(List.of(blocking, allowed, urlRule), false);
        ValidationResult strict = validate(List.of(blocking, allowed, urlRule), true);

        assertEquals(List.of(allowed, urlRule), relaxed.accepted());
        assertEquals(1, relaxed.resolver().calls());
        assertEquals(1, relaxed.metrics().snapshot().dns().checked());
        assertEquals(1, relaxed.metrics().snapshot().dns().invalid());
        assertEquals(2, relaxed.metrics().snapshot().dns().skipped());
        assertEquals(List.of(blocking, allowed, urlRule), strict.accepted());
        assertEquals(0, strict.resolver().calls());
        assertEquals(0, strict.metrics().snapshot().dns().checked());
        assertEquals(3, strict.metrics().snapshot().dns().skipped());
    }

    @Test
    void preservesRulesWhenResolverInitializationFails() {
        DomainRule rule = new DomainRule(
                new ExactDomain(new DomainName("failure.example")), RuleAction.BLOCK);
        DnsValidator validator = new DnsValidator(_ -> {
            throw new IllegalStateException("测试 DNS 初始化失败");
        });
        ProcessingMetrics metrics = new ProcessingMetrics(1);
        List<RuleEntry> accepted = new ArrayList<>();
        try (RuleSpool spool = new RuleSpool(temporaryDirectory.resolve("init-failure-rules.bin"), 1_024);
                CanonicalStore store = new CanonicalStore(
                        temporaryDirectory.resolve("init-failure-canonical.bin"))) {
            spool.accept(rule);
            validator.validate(spool, config(false), new RuleDeduplicator(store),
                    _ -> true, accepted::add, metrics);
        }

        assertEquals(List.of(rule), accepted);
        assertEquals(1, metrics.snapshot().dns().checked());
        assertEquals(1, metrics.snapshot().dns().failed());
    }

    private ValidationResult validate(List<RuleEntry> entries, boolean strictMode) {
        ControlledResolver resolver = new ControlledResolver(new CountDownLatch(0));
        DnsValidator validator = new DnsValidator(_ -> resolver);
        ProcessingMetrics metrics = new ProcessingMetrics(1);
        List<RuleEntry> accepted = new ArrayList<>();
        Path spoolPath = temporaryDirectory.resolve("rules-" + strictMode + ".bin");
        Path canonicalPath = temporaryDirectory.resolve("canonical-" + strictMode + ".bin");
        try (RuleSpool spool = new RuleSpool(spoolPath, 1_024);
                CanonicalStore store = new CanonicalStore(canonicalPath)) {
            entries.forEach(spool::accept);
            validator.validate(spool, config(strictMode), new RuleDeduplicator(store),
                    _ -> true, accepted::add, metrics);
        }
        return new ValidationResult(List.copyOf(accepted), metrics, resolver);
    }

    private static DnsConfig config(boolean strictMode) {
        return new DnsConfig(true, strictMode, List.of(), 4, Duration.ofSeconds(1), 0, 4,
                new DnsCacheConfig(1_024, Duration.ofMinutes(1), Duration.ofSeconds(30)));
    }

    private static AdblockNetworkRule adblockRule(
            AdblockPattern.Kind kind, String value, RuleAction action) {
        return new AdblockNetworkRule(new AdblockPattern(kind, value), action,
                Set.of(), Set.of(), List.of(), PartyConstraint.ANY,
                false, false, List.of(), RuleDialect.ADGUARD);
    }

    private record ValidationResult(
            List<RuleEntry> accepted, ProcessingMetrics metrics, ControlledResolver resolver) {
    }

    private static final class ControlledResolver implements DnsResolver {

        private final CountDownLatch release;
        private final AtomicInteger calls = new AtomicInteger();

        private ControlledResolver(CountDownLatch release) {
            this.release = release;
        }

        @Override
        public DnsResult resolve(DomainName domain) {
            calls.incrementAndGet();
            if (domain.value().equals("valid.example")) {
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("等待测试解析器释放超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("测试解析器被中断", exception);
                }
                return DnsResult.VALID;
            }
            release.countDown();
            if (domain.value().equals("failure.example")) {
                throw new IllegalStateException("测试 DNS 查询失败");
            }
            return DnsResult.INVALID;
        }

        @Override
        public int cacheSize() {
            return 2;
        }

        private int calls() {
            return calls.get();
        }
    }
}
