package dev.fordes.adfs.rule.normalize;

import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.fordes.adfs.rule.model.AdblockModifier;
import dev.fordes.adfs.rule.model.AdblockNetworkRule;
import dev.fordes.adfs.rule.model.AdblockResourceType;
import dev.fordes.adfs.rule.model.AllOf;
import dev.fordes.adfs.rule.model.AnyOf;
import dev.fordes.adfs.rule.model.CosmeticRule;
import dev.fordes.adfs.rule.model.DomainConstraint;
import dev.fordes.adfs.rule.model.DomainMatch;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.DomainPattern;
import dev.fordes.adfs.rule.model.DomainRule;
import dev.fordes.adfs.rule.model.ExactDomain;
import dev.fordes.adfs.rule.model.HostMappingRule;
import dev.fordes.adfs.rule.model.IpAddress;
import dev.fordes.adfs.rule.model.IpCidrMatch;
import dev.fordes.adfs.rule.model.IpCidrRule;
import dev.fordes.adfs.rule.model.KeywordDomain;
import dev.fordes.adfs.rule.model.MatchExpression;
import dev.fordes.adfs.rule.model.MatchSide;
import dev.fordes.adfs.rule.model.NetworkMatch;
import dev.fordes.adfs.rule.model.Not;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.PortMatch;
import dev.fordes.adfs.rule.model.ProcessMatch;
import dev.fordes.adfs.rule.model.RegexDomain;
import dev.fordes.adfs.rule.model.RouteRule;
import dev.fordes.adfs.rule.model.RuleAction;
import dev.fordes.adfs.rule.model.RuleEntry;
import dev.fordes.adfs.rule.model.DnsAddressRule;
import dev.fordes.adfs.rule.model.SafariRule;
import dev.fordes.adfs.rule.model.SuffixDomain;
import dev.fordes.adfs.rule.model.Subdomain;
import dev.fordes.adfs.rule.model.WildcardDomain;

public final class CanonicalRuleEncoder {

    private static final String VERSION = "2";

    public byte[] encode(RuleEntry entry) {
        StringBuilder value = new StringBuilder();
        append(value, VERSION);
        switch (entry) {
            case SafariRule safari -> {
                append(value, "safari");
                append(value, safari.affinity());
                append(value, new String(encode(safari.rule()), StandardCharsets.UTF_8));
            }
            case DomainRule(var pattern, RuleAction action) -> {
                append(value, "domain");
                append(value, switch (pattern) {
                    case ExactDomain _ -> "exact";
                    case SuffixDomain _ -> "suffix";
                    case Subdomain _ -> "subdomain";
                    case KeywordDomain _ -> "keyword";
                    case WildcardDomain _ -> "wildcard";
                    case RegexDomain _ -> "regex";
                });
                append(value, pattern.value());
                if (pattern instanceof WildcardDomain wildcard) {
                    append(value, wildcard.syntax().name());
                }
                append(value, action.value());
            }
            case DnsAddressRule rule -> {
                append(value, "dns-address");
                append(value, rule.format().value());
                appendExpression(value, new DomainMatch(rule.pattern()));
                append(value, rule.response().name());
                append(value, Integer.toString(rule.families().size()));
                rule.families().stream().sorted().forEach(family -> append(value, family.value()));
                append(value, Integer.toString(rule.addresses().size()));
                rule.addresses().forEach(address -> appendAddress(value, address));
            }
            case HostMappingRule(IpAddress address, DomainName hostname) -> {
                append(value, "host-mapping");
                appendAddress(value, address);
                append(value, hostname.value());
            }
            case IpCidrRule(IpAddress network, int prefixLength, RuleAction action) -> {
                append(value, "ip-cidr");
                appendAddress(value, network);
                append(value, Integer.toString(prefixLength));
                append(value, action.value());
            }
            case RouteRule(MatchExpression expression) -> {
                append(value, "route");
                appendExpression(value, expression);
            }
            case AdblockNetworkRule rule -> appendAdblockNetwork(value, rule);
            case CosmeticRule rule -> appendCosmetic(value, rule);
            case OpaqueRule(var type, var dialect, var envelope, String payload) -> {
                append(value, "opaque");
                append(value, type.value());
                append(value, dialect.value());
                append(value, envelope.value());
                append(value, payload);
            }
        }
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendAdblockNetwork(StringBuilder value, AdblockNetworkRule rule) {
        append(value, "adblock-network");
        append(value, rule.dialect().value());
        append(value, rule.pattern().kind().value());
        append(value, rule.pattern().value());
        append(value, rule.action().value());
        appendResources(value, rule.includedResourceTypes().stream().sorted().toList());
        appendResources(value, rule.excludedResourceTypes().stream().sorted().toList());
        appendDomains(value, rule.domainConstraints());
        append(value, rule.partyConstraint().value());
        append(value, Boolean.toString(rule.matchCase()));
        append(value, Boolean.toString(rule.important()));
        append(value, Integer.toString(rule.modifiers().size()));
        for (AdblockModifier modifier : rule.modifiers()) {
            append(value, modifier.type().value());
            append(value, modifier.value());
        }
    }

    private static void appendCosmetic(StringBuilder value, CosmeticRule rule) {
        append(value, "cosmetic");
        append(value, rule.dialect().value());
        appendDomains(value, rule.domains());
        append(value, Boolean.toString(rule.exception()));
        append(value, rule.operator().value());
        append(value, rule.body());
    }

    private static void appendResources(StringBuilder value, List<AdblockResourceType> resources) {
        append(value, Integer.toString(resources.size()));
        resources.forEach(resource -> append(value, resource.value()));
    }

    private static void appendDomains(StringBuilder value, List<DomainConstraint> domains) {
        append(value, Integer.toString(domains.size()));
        for (DomainConstraint domain : domains) {
            append(value, domain.domain().value());
            append(value, Boolean.toString(domain.excluded()));
        }
    }

    private static void appendAddress(StringBuilder value, IpAddress address) {
        append(value, address.family().value());
        append(value, Long.toUnsignedString(address.high()));
        append(value, Long.toUnsignedString(address.low()));
    }

    private static void appendExpression(StringBuilder value, MatchExpression expression) {
        switch (expression) {
            case DomainMatch(DomainPattern pattern) -> {
                append(value, "domain-match");
                append(value, switch (pattern) {
                    case ExactDomain _ -> "exact";
                    case SuffixDomain _ -> "suffix";
                    case Subdomain _ -> "subdomain";
                    case KeywordDomain _ -> "keyword";
                    case WildcardDomain _ -> "wildcard";
                    case RegexDomain _ -> "regex";
                });
                append(value, pattern.value());
                if (pattern instanceof WildcardDomain wildcard) {
                    append(value, wildcard.syntax().name());
                }
            }
            case IpCidrMatch(MatchSide side, IpAddress network, int prefixLength) -> {
                append(value, "ip-cidr-match");
                append(value, side.value());
                appendAddress(value, network);
                append(value, Integer.toString(prefixLength));
            }
            case PortMatch(MatchSide side, int first, int last) -> {
                append(value, "port-match");
                append(value, side.value());
                append(value, Integer.toString(first));
                append(value, Integer.toString(last));
            }
            case NetworkMatch(var network) -> {
                append(value, "network-match");
                append(value, network.value());
            }
            case ProcessMatch(var field, String fieldValue) -> {
                append(value, "process-match");
                append(value, field.value());
                append(value, fieldValue);
            }
            case AllOf(var expressions) -> appendExpressions(value, "all", expressions);
            case AnyOf(var expressions) -> appendExpressions(value, "any", expressions);
            case Not(MatchExpression child) -> {
                append(value, "not");
                appendExpression(value, child);
            }
        }
    }

    private static void appendExpressions(StringBuilder value, String type, List<MatchExpression> expressions) {
        append(value, type);
        List<String> members = expressions.stream().map(expression -> {
            StringBuilder member = new StringBuilder();
            appendExpression(member, expression);
            return member.toString();
        }).distinct().sorted().toList();
        append(value, Integer.toString(members.size()));
        members.forEach(value::append);
    }

    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }
}
