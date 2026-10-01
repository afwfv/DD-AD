package dev.fordes.adfs.rule.model;

public record HostMappingRule(IpAddress address, DomainName hostname) implements Rule {
}
