package dev.fordes.adfs.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import dev.fordes.adfs.error.InputException;

/** 严格解码 UTF-8, 将传输和编码故障保留为输入错误。 */
public final class Utf8Reader extends Reader {

    private final Reader reader;
    private final String source;
    private boolean first = true;

    public Utf8Reader(SourceStream stream) {
        source = stream.description();
        reader = new BufferedReader(new InputStreamReader(stream.input(), StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
    }

    @Override
    public int read(char[] buffer, int offset, int length) {
        try {
            int count = reader.read(buffer, offset, length);
            if (first && count > 0) {
                first = false;
                if (buffer[offset] == '\uFEFF') {
                    System.arraycopy(buffer, offset + 1, buffer, offset, count - 1);
                    return count == 1 ? read(buffer, offset, length) : count - 1;
                }
            }
            return count;
        } catch (IOException exception) {
            throw new InputException("读取 UTF-8 输入失败: " + source + " --> " + exception.getMessage(), exception);
        }
    }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new InputException("关闭 UTF-8 输入失败: " + source, exception);
        }
    }
}
