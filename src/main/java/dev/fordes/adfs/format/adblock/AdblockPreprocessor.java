package dev.fordes.adfs.format.adblock;

import dev.fordes.adfs.config.EffectiveConfig.InputLimits;
import dev.fordes.adfs.config.EffectiveConfig.PreprocessorConfig;
import dev.fordes.adfs.error.InputException;
import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.rule.spool.RuleSpool;
import dev.fordes.adfs.source.BoundedLineReader;
import dev.fordes.adfs.source.SourceLine;
import dev.fordes.adfs.source.SourceSession;
import dev.fordes.adfs.source.SourceStream;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.net.URI;
import java.util.*;
import java.util.function.BiConsumer;

@Slf4j
final class AdblockPreprocessor {

    private final InputLimits limits;
    private final PreprocessorConfig config;
    private final Set<String> trueTokens;
    private final Deque<ConditionalFrame> conditions = new ArrayDeque<>();
    private final Set<URI> activeSources = new HashSet<>();
    private int suppressedConditionDepth;

    AdblockPreprocessor(InputLimits limits, PreprocessorConfig config, Set<String> builtInTokens) {
        this.limits = limits;
        this.config = config;
        Set<String> tokens = new HashSet<>(config.trueTokens());
        tokens.addAll(builtInTokens);
        this.trueTokens = Set.copyOf(tokens);
    }

    void process(SourceSession session, BiConsumer<SourceLine, Set<String>> consumer) {
        processStream(session, session.root(), Set.of(), consumer);
    }

    private Set<String> processStream(SourceSession session, SourceStream stream, Set<String> inheritedAffinity,
            BiConsumer<SourceLine, Set<String>> consumer) {
        URI normalized = stream.location().normalize();
        if (!activeSources.add(normalized)) {
            throw new RuleProcessingException("Adblock include 形成循环");
        }
        int initialDepth = conditions.size();
        int initialSuppressedDepth = suppressedConditionDepth;
        Set<String> affinity = inheritedAffinity;
        try (BoundedLineReader reader = new BoundedLineReader(stream, limits.maxLineLength())) {
            Optional<SourceLine> next;
            while ((next = reader.readLine()).isPresent()) {
                SourceLine line = next.orElseThrow();
                Optional<AdblockDirective> parsed;
                try {
                    parsed = AdblockDirective.parse(line);
                } catch (RuleProcessingException | IllegalArgumentException exception) {
                    reportInvalid(session, line, exception.getMessage());
                    recoverMalformedDirective(line, initialDepth);
                    continue;
                }
                if (parsed.isEmpty()) {
                    if (AdblockParser.isComment(line.text())) {
                        stream.comment();
                    }
                    if (isActive()) {
                        consumer.accept(line, affinity);
                    }
                    continue;
                }
                AdblockDirective directive = parsed.orElseThrow();
                if (suppressedConditionDepth > initialSuppressedDepth) {
                    updateSuppressedDepth(directive.kind());
                    continue;
                }
                try {
                    switch (directive.kind()) {
                        case IF -> openCondition(directive.argument(), line);
                        case ELSE -> switchBranch(line, initialDepth);
                        case ENDIF -> closeCondition(line, initialDepth);
                        case INCLUDE -> {
                            if (isActive()) {
                                try {
                                    affinity = processStream(session, session.openInclude(stream, directive.argument()),
                                            affinity, consumer);
                                } catch (InputException exception) {
                                    throw new InputException("读取 include 失败: " + directive.argument(), exception);
                                } catch (RuleProcessingException | IllegalArgumentException exception) {
                                    throw new RuleProcessingException("展开 include 失败: " + directive.argument()
                                            + " --> " + exception.getMessage(), exception);
                                }
                            }
                        }
                        case AFFINITY -> {
                            if (isActive()) {
                                affinity = directive.blockers();
                            }
                        }
                    }
                } catch (RuleProcessingException | IllegalArgumentException exception) {
                    reportInvalid(session, line, exception.getMessage());
                    recoverDirective(directive.kind(), line, initialDepth);
                }
            }
            while (conditions.size() > initialDepth) {
                ConditionalFrame unclosed = conditions.pop();
                reportInvalid(session, unclosed.opening(), "include 或来源内的条件块未闭合");
            }
            if (suppressedConditionDepth > initialSuppressedDepth) {
                reportInvalid(session, new SourceLine(stream.description(), 0, "!#if"),
                        "超过嵌套上限的条件块未闭合");
                suppressedConditionDepth = initialSuppressedDepth;
            }
            return affinity;
        } finally {
            activeSources.remove(normalized);
        }
    }

    private void openCondition(String expression, SourceLine line) {
        if (conditions.size() == config.maxDepth()) {
            suppressedConditionDepth++;
            throw failure(line, "Adblock 条件嵌套超过上限: " + config.maxDepth());
        }
        boolean parentActive = isActive();
        boolean condition;
        try {
            condition = BooleanExpression.evaluate(expression, trueTokens);
        } catch (RuleProcessingException exception) {
            throw new RuleProcessingException("条件表达式解析失败: " + exception.getMessage(), exception);
        }
        conditions.push(new ConditionalFrame(parentActive, condition, true, false, parentActive && condition, line));
    }

    private void switchBranch(SourceLine line, int initialDepth) {
        if (conditions.size() <= initialDepth) {
            throw failure(line, "Adblock !#else 没有对应 !#if");
        }
        ConditionalFrame frame = conditions.peek();
        if (frame.elseSeen()) {
            throw failure(line, "Adblock 条件块包含重复 !#else");
        }
        conditions.pop();
        conditions.push(new ConditionalFrame(frame.parentActive(), frame.condition(), frame.valid(), true,
                frame.valid() && frame.parentActive() && !frame.condition(), frame.opening()));
    }

    private void closeCondition(SourceLine line, int initialDepth) {
        if (conditions.size() <= initialDepth) {
            throw failure(line, "Adblock !#endif 没有对应 !#if");
        }
        conditions.pop();
    }

    private boolean isActive() {
        return suppressedConditionDepth == 0 && (conditions.isEmpty() || conditions.peek().active());
    }

    private void recoverMalformedDirective(SourceLine line, int initialDepth) {
        AdblockDirective.recognizedKind(line).ifPresent(kind -> {
            if (suppressedConditionDepth > 0) {
                updateSuppressedDepth(kind);
            } else {
                recoverDirective(kind, line, initialDepth);
            }
        });
    }

    private void recoverDirective(AdblockDirective.Kind kind, SourceLine line, int initialDepth) {
        switch (kind) {
            case IF -> {
                if (suppressedConditionDepth == 0) {
                    if (conditions.size() < config.maxDepth()) {
                        conditions.push(new ConditionalFrame(isActive(), false, false, false, false, line));
                    } else {
                        suppressedConditionDepth++;
                    }
                }
            }
            case ELSE -> invalidateCurrentBranch(initialDepth);
            case ENDIF -> {
                if (conditions.size() > initialDepth) {
                    conditions.pop();
                }
            }
            case INCLUDE, AFFINITY -> {
                // 非法 include 与 affinity 不改变当前预处理状态。
            }
        }
    }

    private void invalidateCurrentBranch(int initialDepth) {
        if (conditions.size() <= initialDepth) {
            return;
        }
        ConditionalFrame frame = conditions.pop();
        conditions.push(new ConditionalFrame(frame.parentActive(), frame.condition(), false, true, false, frame.opening()));
    }

    private void updateSuppressedDepth(AdblockDirective.Kind kind) {
        switch (kind) {
            case IF -> suppressedConditionDepth++;
            case ENDIF -> suppressedConditionDepth--;
            case ELSE, INCLUDE, AFFINITY -> {
                // 被抑制的条件块内不执行其他指令。
            }
        }
    }

    private static void reportInvalid(SourceSession session, SourceLine line, String reason) {
        session.invalidRule();
        log.error("Adblock 预处理指令非法, 已安全跳过:  {} --> Adblock 预处理 | {} --> {}",
                MDC.get(RuleSpool.INPUT), line.text(), reason);
    }

    private static RuleProcessingException failure(SourceLine line, String message) {
        return AdblockDirective.failure(line, message);
    }

    private record ConditionalFrame(boolean parentActive, boolean condition, boolean valid, boolean elseSeen, boolean active,
            SourceLine opening) {
    }
}
