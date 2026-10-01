package dev.fordes.adfs.rule.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.format.conversion.AdblockDomainConversion;
import dev.fordes.adfs.format.conversion.RegexCompatibility;
import dev.fordes.adfs.rule.model.AdblockNetworkRule;
import dev.fordes.adfs.rule.model.AdblockPattern;
import dev.fordes.adfs.rule.model.AdblockResourceType;
import dev.fordes.adfs.rule.model.PartyConstraint;
import dev.fordes.adfs.rule.model.RuleAction;

final class ConversionContractTest {

    @Test
    void combinesAndAuthorizesConversionScopes() {
        ConversionPolicy strict = new ConversionPolicy(false, false);
        ConversionPolicy permissive = new ConversionPolicy(true, true);

        assertEquals(ConversionScope.MIXED, ConversionScope.EXPANDED.combine(ConversionScope.REDUCED));
        assertEquals(ConversionScope.UNSUPPORTED, ConversionScope.EXACT.combine(ConversionScope.UNSUPPORTED));
        assertTrue(strict.allows(ConversionScope.EXACT));
        assertFalse(strict.allows(ConversionScope.EXPANDED));
        assertFalse(strict.allows(ConversionScope.REDUCED));
        assertFalse(strict.allows(ConversionScope.MIXED));
        assertFalse(permissive.allows(ConversionScope.UNSUPPORTED));
        assertTrue(permissive.allows(ConversionScope.MIXED));
    }

    @Test
    void acceptsOnlyPortableBoundedRegexSyntax() {
        assertEquals("(?i:^ads\\d+\\.example$)",
                RegexCompatibility.adblockToDomain("^ads\\d+\\.example$", false).orElseThrow());
        assertEquals("^ads\\d+\\.example$",
                RegexCompatibility.adblockToDomain("^ads\\d+\\.example$", true).orElseThrow());
        assertTrue(RegexCompatibility.adblockToDomain("(?=lookahead)", true).isEmpty());
        assertTrue(RegexCompatibility.adblockToDomain("a{1001}", true).isEmpty());
        assertTrue(RegexCompatibility.adblockToDomain("[unterminated", true).isEmpty());
    }

    @Test
    void reportsAdblockConstraintAndPriorityLosses() {
        AdblockNetworkRule rule = new AdblockNetworkRule(
                new AdblockPattern(AdblockPattern.Kind.DOMAIN_ANCHOR, "ads.example"), RuleAction.BLOCK,
                Set.of(AdblockResourceType.SCRIPT), Set.of(), List.of(), PartyConstraint.ANY,
                false, true, List.of(), RuleDialect.ADGUARD);

        ConversionDecision decision = AdblockDomainConversion.decide(rule, false,
                new ConversionDecision(ConversionScope.EXACT, "基础转换"));

        assertEquals(ConversionScope.MIXED, decision.scope());
        assertEquals(Set.of(ConversionLoss.DROPPED_REQUEST_CONSTRAINT, ConversionLoss.DROPPED_PRIORITY),
                decision.losses());
    }
}
