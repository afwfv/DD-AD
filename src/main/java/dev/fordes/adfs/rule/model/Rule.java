package dev.fordes.adfs.rule.model;

public sealed interface Rule extends RuleEntry permits AdblockNetworkRule, CosmeticRule, DomainRule, HostMappingRule,
        IpCidrRule, RouteRule, DnsAddressRule {
}
