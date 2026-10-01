package dev.fordes.adfs.format;

import dev.fordes.adfs.source.SourceSession;

/** 将一个来源会话流式解析为规则条目。 */
public interface RuleParser {

    ParseResult parse(SourceSession session, RuleConsumer consumer);
}
