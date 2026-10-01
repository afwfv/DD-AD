package dev.fordes.adfs.rule.model;

import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;

public record OpaqueRule(RuleType type, RuleDialect dialect, DomainEnvelope domainEnvelope, String payload)
        implements RuleEntry {
}
