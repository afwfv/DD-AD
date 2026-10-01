package dev.fordes.adfs.format;

import java.util.Optional;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import dev.fordes.adfs.config.EffectiveConfig.InputLimits;
import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.rule.spool.RuleSpool;
import dev.fordes.adfs.source.BoundedLineReader;
import dev.fordes.adfs.source.SourceLine;
import dev.fordes.adfs.source.SourceSession;

@Slf4j
public final class TextSource {



    private TextSource() {
    }

    public static void read(SourceSession session, InputLimits limits,
            java.util.function.Predicate<String> comment, Consumer<SourceLine> consumer) {
        try (BoundedLineReader reader = new BoundedLineReader(session.root(), limits.maxLineLength())) {
            Optional<SourceLine> next;
            while ((next = reader.readLine()).isPresent()) {
                SourceLine line = next.orElseThrow();
                if (comment.test(line.text().strip())) {
                    session.root().comment();
                }
                try {
                    if (log.isDebugEnabled()) {
                        MDC.put(RuleSpool.INPUT_RULE, line.text());
                    }
                    consumer.accept(line);
                } catch (RuleProcessingException | IllegalArgumentException exception) {
                    session.invalidRule();
                    log.warn("规则语法非法, 已跳过:  {} --> 规则解析 | {} --> {}",
                            MDC.get(RuleSpool.INPUT), line.text(), exception.getMessage());
                }
            }
        }
    }

    public static String ruleText(SourceLine line, int minimum, int maximum) {
        String text = line.text().strip();
        if (text.length() < minimum || text.length() > maximum) {
            throw new RuleProcessingException("逻辑规则长度越界: " + text.length()
                    + " --> 允许 " + minimum + ".." + maximum);
        }
        return text;
    }
}
