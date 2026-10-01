package dev.fordes.adfs.rule.model;

public sealed interface DomainPattern permits ExactDomain, KeywordDomain, RegexDomain, SuffixDomain, Subdomain, WildcardDomain {

    String value();
}
