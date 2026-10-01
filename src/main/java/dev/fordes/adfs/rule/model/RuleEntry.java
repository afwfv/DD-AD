package dev.fordes.adfs.rule.model;

public sealed interface RuleEntry permits OpaqueRule, Rule, SafariRule {
}
