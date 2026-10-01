package dev.fordes.adfs.config;

import java.util.Locale;

import dev.fordes.adfs.error.ConfigurationException;

public enum RuleType {
    ADBLOCK("adblock"),
    DNS("dns"),
    HOSTS("hosts"),
    DNSMASQ("dnsmasq"),
    MIHOMO("mihomo"),
    SING_BOX("sing-box"),
    SMARTDNS("smartdns");

    private final String value;

    RuleType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static RuleType parse(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        for (RuleType type : values()) {
            if (type.value.equals(normalized)) {
                return type;
            }
        }
        throw new ConfigurationException("未知规则类型: " + value);
    }
}
