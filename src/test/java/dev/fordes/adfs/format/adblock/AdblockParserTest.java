package dev.fordes.adfs.format.adblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.fordes.adfs.config.EffectiveConfig.InputLimits;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.rule.model.AdblockNetworkRule;
import dev.fordes.adfs.rule.model.AdblockPattern;
import dev.fordes.adfs.rule.model.AdblockResourceType;
import dev.fordes.adfs.rule.model.CosmeticRule;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.PartyConstraint;
import dev.fordes.adfs.rule.model.RuleEntry;
import dev.fordes.adfs.testing.ParserTestSupport;
import dev.fordes.adfs.testing.ParserTestSupport.ParseOutcome;
import dev.fordes.adfs.testing.TestConfigs;

final class AdblockParserTest {

    private static final InputLimits LIMITS = new InputLimits(1_048_576, 262_144);

    @TempDir
    Path temporaryDirectory;

    @Test
    void handlesPreprocessingStructuredRulesAndPassthrough() throws IOException {
        Files.writeString(temporaryDirectory.resolve("included.txt"), "||included.example^\n");
        String source = """
                !#if adguard
                ||ads.example^$script,~image,domain=example.com|~excluded.example,third-party,important
                !#else
                ||inactive.example^
                !#endif
                example.com##.advert
                ||rewrite.example^$redirect=noopjs
                !#include included.txt
                """;

        List<RuleEntry> entries = ParserTestSupport.parse(
                temporaryDirectory.resolve("root.txt"), source,
                new AdblockParser(LIMITS, TestConfigs.rules(), RuleDialect.ADGUARD),
                RuleType.ADBLOCK, RuleDialect.ADGUARD);

        assertEquals(4, entries.size());
        AdblockNetworkRule network = assertInstanceOf(AdblockNetworkRule.class, entries.getFirst());
        assertEquals(AdblockPattern.Kind.DOMAIN_ANCHOR, network.pattern().kind());
        assertTrue(network.includedResourceTypes().contains(AdblockResourceType.SCRIPT));
        assertTrue(network.excludedResourceTypes().contains(AdblockResourceType.IMAGE));
        assertEquals(2, network.domainConstraints().size());
        assertEquals(PartyConstraint.THIRD_PARTY, network.partyConstraint());
        assertTrue(network.important());
        assertInstanceOf(CosmeticRule.class, entries.get(1));
        assertInstanceOf(OpaqueRule.class, entries.get(2));
        assertInstanceOf(AdblockNetworkRule.class, entries.get(3));
    }

    @Test
    void acceptsAbpExtendedCssOperator() throws IOException {
        List<RuleEntry> entries = ParserTestSupport.parse(
                temporaryDirectory.resolve("abp.txt"), "example.com#?#.sponsored\n",
                new AdblockParser(LIMITS, TestConfigs.rules(), RuleDialect.ABP),
                RuleType.ADBLOCK, RuleDialect.ABP);

        CosmeticRule rule = assertInstanceOf(CosmeticRule.class, entries.getFirst());
        assertEquals("#?#", rule.operator().value());
        assertEquals(RuleDialect.ABP, rule.dialect());
    }

    @Test
    void skipsAndCountsIncludeCycles() throws IOException {
        Files.writeString(temporaryDirectory.resolve("child.txt"), "!#include root.txt\n");

        ParseOutcome outcome = ParserTestSupport.parseOutcome(
                temporaryDirectory.resolve("root.txt"), "!#include child.txt\n",
                new AdblockParser(LIMITS, TestConfigs.rules(), RuleDialect.ADGUARD),
                RuleType.ADBLOCK, RuleDialect.ADGUARD);

        assertTrue(outcome.entries().isEmpty());
        assertEquals(1, outcome.metrics().invalidRules());
    }

    @Test
    void skipsInvalidOptionsAndPreservesRegexDelimiters() throws IOException {
        ParseOutcome invalidAbp = ParserTestSupport.parseOutcome(
                temporaryDirectory.resolve("invalid-abp.txt"), "||ads.example^$replace=/a/b/\n",
                new AdblockParser(LIMITS, TestConfigs.rules(), RuleDialect.ABP), RuleType.ADBLOCK, RuleDialect.ABP);
        ParseOutcome invalidOption = ParserTestSupport.parseOutcome(
                temporaryDirectory.resolve("invalid-option.txt"), "||ads.example^$script=invalid\n",
                new AdblockParser(LIMITS, TestConfigs.rules(), RuleDialect.ADGUARD),
                RuleType.ADBLOCK, RuleDialect.ADGUARD);
        assertEquals(1, invalidAbp.metrics().invalidRules());
        assertEquals(1, invalidOption.metrics().invalidRules());
        List<RuleEntry> entries = ParserTestSupport.parse(temporaryDirectory.resolve("delimiters.txt"),
                "/foo##bar$/\n||ads.example^$domain=redirect.example,script\n"
                        + "||rewrite.example^$replace=/a\\,b/c\\,d/,important\n",
                new AdblockParser(LIMITS, TestConfigs.rules(), RuleDialect.ADGUARD), RuleType.ADBLOCK, RuleDialect.ADGUARD);
        assertInstanceOf(AdblockNetworkRule.class, entries.get(0));
        assertInstanceOf(AdblockNetworkRule.class, entries.get(1));
        assertInstanceOf(OpaqueRule.class, entries.get(2));
    }

    @Test
    void acceptsUrlPathsThatStartWithSlash() throws IOException {
        List<RuleEntry> entries = ParserTestSupport.parse(temporaryDirectory.resolve("paths.txt"),
                "/ads/banner\n/advert$script\n/foo/bar/$image\n",
                new AdblockParser(LIMITS, TestConfigs.rules(), RuleDialect.ADGUARD), RuleType.ADBLOCK, RuleDialect.ADGUARD);
        assertEquals(3, entries.size());
        assertEquals(AdblockPattern.Kind.URL, assertInstanceOf(AdblockNetworkRule.class, entries.getFirst()).pattern().kind());
        assertTrue(assertInstanceOf(AdblockNetworkRule.class, entries.get(1)).includedResourceTypes()
                .contains(AdblockResourceType.SCRIPT));
    }

    @Test
    void preservesModifierExceptionsWithoutValues() throws IOException {
        List<RuleEntry> entries = ParserTestSupport.parse(temporaryDirectory.resolve("exceptions.txt"),
                "@@||example.com^$redirect-rule\n@@||example.com^$replace\n",
                new AdblockParser(LIMITS, TestConfigs.rules(), RuleDialect.ADGUARD), RuleType.ADBLOCK, RuleDialect.ADGUARD);
        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(OpaqueRule.class::isInstance));
    }

    @Test
    void preservesChainedUrlskipExpressions() throws IOException {
        String rule = "||tracking.example/click?$urlskip=/id=(.+)/ -base64 /\"url\":\"([^\"]+)\"/";
        List<RuleEntry> entries = ParserTestSupport.parse(temporaryDirectory.resolve("urlskip.txt"), rule,
                new AdblockParser(LIMITS, TestConfigs.rules(), RuleDialect.UBO), RuleType.ADBLOCK, RuleDialect.UBO);
        assertEquals(rule, assertInstanceOf(OpaqueRule.class, entries.getFirst()).payload());
    }
}
