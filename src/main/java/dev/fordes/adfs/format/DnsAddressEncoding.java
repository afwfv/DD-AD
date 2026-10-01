package dev.fordes.adfs.format;

import java.util.Optional;

import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.rule.model.DnsAddressRule;
import dev.fordes.adfs.rule.model.ExactDomain;
import dev.fordes.adfs.rule.model.IpAddress;
import dev.fordes.adfs.rule.model.IpFamily;
import dev.fordes.adfs.rule.model.Subdomain;
import dev.fordes.adfs.rule.model.SuffixDomain;
import dev.fordes.adfs.rule.model.WildcardDomain;
import dev.fordes.adfs.rule.model.WildcardSyntax;

/** 原生响应赋值只在能够保留响应语义的格式中编码。 */
public final class DnsAddressEncoding {

    private DnsAddressEncoding() {
    }

    public static Optional<String> encode(DnsAddressRule rule, RuleType target) {
        if (rule.format() != target) {
            return Optional.empty();
        }
        String selector = switch (rule.pattern()) {
            case ExactDomain exact when target == RuleType.SMARTDNS -> "-." + exact.value();
            case SuffixDomain suffix -> suffix.value();
            case Subdomain sub -> "*." + sub.value();
            case WildcardDomain wildcard when target == RuleType.SMARTDNS
                    && wildcard.syntax() == WildcardSyntax.SMARTDNS -> wildcard.value();
            default -> "";
        };
        if (selector.isEmpty()) {
            return Optional.empty();
        }
        String family = rule.families().size() == 2 ? "" : rule.families().contains(IpFamily.IPV4) ? "4" : "6";
        String value = switch (rule.response()) {
            case NXDOMAIN -> "";
            case NULL_ADDRESS -> family.isEmpty() ? "#" : family.equals("4") ? "0.0.0.0" : "::";
            case SOA -> "#" + family;
            case IGNORE -> "-" + family;
            case ADDRESS -> String.join(",", rule.addresses().stream().map(IpAddress::text).toList());
        };
        return Optional.of((target == RuleType.SMARTDNS ? "address /" : "address=/") + selector + "/" + value);
    }
}
