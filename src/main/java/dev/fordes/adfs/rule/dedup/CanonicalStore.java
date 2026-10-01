package dev.fordes.adfs.rule.dedup;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import dev.fordes.adfs.error.OutputException;

public final class CanonicalStore implements AutoCloseable {

    private static final int COMPARE_BUFFER_SIZE = 8_192;
    private static final int WRITE_BUFFER_SIZE = 65_536;
    private final Path path;
    private final FileChannel channel;
    private final ByteBuffer pending = ByteBuffer.allocate(WRITE_BUFFER_SIZE);
    private long committed;

    public CanonicalStore(Path path) {
        this.path = path;
        try {
            channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new OutputException("创建规范存储失败: " + path, exception);
        }
    }

    public CanonicalLocator append(byte[] value) {
        try {
            if ((long) Integer.BYTES + value.length > pending.remaining()) {
                flush();
            }
            long offset = committed + pending.position();
            pending.putInt(value.length);
            if (value.length > pending.remaining()) {
                flush();
                writeFully(ByteBuffer.wrap(value), committed);
                committed += value.length;
            } else {
                pending.put(value);
            }
            return new CanonicalLocator(offset + Integer.BYTES, value.length);
        } catch (IOException exception) {
            throw new OutputException("追加规范存储失败: " + path, exception);
        }
    }

    public boolean equals(CanonicalLocator locator, byte[] candidate) {
        if (locator.length() != candidate.length) {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.allocate(Math.min(COMPARE_BUFFER_SIZE, candidate.length));
        int compared = 0;
        try {
            if (locator.offset() + locator.length() > committed) {
                flush();
            }
            while (compared < candidate.length) {
                buffer.clear().limit(Math.min(buffer.capacity(), candidate.length - compared));
                int count = channel.read(buffer, locator.offset() + compared);
                if (count <= 0) {
                    throw new IOException("规范存储记录被截断");
                }
                for (int index = 0; index < count; index++) {
                    if (buffer.get(index) != candidate[compared + index]) {
                        return false;
                    }
                }
                compared += count;
            }
            return true;
        } catch (IOException exception) {
            throw new OutputException("读取规范存储失败: " + path, exception);
        }
    }

    private void writeFully(ByteBuffer buffer, long offset) throws IOException {
        long position = offset;
        while (buffer.hasRemaining()) {
            position += channel.write(buffer, position);
        }
    }

    private void flush() throws IOException {
        int length = pending.position();
        pending.flip();
        writeFully(pending, committed);
        committed += length;
        pending.clear();
    }

    @Override
    public void close() {
        try (channel) {
            flush();
        } catch (IOException exception) {
            throw new OutputException("关闭规范存储失败: " + path, exception);
        }
    }
}
