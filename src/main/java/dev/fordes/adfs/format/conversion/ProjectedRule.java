package dev.fordes.adfs.format.conversion;

import java.util.Objects;

import dev.fordes.adfs.rule.conversion.ConversionDecision;
import dev.fordes.adfs.rule.model.RuleEntry;

/** 已完成目标语义投影、尚未执行策略和写出的规则。 */
public record ProjectedRule<T>(
        T encoded,
        byte[] dedupKey,
        RuleEntry effectiveRule,
        ConversionDecision decision,
        boolean passthrough,
        DedupMode dedupMode) {

    public ProjectedRule {
        Objects.requireNonNull(encoded);
        dedupKey = dedupKey.clone();
        Objects.requireNonNull(effectiveRule);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(dedupMode);
    }

    @Override
    public byte[] dedupKey() {
        return dedupKey.clone();
    }
}
