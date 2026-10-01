package dev.fordes.adfs.format.singbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.fordes.adfs.config.EffectiveConfig.RuleConfig;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.rule.model.AllOf;
import dev.fordes.adfs.rule.model.AnyOf;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.RouteRule;
import dev.fordes.adfs.rule.model.RuleEntry;
import dev.fordes.adfs.testing.ParserTestSupport;
import dev.fordes.adfs.testing.ParserTestSupport.ParseOutcome;
import dev.fordes.adfs.testing.TestConfigs;

final class SingBoxParserTest {

    @TempDir
    Path directory;

    @Test
    void groupsDestinationAndPortAlternativesBeforeConjunction() throws IOException {
        List<RuleEntry> entries = parse("""
                {"version":3,"rules":[{"domain":"ads.example","domain_suffix":"other.example",
                "port":443,"port_range":"8000:9000","network":"tcp"}]}
                """, TestConfigs.rules());
        AllOf expression = assertInstanceOf(AllOf.class, assertInstanceOf(RouteRule.class, entries.getFirst()).expression());
        assertEquals(3, expression.expressions().size());
        assertEquals(2, expression.expressions().stream().filter(AnyOf.class::isInstance).count());
    }

    @Test
    void preservesUnknownChildrenButRejectsInvalidKnownCombinations() throws IOException {
        assertInstanceOf(OpaqueRule.class, parse("""
                {"version":3,"rules":[{"type":"logical","mode":"and","rules":[{"geoip":"cn"}]}]}
                """, TestConfigs.rules()).getFirst());
        assertInvalidRule("""
                {"version":3,"rules":[{"type":"logical","mode":"invalid","rules":[{"geoip":"cn"}]}]}
                """, TestConfigs.rules());
        assertInvalidRule("""
                {"version":3,"rules":[{"type":"default","mode":"and","geoip":"cn"}]}
                """, TestConfigs.rules());
    }

    @Test
    void skipsFractionalPortsAndDuplicateFields() throws IOException {
        assertInvalidRule("{\"version\":3,\"rules\":[{\"port\":80.5}]}", TestConfigs.rules());
        assertInvalidRule("{\"version\":3,\"rules\":[{\"port\":80,\"port\":443}]}", TestConfigs.rules());
    }

    @Test
    void boundsAggregateRuleSizeWithManySmallValues() throws IOException {
        RuleConfig rules = new RuleConfig(1, 128, Set.of(), TestConfigs.rules().preprocessor());
        String json = "{\"version\":3,\"rules\":[{\"domain\":[" + "\"a.example\",".repeat(1_000)
                + "\"b.example\"]}]}";
        ParseOutcome outcome = parseOutcome(json, rules);
        assertTrue(outcome.entries().isEmpty());
        assertTrue(outcome.metrics().invalidRules() > 0);
    }

    private List<RuleEntry> parse(String json, RuleConfig rules) throws IOException {
        return ParserTestSupport.parse(directory.resolve("rules.json"), json,
                new SingBoxParser(rules), RuleType.SING_BOX, RuleDialect.NONE);
    }

    private void assertInvalidRule(String json, RuleConfig rules) throws IOException {
        ParseOutcome outcome = parseOutcome(json, rules);
        assertTrue(outcome.entries().isEmpty());
        assertEquals(1, outcome.metrics().invalidRules());
    }

    private ParseOutcome parseOutcome(String json, RuleConfig rules) throws IOException {
        return ParserTestSupport.parseOutcome(directory.resolve("invalid-rules.json"), json,
                new SingBoxParser(rules), RuleType.SING_BOX, RuleDialect.NONE);
    }
}
