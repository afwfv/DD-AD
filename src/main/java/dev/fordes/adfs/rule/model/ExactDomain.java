package dev.fordes.adfs.rule.model;

public record ExactDomain(DomainName domain) implements DomainPattern {

    @Override
    public String value() {
        return domain.value();
    }
}
