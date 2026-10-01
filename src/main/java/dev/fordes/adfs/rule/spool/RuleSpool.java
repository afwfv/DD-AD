package dev.fordes.adfs.rule.spool;

import dev.fordes.adfs.error.OutputException;
import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.format.RuleConsumer;
import dev.fordes.adfs.rule.model.RuleEntry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
public final class RuleSpool implements RuleConsumer, AutoCloseable {

    public static final String INPUT = "adfs.input";
    public static final String INPUT_RULE = "adfs.rule";
    private static final List<String> LOG_CONTEXT_KEYS = List.of(INPUT, INPUT_RULE);

    private final Path path;
    private final int maxRecordSize;
    private final RuleCodec codec = new RuleCodec();
    // 来源仅用于诊断, 不进入规则编码和去重键; INFO 下保持原有暂存记录。
    private final boolean logContext = log.isDebugEnabled();
    private final DataOutputStream output;
    private boolean writing = true;

    public RuleSpool(Path path, int maxRuleLength) {
        this.path = path;
        this.maxRecordSize = Math.addExact(512, Math.multiplyExact(maxRuleLength, 64));
        try {
            output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)));
        } catch (IOException exception) {
            throw new OutputException("创建 RuleSpool 失败: " + path, exception);
        }
    }

    @Override
    public void accept(RuleEntry entry) {
        if (!writing) {
            throw new IllegalStateException("RuleSpool 写入端已经关闭");
        }
        byte[] record = codec.encode(entry);
        if (record.length > maxRecordSize) {
            throw new RuleProcessingException("RuleSpool 条目超过派生上限: " + record.length);
        }
        try {
            output.writeInt(record.length);
            output.write(record);
            if (logContext) {
                for (String key : LOG_CONTEXT_KEYS) {
                    String value = MDC.get(key);
                    byte[] context = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
                    output.writeInt(context.length);
                    output.write(context);
                }
            }
        } catch (IOException exception) {
            throw new OutputException("写入 RuleSpool 失败: " + path, exception);
        }
    }

    public void finishWriting() {
        if (!writing) {
            return;
        }
        try {
            output.close();
            writing = false;
        } catch (IOException exception) {
            throw new OutputException("关闭 RuleSpool 写入端失败: " + path, exception);
        }
    }

    public void replay(RuleConsumer consumer) {
        finishWriting();
        Map<String, String> previousContext = logContext ? MDC.getCopyOfContextMap() : null;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            while (true) {
                byte[] header = input.readNBytes(Integer.BYTES);
                if (header.length == 0) {
                    return;
                }
                if (header.length != Integer.BYTES) {
                    throw new RuleProcessingException("RuleSpool 记录头被截断: " + header.length);
                }
                int length = ByteBuffer.wrap(header).getInt();
                if (length <= 0 || length > maxRecordSize) {
                    throw new RuleProcessingException("RuleSpool 记录长度非法: " + length);
                }
                byte[] record = input.readNBytes(length);
                if (record.length != length) {
                    throw new RuleProcessingException("RuleSpool 记录被截断: " + length
                            + " --> " + record.length);
                }
                if (logContext) {
                    for (String key : LOG_CONTEXT_KEYS) {
                        int contextLength = input.readInt();
                        if (contextLength < 0) {
                            throw new RuleProcessingException("RuleSpool 来源文本长度非法: " + contextLength);
                        }
                        byte[] context = input.readNBytes(contextLength);
                        if (context.length != contextLength) {
                            throw new RuleProcessingException("RuleSpool 来源文本被截断: " + contextLength
                                    + " --> " + context.length);
                        }
                        MDC.put(key, new String(context, StandardCharsets.UTF_8));
                    }
                }
                consumer.accept(codec.decode(record));
            }
        } catch (IOException exception) {
            throw new RuleProcessingException("回放 RuleSpool 失败: " + path, exception);
        } finally {
            if (logContext) {
                if (previousContext == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previousContext);
                }
            }
        }
    }

    @Override
    public void close() {
        finishWriting();
    }
}
