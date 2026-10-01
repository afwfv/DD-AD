package dev.fordes.adfs.source;

import dev.fordes.adfs.error.InputException;

final class ByteBudget {

    private final long maximum;
    private long consumed;

    ByteBudget(long maximum) {
        this.maximum = maximum;
    }

    void consume(long count, String source) {
        if (count < 0 || count > remaining()) {
            throw new InputException("输入及 include 累计大小超过上限: " + source + " --> " + maximum);
        }
        consumed += count;
    }

    long remaining() {
        return maximum - consumed;
    }
}
