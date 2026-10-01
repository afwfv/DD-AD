package dev.fordes.adfs.rule.model;

public enum MatchSide {
    SOURCE("source"),
    DESTINATION("destination");

    private final String value;

    MatchSide(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
