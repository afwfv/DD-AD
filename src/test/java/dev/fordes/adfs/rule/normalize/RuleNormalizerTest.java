package dev.fordes.adfs.rule.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.fordes.adfs.rule.model.AllOf;
import dev.fordes.adfs.rule.model.DomainMatch;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.DomainRule;
import dev.fordes.adfs.rule.model.ExactDomain;
import dev.fordes.adfs.rule.model.IpAddress;
import dev.fordes.adfs.rule.model.IpCidrMatch;
import dev.fordes.adfs.rule.model.IpCidrRule;
import dev.fordes.adfs.rule.model.MatchSide;
import dev.fordes.adfs.rule.model.Not;
import dev.fordes.adfs.rule.model.PortMatch;
import dev.fordes.adfs.rule.model.RouteRule;
import dev.fordes.adfs.rule.model.RuleAction;
import dev.fordes.adfs.rule.model.RuleEntry;

final class RuleNormalizerTest {

    @Test
    void flattensSortsAndDeduplicatesCommutativeExpressions() {
        PortMatch port = new PortMatch(MatchSide.DESTINATION, 443, 443);
        DomainMatch domain = new DomainMatch(new ExactDomain(new DomainName("example.com")));
        RouteRule input = new RouteRule(new AllOf(List.of(port, new AllOf(List.of(domain, port)))));

        AllOf normalized = assertInstanceOf(AllOf.class,
                assertInstanceOf(RouteRule.class, RuleNormalizer.normalize(input)).expression());

        assertEquals(List.of(port, domain), normalized.expressions());
    }

    @Test
    void collapsesRedundantLogicalAndSimpleRouteRules() {
        DomainMatch domain = new DomainMatch(new ExactDomain(new DomainName("example.com")));
        RuleEntry domainRule = RuleNormalizer.normalize(new RouteRule(new Not(new Not(domain))));
        RuleEntry cidrRule = RuleNormalizer.normalize(new RouteRule(
                new IpCidrMatch(MatchSide.DESTINATION, IpAddress.parse("192.0.2.1"), 24)));
        RuleEntry sourceCidr = RuleNormalizer.normalize(new RouteRule(
                new IpCidrMatch(MatchSide.SOURCE, IpAddress.parse("192.0.2.1"), 24)));

        assertEquals(new DomainRule(new ExactDomain(new DomainName("example.com")), RuleAction.BLOCK), domainRule);
        assertEquals(new IpCidrRule(IpAddress.parse("192.0.2.0"), 24, RuleAction.BLOCK), cidrRule);
        assertInstanceOf(RouteRule.class, sourceCidr);
    }
}
