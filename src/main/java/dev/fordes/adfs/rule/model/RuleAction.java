package dev.fordes.adfs.rule.model;

public enum RuleAction {
    BLOCK("block"),
    ALLOW("allow");

    private final String value;

    RuleAction(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
