package dev.fordes.adfs.rule.model;

import dev.fordes.adfs.error.RuleProcessingException;

public record IpCidr(IpAddress network, int prefixLength) {

    public static IpCidr parse(String value) {
        int separator = value.lastIndexOf('/');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new RuleProcessingException("CIDR 格式非法: " + value);
        }
        IpAddress address = IpAddress.parse(value.substring(0, separator));
        int prefix;
        try {
            prefix = Integer.parseInt(value.substring(separator + 1));
        } catch (NumberFormatException exception) {
            throw new RuleProcessingException("CIDR 前缀非法: " + value, exception);
        }
        IpCidrRule normalized = new IpCidrRule(address, prefix, RuleAction.BLOCK);
        if (!normalized.network().equals(address)) {
            throw new RuleProcessingException("CIDR 包含非零 host bits: " + value);
        }
        return new IpCidr(normalized.network(), prefix);
    }

    public String text() {
        return network.text() + "/" + prefixLength;
    }
}
