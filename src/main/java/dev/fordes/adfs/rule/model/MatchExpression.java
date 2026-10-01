package dev.fordes.adfs.rule.model;

public sealed interface MatchExpression permits AllOf, AnyOf, DomainMatch, IpCidrMatch, NetworkMatch, Not, PortMatch,
        ProcessMatch {
}
