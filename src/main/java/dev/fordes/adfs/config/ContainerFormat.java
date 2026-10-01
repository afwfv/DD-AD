package dev.fordes.adfs.config;

import java.util.Locale;

import dev.fordes.adfs.error.ConfigurationException;

public enum ContainerFormat {
    TEXT("text"),
    YAML("yaml"),
    JSON("json");

    private final String value;

    ContainerFormat(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ContainerFormat parse(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        for (ContainerFormat format : values()) {
            if (format.value.equals(normalized)) {
                return format;
            }
        }
        throw new ConfigurationException("未知容器格式: " + value);
    }
}
