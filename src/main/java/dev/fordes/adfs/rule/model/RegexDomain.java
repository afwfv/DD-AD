package dev.fordes.adfs.rule.model;

public record RegexDomain(String value) implements DomainPattern {

    public RegexDomain {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("域名正则不得为空");
        }
    }
}
