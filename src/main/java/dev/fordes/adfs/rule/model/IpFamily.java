package dev.fordes.adfs.rule.model;

public enum IpFamily {
    IPV4("ipv4"),
    IPV6("ipv6");

    private final String value;

    IpFamily(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
