package dev.fordes.adfs.rule.conversion;

import java.util.List;

import dev.fordes.adfs.rule.model.AdblockNetworkRule;
import dev.fordes.adfs.rule.model.AdblockPattern;
import dev.fordes.adfs.rule.model.DnsAddressRule;
import dev.fordes.adfs.rule.model.DnsResponse;
import dev.fordes.adfs.rule.model.DomainMatch;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.DomainPattern;
import dev.fordes.adfs.rule.model.DomainRule;
import dev.fordes.adfs.rule.model.ExactDomain;
import dev.fordes.adfs.rule.model.HostMappingRule;
import dev.fordes.adfs.rule.model.MatchExpression;
import dev.fordes.adfs.rule.model.RouteRule;
import dev.fordes.adfs.rule.model.RuleAction;
import dev.fordes.adfs.rule.model.RuleEntry;
import dev.fordes.adfs.rule.model.Subdomain;
import dev.fordes.adfs.rule.model.SuffixDomain;

/** 仅对白名单与简单规则中的完整域名执行相等判断。 */
public final class WhitelistMatcher {

    private WhitelistMatcher() {
    }

    private static boolean matches(MatchExpression expression, DomainName allowed) {
        return switch (expression) {
            case DomainMatch(var pattern) -> matches(pattern, allowed);
            default -> false;
        };
    }

    private static boolean matches(DomainPattern pattern, DomainName allowed) {
        return switch (pattern) {
            case ExactDomain(var domain) -> domain.equals(allowed);
            case Subdomain(var domain) -> domain.equals(allowed);
            case SuffixDomain(var domain) -> domain.equals(allowed);
            default -> false;
        };
    }

    private static boolean matches(RuleEntry entry, DomainName allowed) {
        return switch (entry) {
            case DomainRule(var pattern, RuleAction action) -> action == RuleAction.BLOCK
                    && matches(pattern, allowed);
            case HostMappingRule(_, DomainName hostname) -> hostname.equals(allowed);
            case RouteRule(var expression) -> matches(expression, allowed);
            case AdblockNetworkRule rule -> matchesAdblock(rule, allowed);
            case DnsAddressRule rule -> rule.response() != DnsResponse.IGNORE
                    && matches(rule.pattern(), allowed);
            default -> false;
        };
    }

    public static boolean matches(RuleEntry entry, List<DomainName> allowedDomains) {
        return allowedDomains.stream().anyMatch(allowed -> matches(entry, allowed));
    }

    private static boolean matchesAdblock(AdblockNetworkRule rule, DomainName allowed) {
        return rule.action() == RuleAction.BLOCK
                && rule.pattern().kind() == AdblockPattern.Kind.DOMAIN_ANCHOR
                && new DomainName(rule.pattern().value()).equals(allowed);
    }
}
