package dev.fordes.adfs.rule.normalize;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TreeMap;

import dev.fordes.adfs.rule.model.AllOf;
import dev.fordes.adfs.rule.model.AnyOf;
import dev.fordes.adfs.rule.model.DomainMatch;
import dev.fordes.adfs.rule.model.DomainRule;
import dev.fordes.adfs.rule.model.IpCidrMatch;
import dev.fordes.adfs.rule.model.IpCidrRule;
import dev.fordes.adfs.rule.model.MatchExpression;
import dev.fordes.adfs.rule.model.MatchSide;
import dev.fordes.adfs.rule.model.Not;
import dev.fordes.adfs.rule.model.RouteRule;
import dev.fordes.adfs.rule.model.RuleAction;
import dev.fordes.adfs.rule.model.RuleEntry;

/** 仅规范化可交换的匹配集合, 不改变规则之间的处理顺序。 */
public final class RuleNormalizer {

    private RuleNormalizer() {
    }

    public static RuleEntry normalize(RuleEntry entry) {
        if (!(entry instanceof RouteRule route)) {
            return entry;
        }
        MatchExpression expression = normalizeExpression(route.expression());
        return switch (expression) {
            case DomainMatch(var pattern) -> new DomainRule(pattern, RuleAction.BLOCK);
            case IpCidrMatch(var side, var network, int prefix) when side == MatchSide.DESTINATION ->
                    new IpCidrRule(network, prefix, RuleAction.BLOCK);
            default -> new RouteRule(expression);
        };
    }

    private static MatchExpression normalizeExpression(MatchExpression expression) {
        return switch (expression) {
            case AllOf(var children) -> {
                List<MatchExpression> members = unique(children.stream().map(RuleNormalizer::normalizeExpression)
                        .flatMap(child -> child instanceof AllOf(var nested) ? nested.stream() : java.util.stream.Stream.of(child)).toList());
                yield members.size() == 1 ? members.getFirst() : new AllOf(members);
            }
            case AnyOf(var children) -> {
                List<MatchExpression> members = unique(children.stream().map(RuleNormalizer::normalizeExpression)
                        .flatMap(child -> child instanceof AnyOf(var nested) ? nested.stream() : java.util.stream.Stream.of(child)).toList());
                yield members.size() == 1 ? members.getFirst() : new AnyOf(members);
            }
            case Not(var child) -> {
                MatchExpression normalized = normalizeExpression(child);
                yield normalized instanceof Not(var nested) ? nested : new Not(normalized);
            }
            default -> expression;
        };
    }

    private static List<MatchExpression> unique(List<MatchExpression> children) {
        TreeMap<String, MatchExpression> members = new TreeMap<>();
        CanonicalRuleEncoder encoder = new CanonicalRuleEncoder();
        for (MatchExpression member : children) {
            members.put(new String(encoder.encode(new RouteRule(member)), StandardCharsets.UTF_8), member);
        }
        return List.copyOf(members.values());
    }
}
