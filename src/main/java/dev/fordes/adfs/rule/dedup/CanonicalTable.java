package dev.fordes.adfs.rule.dedup;

import java.util.Arrays;

public final class CanonicalTable {

    private static final int INITIAL_CAPACITY = 1_024;
    private static final double MAX_LOAD_FACTOR = 0.65;
    private static final int INITIAL_COLLISION_CAPACITY = 16;
    private long[] hashHigh = new long[INITIAL_CAPACITY];
    private long[] hashLow = new long[INITIAL_CAPACITY];
    private long[] offsets = new long[INITIAL_CAPACITY];
    private int[] lengths = new int[INITIAL_CAPACITY];
    private int[] collisionHeads = initializedHeads(INITIAL_CAPACITY);
    private boolean[] occupied = new boolean[INITIAL_CAPACITY];
    private long[] collisionOffsets = new long[INITIAL_COLLISION_CAPACITY];
    private int[] collisionLengths = new int[INITIAL_COLLISION_CAPACITY];
    private int[] collisionNext = new int[INITIAL_COLLISION_CAPACITY];
    private int size;
    private int collisionSize;

    public boolean add(Hash128 hash, byte[] canonical, CanonicalStore store) {
        if (size + 1 > occupied.length * MAX_LOAD_FACTOR) {
            resize();
        }
        int slot = findSlot(hash);
        if (!occupied[slot]) {
            CanonicalLocator locator = store.append(canonical);
            occupy(slot, hash, locator, -1);
            size++;
            return true;
        }
        CanonicalLocator primary = new CanonicalLocator(offsets[slot], lengths[slot]);
        if (store.equals(primary, canonical)) {
            return false;
        }
        for (int collision = collisionHeads[slot]; collision >= 0; collision = collisionNext[collision]) {
            CanonicalLocator locator = new CanonicalLocator(collisionOffsets[collision], collisionLengths[collision]);
            if (store.equals(locator, canonical)) {
                return false;
            }
        }
        appendCollision(slot, store.append(canonical));
        return true;
    }

    public boolean contains(Hash128 hash, byte[] canonical, CanonicalStore store) {
        int slot = findSlot(hash);
        if (!occupied[slot]) {
            return false;
        }
        CanonicalLocator primary = new CanonicalLocator(offsets[slot], lengths[slot]);
        if (store.equals(primary, canonical)) {
            return true;
        }
        for (int collision = collisionHeads[slot]; collision >= 0; collision = collisionNext[collision]) {
            CanonicalLocator locator = new CanonicalLocator(collisionOffsets[collision], collisionLengths[collision]);
            if (store.equals(locator, canonical)) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return size + collisionSize;
    }

    public int capacity() {
        return occupied.length;
    }

    public int collisions() {
        return collisionSize;
    }

    private int findSlot(Hash128 hash) {
        int mask = occupied.length - 1;
        int slot = mix(hash.high() + hash.low()) & mask;
        while (occupied[slot] && (hashHigh[slot] != hash.high() || hashLow[slot] != hash.low())) {
            slot = slot + 1 & mask;
        }
        return slot;
    }

    private void appendCollision(int slot, CanonicalLocator locator) {
        if (collisionSize == collisionOffsets.length) {
            int capacity = Math.multiplyExact(collisionSize, 2);
            collisionOffsets = Arrays.copyOf(collisionOffsets, capacity);
            collisionLengths = Arrays.copyOf(collisionLengths, capacity);
            collisionNext = Arrays.copyOf(collisionNext, capacity);
        }
        collisionOffsets[collisionSize] = locator.offset();
        collisionLengths[collisionSize] = locator.length();
        collisionNext[collisionSize] = collisionHeads[slot];
        collisionHeads[slot] = collisionSize;
        collisionSize++;
    }

    private void resize() {
        int capacity = Math.multiplyExact(occupied.length, 2);
        long[] oldHigh = hashHigh;
        long[] oldLow = hashLow;
        long[] oldOffsets = offsets;
        int[] oldLengths = lengths;
        int[] oldHeads = collisionHeads;
        boolean[] oldOccupied = occupied;
        hashHigh = new long[capacity];
        hashLow = new long[capacity];
        offsets = new long[capacity];
        lengths = new int[capacity];
        collisionHeads = initializedHeads(capacity);
        occupied = new boolean[capacity];
        for (int oldSlot = 0; oldSlot < oldOccupied.length; oldSlot++) {
            if (oldOccupied[oldSlot]) {
                Hash128 hash = new Hash128(oldHigh[oldSlot], oldLow[oldSlot]);
                int slot = findSlot(hash);
                occupy(slot, hash, new CanonicalLocator(oldOffsets[oldSlot], oldLengths[oldSlot]), oldHeads[oldSlot]);
            }
        }
    }

    private void occupy(int slot, Hash128 hash, CanonicalLocator locator, int collisionHead) {
        occupied[slot] = true;
        hashHigh[slot] = hash.high();
        hashLow[slot] = hash.low();
        offsets[slot] = locator.offset();
        lengths[slot] = locator.length();
        collisionHeads[slot] = collisionHead;
    }

    private static int[] initializedHeads(int capacity) {
        int[] heads = new int[capacity];
        Arrays.fill(heads, -1);
        return heads;
    }

    private static int mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return (int) (value ^ value >>> 32);
    }
}
