package dev.fordes.adfs.format;

import java.util.function.Consumer;
import java.util.function.Predicate;

import dev.fordes.adfs.format.conversion.DedupMode;
import dev.fordes.adfs.format.conversion.ProjectedRule;
import dev.fordes.adfs.rule.conversion.ConversionDecision;
import dev.fordes.adfs.rule.conversion.ConversionPolicy;
import dev.fordes.adfs.rule.dedup.OutputDeduplicator;
import dev.fordes.adfs.rule.model.RuleEntry;

/** 统一执行转换策略、白名单、目标去重和写出顺序。 */
public final class OutputRuleProcessor<T> {

    private final ConversionPolicy policy;
    private final Predicate<RuleEntry> removeForWhitelist;
    private final OutputDeduplicator deduplicator;
    private final Consumer<T> sink;

    public OutputRuleProcessor(
            ConversionPolicy policy,
            OutputDeduplicator deduplicator,
            Consumer<T> sink) {
        this(policy, _ -> false, deduplicator, sink);
    }

    public OutputRuleProcessor(
            ConversionPolicy policy,
            Predicate<RuleEntry> removeForWhitelist,
            OutputDeduplicator deduplicator,
            Consumer<T> sink) {
        this.policy = policy;
        this.removeForWhitelist = removeForWhitelist;
        this.deduplicator = deduplicator;
        this.sink = sink;
    }

    public WriteResult process(ProjectedRule<T> projected) {
        ConversionDecision decision = projected.decision();
        if (!policy.allows(decision.scope())) {
            return WriteResult.UNSUPPORTED;
        }
        if (removeForWhitelist.test(projected.effectiveRule())) {
            return WriteResult.WHITELIST_REMOVED;
        }
        if (projected.dedupMode() == DedupMode.ORDERED) {
            deduplicator.recordOrdered();
        } else if (!deduplicator.add(projected.dedupKey())) {
            return WriteResult.DUPLICATE;
        }
        sink.accept(projected.encoded());
        return projected.passthrough() ? WriteResult.PASSTHROUGH : WriteResult.WRITTEN;
    }

}
