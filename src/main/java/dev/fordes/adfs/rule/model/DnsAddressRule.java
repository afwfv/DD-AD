package dev.fordes.adfs.rule.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.fordes.adfs.config.RuleType;

/** DNS 配置赋值保留响应类型、地址族和同条记录的完整地址集合, 按输入顺序执行。 */
public record DnsAddressRule(
        RuleType format,
        DomainPattern pattern,
        DnsResponse response,
        Set<IpFamily> families,
        List<IpAddress> addresses) implements Rule {

    public DnsAddressRule {
        Objects.requireNonNull(pattern);
        Objects.requireNonNull(response);
        families = Set.copyOf(families);
        addresses = List.copyOf(addresses);
        if (format != RuleType.SMARTDNS && format != RuleType.DNSMASQ || families.isEmpty()
                || (response == DnsResponse.ADDRESS) != !addresses.isEmpty()) {
            throw new IllegalArgumentException("DNS 地址赋值的格式、响应类型或地址族不一致");
        }
        for (IpAddress address : addresses) {
            if (!families.contains(address.family())) {
                throw new IllegalArgumentException("DNS 地址不属于指定地址族");
            }
        }
        boolean validResponse = switch (format) {
            case SMARTDNS -> response == DnsResponse.SOA || response == DnsResponse.IGNORE
                    || response == DnsResponse.ADDRESS;
            case DNSMASQ -> response == DnsResponse.NXDOMAIN || response == DnsResponse.NULL_ADDRESS
                    || response == DnsResponse.ADDRESS && addresses.size() == 1;
            default -> false;
        };
        if (!validResponse) {
            throw new IllegalArgumentException("DNS 响应类型或地址数量不符合目标格式");
        }
    }
}
