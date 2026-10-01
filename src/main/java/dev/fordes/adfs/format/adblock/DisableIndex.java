package dev.fordes.adfs.format.adblock;

import java.nio.file.Path;
import java.util.List;

import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.rule.dedup.CanonicalStore;
import dev.fordes.adfs.rule.dedup.CanonicalTable;
import dev.fordes.adfs.rule.dedup.Murmur3;
import dev.fordes.adfs.rule.model.AdblockModifier;
import dev.fordes.adfs.rule.model.AdblockNetworkRule;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.RuleEntry;
import dev.fordes.adfs.rule.model.SafariRule;
import dev.fordes.adfs.rule.normalize.CanonicalRuleEncoder;

public final class DisableIndex implements AutoCloseable {

    private final CanonicalRuleEncoder encoder = new CanonicalRuleEncoder();
    private final CanonicalTable table = new CanonicalTable();
    private final CanonicalStore store;

    public DisableIndex(Path path) {
        this.store = new CanonicalStore(path);
    }

    public void disable(RuleEntry badfilter) {
        byte[] key = key(badfilter);
        table.add(Murmur3.hash(key), key, store);
    }

    public boolean isDisabled(RuleEntry rule) {
        if (rule instanceof SafariRule safari) {
            return isDisabled(safari.rule());
        }
        if (!(rule instanceof AdblockNetworkRule)
                && !(rule instanceof OpaqueRule opaque && opaque.type() == RuleType.ADBLOCK)) {
            return false;
        }
        byte[] key = key(rule);
        return table.contains(Murmur3.hash(key), key, store);
    }

    public boolean isControl(RuleEntry entry) {
        if (entry instanceof SafariRule safari) {
            return isControl(safari.rule());
        }
        if (entry instanceof AdblockNetworkRule rule) {
            return rule.isBadfilter();
        }
        return entry instanceof OpaqueRule opaque && opaque.type() == RuleType.ADBLOCK
                && AdblockSyntax.networkOptions(opaque.payload()).stream().anyMatch(option -> option.equalsIgnoreCase("badfilter"));
    }

    private byte[] key(RuleEntry entry) {
        if (entry instanceof SafariRule safari) {
            return key(safari.rule());
        }
        if (entry instanceof OpaqueRule opaque) {
            List<String> options = AdblockSyntax.networkOptions(opaque.payload());
            int separator = options.isEmpty() ? -1 : AdblockSyntax.optionSeparator(opaque.payload());
            String body = separator < 0 ? opaque.payload() : opaque.payload().substring(0, separator);
            List<String> activeOptions = options.stream().filter(option -> !option.equalsIgnoreCase("badfilter"))
                    .sorted().toList();
            return encoder.encode(new OpaqueRule(opaque.type(), opaque.dialect(), opaque.domainEnvelope(),
                    body + (activeOptions.isEmpty() ? "" : "$" + String.join(",", activeOptions))));
        }
        AdblockNetworkRule rule = (AdblockNetworkRule) entry;
        AdblockNetworkRule subject = new AdblockNetworkRule(
                rule.pattern(), rule.action(), rule.includedResourceTypes(), rule.excludedResourceTypes(),
                rule.domainConstraints(), rule.partyConstraint(), rule.matchCase(), rule.important(),
                rule.modifiers().stream()
                        .filter(modifier -> modifier.type() != AdblockModifier.Type.BADFILTER)
                        .toList(), rule.dialect());
        return encoder.encode(subject);
    }

    @Override
    public void close() {
        store.close();
    }
}
