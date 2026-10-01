package dev.fordes.adfs.rule.model;

/** 匹配任意层级子域, 不包含根域名。 */
public record Subdomain(DomainName domain) implements DomainPattern {

    @Override
    public String value() {
        return domain.value();
    }
}
