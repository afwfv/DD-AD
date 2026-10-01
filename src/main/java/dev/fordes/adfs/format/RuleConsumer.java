package dev.fordes.adfs.format;

import dev.fordes.adfs.rule.model.RuleEntry;

@FunctionalInterface
public interface RuleConsumer {

    void accept(RuleEntry entry);
}
