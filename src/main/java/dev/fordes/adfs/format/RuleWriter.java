package dev.fordes.adfs.format;

import dev.fordes.adfs.rule.model.RuleEntry;

/** 转换、去重并写出一个目标格式。 */
public interface RuleWriter extends AutoCloseable {

    WriteResult write(RuleEntry entry);

    FinishResult finish();

    @Override
    void close();
}
