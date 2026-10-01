package dev.fordes.adfs.rule.model;

public record DomainRule(DomainPattern pattern, RuleAction action) implements Rule {
}
