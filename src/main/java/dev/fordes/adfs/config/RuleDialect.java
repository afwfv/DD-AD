package dev.fordes.adfs.config;

import java.util.Locale;

import dev.fordes.adfs.error.ConfigurationException;

public enum RuleDialect {
    NONE("none"),
    CORE("core"),
    ADGUARD("adguard"),
    ABP("abp"),
    UBO("ubo"),
    CLASSICAL("classical"),
    DOMAIN("domain"),
    IPCIDR("ipcidr");

    private final String value;

    RuleDialect(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static RuleDialect parse(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        for (RuleDialect dialect : values()) {
            if (dialect.value.equals(normalized)) {
                return dialect;
            }
        }
        throw new ConfigurationException("未知规则方言: " + value);
    }
}
