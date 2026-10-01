package dev.fordes.adfs.rule.model;

public record IpCidrMatch(MatchSide side, IpAddress network, int prefixLength) implements MatchExpression {

    public IpCidrMatch {
        IpCidrRule normalized = new IpCidrRule(network, prefixLength, RuleAction.BLOCK);
        network = normalized.network();
    }
}
