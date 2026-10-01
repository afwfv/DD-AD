package dev.fordes.adfs.rule.dedup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.rule.model.DomainEnvelope;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.DomainRule;
import dev.fordes.adfs.rule.model.ExactDomain;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.RuleAction;

final class RuleDeduplicatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void deduplicatesCanonicalRulesButPreservesOrderedFormats() {
        DomainRule rule = new DomainRule(new ExactDomain(new DomainName("example.com")), RuleAction.BLOCK);
        OpaqueRule ordered = new OpaqueRule(RuleType.DNSMASQ, RuleDialect.NONE,
                DomainEnvelope.UNKNOWN, "server=/example.com/1.1.1.1");

        try (CanonicalStore store = new CanonicalStore(temporaryDirectory.resolve("rules.bin"))) {
            RuleDeduplicator deduplicator = new RuleDeduplicator(store);
            assertTrue(deduplicator.add(rule));
            assertFalse(deduplicator.add(rule));
            assertTrue(deduplicator.add(ordered));
            assertTrue(deduplicator.add(ordered));
            assertEquals(1, deduplicator.size());
        }
    }

    @Test
    void countsSetLikeAndOrderedOutputRecords() {
        byte[] rule = "example.com".getBytes(StandardCharsets.UTF_8);

        try (CanonicalStore store = new CanonicalStore(temporaryDirectory.resolve("outputs.bin"))) {
            OutputDeduplicator deduplicator = new OutputDeduplicator(store);
            assertTrue(deduplicator.add(rule));
            assertFalse(deduplicator.add(rule));
            deduplicator.recordOrdered();
            deduplicator.recordOrdered();
            assertEquals(3, deduplicator.size());
        }
    }
}
