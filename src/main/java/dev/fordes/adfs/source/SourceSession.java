package dev.fordes.adfs.source;

/** 管理一个根来源及其 include 流的累计大小和资源生命周期。 */
public interface SourceSession extends AutoCloseable {

    SourceStream root();

    SourceStream openInclude(SourceStream parent, String reference);

    /** 记录已报告并跳过的非法规则或预处理指令。 */
    void invalidRule();

    dev.fordes.adfs.report.InputMetrics metrics(long rules);

    @Override
    void close();
}
