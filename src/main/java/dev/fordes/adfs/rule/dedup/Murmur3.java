package dev.fordes.adfs.rule.dedup;

public final class Murmur3 {

    private static final long C1 = 0x87c37b91114253d5L;
    private static final long C2 = 0x4cf5ad432745937fL;

    private Murmur3() {
    }

    public static Hash128 hash(byte[] data) {
        long high = 0;
        long low = 0;
        int blocks = data.length >>> 4;
        for (int block = 0; block < blocks; block++) {
            int offset = block << 4;
            long first = littleEndianLong(data, offset);
            long second = littleEndianLong(data, offset + 8);

            first *= C1;
            first = Long.rotateLeft(first, 31);
            first *= C2;
            high ^= first;
            high = Long.rotateLeft(high, 27) + low;
            high = high * 5 + 0x52dce729;

            second *= C2;
            second = Long.rotateLeft(second, 33);
            second *= C1;
            low ^= second;
            low = Long.rotateLeft(low, 31) + high;
            low = low * 5 + 0x38495ab5;
        }

        int tail = blocks << 4;
        long first = 0;
        long second = 0;
        switch (data.length & 15) {
            case 15 -> second ^= (long) (data[tail + 14] & 0xff) << 48;
            default -> { }
        }
        if ((data.length & 15) >= 14) second ^= (long) (data[tail + 13] & 0xff) << 40;
        if ((data.length & 15) >= 13) second ^= (long) (data[tail + 12] & 0xff) << 32;
        if ((data.length & 15) >= 12) second ^= (long) (data[tail + 11] & 0xff) << 24;
        if ((data.length & 15) >= 11) second ^= (long) (data[tail + 10] & 0xff) << 16;
        if ((data.length & 15) >= 10) second ^= (long) (data[tail + 9] & 0xff) << 8;
        if ((data.length & 15) >= 9) {
            second ^= data[tail + 8] & 0xffL;
            second *= C2;
            second = Long.rotateLeft(second, 33);
            second *= C1;
            low ^= second;
        }
        if ((data.length & 15) >= 8) first ^= (long) (data[tail + 7] & 0xff) << 56;
        if ((data.length & 15) >= 7) first ^= (long) (data[tail + 6] & 0xff) << 48;
        if ((data.length & 15) >= 6) first ^= (long) (data[tail + 5] & 0xff) << 40;
        if ((data.length & 15) >= 5) first ^= (long) (data[tail + 4] & 0xff) << 32;
        if ((data.length & 15) >= 4) first ^= (long) (data[tail + 3] & 0xff) << 24;
        if ((data.length & 15) >= 3) first ^= (long) (data[tail + 2] & 0xff) << 16;
        if ((data.length & 15) >= 2) first ^= (long) (data[tail + 1] & 0xff) << 8;
        if ((data.length & 15) >= 1) {
            first ^= data[tail] & 0xffL;
            first *= C1;
            first = Long.rotateLeft(first, 31);
            first *= C2;
            high ^= first;
        }

        high ^= data.length;
        low ^= data.length;
        high += low;
        low += high;
        high = finalizeHash(high);
        low = finalizeHash(low);
        high += low;
        low += high;
        return new Hash128(high, low);
    }

    private static long littleEndianLong(byte[] data, int offset) {
        return (data[offset] & 0xffL)
                | (data[offset + 1] & 0xffL) << 8
                | (data[offset + 2] & 0xffL) << 16
                | (data[offset + 3] & 0xffL) << 24
                | (data[offset + 4] & 0xffL) << 32
                | (data[offset + 5] & 0xffL) << 40
                | (data[offset + 6] & 0xffL) << 48
                | (data[offset + 7] & 0xffL) << 56;
    }

    private static long finalizeHash(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
