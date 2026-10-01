package dev.fordes.adfs.rule.model;

public enum PartyConstraint {
    ANY("any"),
    FIRST_PARTY("first-party"),
    THIRD_PARTY("third-party");

    private final String value;

    PartyConstraint(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
