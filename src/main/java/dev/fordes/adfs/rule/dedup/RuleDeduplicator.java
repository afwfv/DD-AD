package dev.fordes.adfs.rule.dedup;

import dev.fordes.adfs.rule.model.RuleEntry;
import dev.fordes.adfs.rule.normalize.CanonicalRuleEncoder;

public final class RuleDeduplicator {

    private final CanonicalRuleEncoder encoder = new CanonicalRuleEncoder();
    private final CanonicalTable table = new CanonicalTable();
    private final CanonicalStore store;

    public RuleDeduplicator(CanonicalStore store) {
        this.store = store;
    }

    public boolean add(RuleEntry entry) {
        if (dev.fordes.adfs.rule.normalize.RuleOrder.requiresOrder(entry)) {
            return true;
        }
        byte[] canonical = encoder.encode(entry);
        return table.add(Murmur3.hash(canonical), canonical, store);
    }

    public int size() {
        return table.size();
    }

    public int capacity() {
        return table.capacity();
    }

    public int collisions() {
        return table.collisions();
    }
}
