package dev.fordes.adfs.rule.model;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import dev.fordes.adfs.config.RuleDialect;

public record AdblockNetworkRule(
        AdblockPattern pattern,
        RuleAction action,
        Set<AdblockResourceType> includedResourceTypes,
        Set<AdblockResourceType> excludedResourceTypes,
        List<DomainConstraint> domainConstraints,
        PartyConstraint partyConstraint,
        boolean matchCase,
        boolean important,
        List<AdblockModifier> modifiers,
        RuleDialect dialect) implements Rule {

    public AdblockNetworkRule {
        includedResourceTypes = Set.copyOf(includedResourceTypes);
        excludedResourceTypes = Set.copyOf(excludedResourceTypes);
        domainConstraints = domainConstraints.stream().distinct()
                .sorted(Comparator.comparing(DomainConstraint::domain).thenComparing(DomainConstraint::excluded)).toList();
        modifiers = modifiers.stream().distinct().sorted(Comparator.comparing((AdblockModifier modifier) -> modifier.type().value())
                .thenComparing(AdblockModifier::value)).toList();
    }

    public boolean isBadfilter() {
        return modifiers.stream().anyMatch(modifier -> modifier.type() == AdblockModifier.Type.BADFILTER);
    }
}
