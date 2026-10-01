package dev.fordes.adfs.rule.model;

public record IpCidrRule(IpAddress network, int prefixLength, RuleAction action) implements Rule {

    public IpCidrRule {
        int maximum = network.family() == IpFamily.IPV4 ? 32 : 128;
        if (prefixLength < 0 || prefixLength > maximum) {
            throw new IllegalArgumentException("CIDR 前缀长度越界");
        }
        network = network.mask(prefixLength);
    }
}
