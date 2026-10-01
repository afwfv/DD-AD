package dev.fordes.adfs.rule.dedup;

public final class OutputDeduplicator {

    private int orderedRecords;
    private final CanonicalTable table = new CanonicalTable();
    private final CanonicalStore store;

    public OutputDeduplicator(CanonicalStore store) {
        this.store = store;
    }

    public boolean add(byte[] outputRecord) {
        return table.add(Murmur3.hash(outputRecord), outputRecord, store);
    }

    public void recordOrdered() {
        orderedRecords = Math.incrementExact(orderedRecords);
    }

    public int size() {
        return Math.addExact(table.size(), orderedRecords);
    }
}
