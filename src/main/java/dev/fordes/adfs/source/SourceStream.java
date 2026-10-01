package dev.fordes.adfs.source;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import dev.fordes.adfs.error.InputException;

public final class SourceStream implements AutoCloseable {

    private final URI location;
    private final String description;
    private final int includeDepth;
    private final InputStream input;
    private boolean closed;
    private final SourceCounters counters;

    SourceStream(URI location, String description, int includeDepth, InputStream input, SourceCounters counters) {
        this.location = location;
        this.description = description;
        this.includeDepth = includeDepth;
        this.counters = counters;
        this.input = new java.io.FilterInputStream(input) {
            @Override
            public int read() throws IOException {
                int value = in.read();
                counters.bytes(value < 0 ? 0 : 1);
                return value;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                int count = in.read(buffer, offset, length);
                counters.bytes(count);
                return count;
            }
        };
    }

    public URI location() {
        return location;
    }

    public String description() {
        return description;
    }

    public int includeDepth() {
        return includeDepth;
    }

    public InputStream input() {
        return input;
    }

    void line(String text) {
        counters.line(text);
    }

    public void comment() {
        counters.comment();
    }

    boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            input.close();
        } catch (IOException exception) {
            throw new InputException("关闭输入流失败: " + description, exception);
        }
    }
}
