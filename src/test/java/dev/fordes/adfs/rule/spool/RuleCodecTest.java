package dev.fordes.adfs.rule.spool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.rule.model.AdblockModifier;
import dev.fordes.adfs.rule.model.AdblockNetworkRule;
import dev.fordes.adfs.rule.model.AdblockPattern;
import dev.fordes.adfs.rule.model.AdblockResourceType;
import dev.fordes.adfs.rule.model.AllOf;
import dev.fordes.adfs.rule.model.AnyOf;
import dev.fordes.adfs.rule.model.CosmeticOperator;
import dev.fordes.adfs.rule.model.CosmeticRule;
import dev.fordes.adfs.rule.model.DnsAddressRule;
import dev.fordes.adfs.rule.model.DnsResponse;
import dev.fordes.adfs.rule.model.DomainConstraint;
import dev.fordes.adfs.rule.model.DomainEnvelope;
import dev.fordes.adfs.rule.model.DomainMatch;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.DomainRule;
import dev.fordes.adfs.rule.model.ExactDomain;
import dev.fordes.adfs.rule.model.HostMappingRule;
import dev.fordes.adfs.rule.model.IpAddress;
import dev.fordes.adfs.rule.model.IpCidrMatch;
import dev.fordes.adfs.rule.model.IpCidrRule;
import dev.fordes.adfs.rule.model.IpFamily;
import dev.fordes.adfs.rule.model.KeywordDomain;
import dev.fordes.adfs.rule.model.MatchSide;
import dev.fordes.adfs.rule.model.NetworkMatch;
import dev.fordes.adfs.rule.model.Not;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.PartyConstraint;
import dev.fordes.adfs.rule.model.PortMatch;
import dev.fordes.adfs.rule.model.ProcessMatch;
import dev.fordes.adfs.rule.model.RegexDomain;
import dev.fordes.adfs.rule.model.RouteRule;
import dev.fordes.adfs.rule.model.RuleAction;
import dev.fordes.adfs.rule.model.RuleEntry;
import dev.fordes.adfs.rule.model.SafariRule;
import dev.fordes.adfs.rule.model.Subdomain;
import dev.fordes.adfs.rule.model.SuffixDomain;
import dev.fordes.adfs.rule.model.WildcardDomain;
import dev.fordes.adfs.rule.model.WildcardSyntax;

final class RuleCodecTest {

    private final RuleCodec codec = new RuleCodec();

    @Test
    void roundTripsEveryRuleAndExpressionShape() {
        List<RuleEntry> entries = fixtures();

        assertEquals(entries, entries.stream().map(codec::encode).map(codec::decode).toList());
    }

    @Test
    void rejectsUnknownTruncatedAndTrailingData() {
        byte[] encoded = codec.encode(new DomainRule(
                new ExactDomain(new DomainName("example.com")), RuleAction.BLOCK));
        byte[] truncated = java.util.Arrays.copyOf(encoded, encoded.length - 1);
        byte[] trailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);

        assertThrows(RuleProcessingException.class, () -> codec.decode(new byte[] {0}));
        assertThrows(RuleProcessingException.class, () -> codec.decode(truncated));
        assertThrows(RuleProcessingException.class, () -> codec.decode(trailing));
    }

    private static List<RuleEntry> fixtures() {
        DomainName example = new DomainName("example.com");
        IpAddress ipv4 = IpAddress.parse("192.0.2.1");
        AdblockNetworkRule network = new AdblockNetworkRule(
                new AdblockPattern(AdblockPattern.Kind.DOMAIN_ANCHOR, "ads.example"), RuleAction.BLOCK,
                Set.of(AdblockResourceType.SCRIPT), Set.of(AdblockResourceType.IMAGE),
                List.of(new DomainConstraint(example, false)), PartyConstraint.THIRD_PARTY,
                true, true, List.of(new AdblockModifier(AdblockModifier.Type.REDIRECT, "noopjs")),
                RuleDialect.ADGUARD);
        RouteRule route = new RouteRule(new AllOf(List.of(
                new AnyOf(List.of(new DomainMatch(new SuffixDomain(example)),
                        new IpCidrMatch(MatchSide.DESTINATION, ipv4, 24))),
                new PortMatch(MatchSide.SOURCE, 53, 53),
                new NetworkMatch(NetworkMatch.Network.UDP),
                new ProcessMatch(ProcessMatch.ProcessField.NAME, "resolver"),
                new Not(new DomainMatch(new ExactDomain(new DomainName("allow.example")))))));
        CosmeticRule cosmetic = new CosmeticRule(List.of(new DomainConstraint(example, false)), false,
                CosmeticOperator.ELEMENT_HIDE, ".advert", RuleDialect.ADGUARD);

        return List.of(
                new DomainRule(new ExactDomain(example), RuleAction.BLOCK),
                new DomainRule(new Subdomain(example), RuleAction.ALLOW),
                new DomainRule(new SuffixDomain(example), RuleAction.BLOCK),
                new DomainRule(new KeywordDomain("advert"), RuleAction.BLOCK),
                new DomainRule(new WildcardDomain("*.example.com", WildcardSyntax.MIHOMO_DOMAIN), RuleAction.BLOCK),
                new DomainRule(new RegexDomain("^ads\\."), RuleAction.BLOCK),
                new HostMappingRule(ipv4, example),
                new IpCidrRule(ipv4, 24, RuleAction.ALLOW),
                new DnsAddressRule(RuleType.SMARTDNS, new SuffixDomain(example), DnsResponse.ADDRESS,
                        Set.of(IpFamily.IPV4), List.of(ipv4)),
                route,
                network,
                cosmetic,
                new OpaqueRule(RuleType.DNSMASQ, RuleDialect.NONE, DomainEnvelope.SUFFIX,
                        "server=/example.com/1.1.1.1"),
                new SafariRule(network, Set.of("general", "privacy")));
    }
}
