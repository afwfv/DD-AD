package dev.fordes.adfs.format.singbox;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import dev.fordes.adfs.error.RuleProcessingException;

/** 在 JSON 编码写入前限制单条规则缓冲区大小。 */
final class RuleBuffer extends OutputStream {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final int limit;

    RuleBuffer(int limit) {
        this.limit = limit;
    }

    @Override
    public void write(int value) {
        requireCapacity(1);
        bytes.write(value);
    }

    @Override
    public void write(byte[] value, int offset, int length) {
        requireCapacity(length);
        bytes.write(value, offset, length);
    }

    byte[] toByteArray() {
        return bytes.toByteArray();
    }

    private void requireCapacity(int length) {
        if (length > limit - bytes.size()) {
            throw new RuleProcessingException("单条 Sing-box 规则超过派生字节上限: " + limit);
        }
    }
}
