package dev.fordes.adfs.rule.model;

import java.util.List;

public record AnyOf(List<MatchExpression> expressions) implements MatchExpression {

    public AnyOf {
        expressions = List.copyOf(expressions);
        if (expressions.isEmpty()) {
            throw new IllegalArgumentException("AnyOf 至少需要一个表达式");
        }
    }
}
