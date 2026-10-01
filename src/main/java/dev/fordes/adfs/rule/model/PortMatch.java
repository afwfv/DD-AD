package dev.fordes.adfs.rule.model;

public record PortMatch(MatchSide side, int first, int last) implements MatchExpression {

    public PortMatch {
        if (first < 0 || last > 65_535 || first > last) {
            throw new IllegalArgumentException("端口范围非法");
        }
    }
}
