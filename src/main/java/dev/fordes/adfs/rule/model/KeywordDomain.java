package dev.fordes.adfs.rule.model;

import java.util.Locale;

public record KeywordDomain(String value) implements DomainPattern {

    public KeywordDomain {
        value = value.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("域名关键词不得为空");
        }
    }
}
