package dev.fordes.adfs.rule.model;

import java.util.List;
import java.util.Comparator;

import dev.fordes.adfs.config.RuleDialect;

import dev.fordes.adfs.error.RuleProcessingException;

public record CosmeticRule(
        List<DomainConstraint> domains,
        boolean exception,
        CosmeticOperator operator,
        String body,
        RuleDialect dialect) implements Rule {

    public CosmeticRule {
        domains = domains.stream().distinct().sorted(Comparator.comparing(DomainConstraint::domain)
                .thenComparing(DomainConstraint::excluded)).toList();
        body = body.strip();
        if (body.isEmpty()) {
            throw new RuleProcessingException("元素规则主体不得为空");
        }
    }
}
