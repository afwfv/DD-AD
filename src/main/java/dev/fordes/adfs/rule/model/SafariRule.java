package dev.fordes.adfs.rule.model;

import java.util.Objects;
import java.util.Set;

import dev.fordes.adfs.config.RuleType;

/** 将 Safari 内容拦截器归属绑定到规则, 避免控制标记被独立去重。 */
public record SafariRule(RuleEntry rule, Set<String> blockers) implements RuleEntry {

    public static final Set<String> BLOCKERS = Set.of("general", "privacy", "social", "security", "other", "custom");

    public SafariRule {
        Objects.requireNonNull(rule);
        blockers = Set.copyOf(blockers);
        if (!(rule instanceof AdblockNetworkRule || rule instanceof CosmeticRule
                || rule instanceof OpaqueRule opaque && opaque.type() == RuleType.ADBLOCK)
                || blockers.isEmpty() || !BLOCKERS.containsAll(blockers)) {
            throw new IllegalArgumentException("Safari 分组需要 Adblock 规则及有效的内容拦截器集合");
        }
    }

    public String affinity() {
        return String.join(",", blockers.stream().sorted().toList());
    }
}
