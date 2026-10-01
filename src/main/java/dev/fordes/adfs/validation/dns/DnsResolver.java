package dev.fordes.adfs.validation.dns;

import dev.fordes.adfs.rule.model.DomainName;

public interface DnsResolver {

    DnsResult resolve(DomainName domain);

    int cacheSize();
}
