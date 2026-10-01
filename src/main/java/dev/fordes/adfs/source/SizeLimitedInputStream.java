package dev.fordes.adfs.source;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import dev.fordes.adfs.error.InputException;

final class SizeLimitedInputStream extends FilterInputStream {

    private final ByteBudget budget;
    private final String source;

    SizeLimitedInputStream(InputStream input, ByteBudget budget, String source) {
        super(input);
        this.budget = budget;
        this.source = source;
    }

    @Override
    public int read() throws IOException {
        int value = super.read();
        if (value >= 0) {
            budget.consume(1, source);
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int count = super.read(buffer, offset, length);
        if (count > 0) {
            budget.consume(count, source);
        }
        return count;
    }
}
