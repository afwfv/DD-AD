package dev.fordes.adfs.rule.model;

import java.util.List;

public record AllOf(List<MatchExpression> expressions) implements MatchExpression {

    public AllOf {
        expressions = List.copyOf(expressions);
        if (expressions.isEmpty()) {
            throw new IllegalArgumentException("AllOf 至少需要一个表达式");
        }
    }
}
