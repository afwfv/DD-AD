package dev.fordes.adfs.rule.model;

public enum DomainEnvelope {
    UNKNOWN("unknown"),
    EXACT("exact"),
    SUFFIX("suffix");

    private final String value;

    DomainEnvelope(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
