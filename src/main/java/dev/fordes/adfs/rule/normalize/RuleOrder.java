package dev.fordes.adfs.rule.normalize;

import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.rule.model.DnsAddressRule;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.RuleEntry;

public final class RuleOrder {

    private RuleOrder() {
    }

    public static boolean requiresOrder(RuleEntry entry) {
        return entry instanceof DnsAddressRule || entry instanceof OpaqueRule opaque
                && (opaque.type() == RuleType.SMARTDNS || opaque.type() == RuleType.DNSMASQ);
    }
}
