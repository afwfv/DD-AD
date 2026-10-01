package dev.fordes.adfs.format.adblock;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.rule.model.SafariRule;
import dev.fordes.adfs.source.SourceLine;

/** 识别指令名称后验证参数; 装饰性注释不属于指令。 */
record AdblockDirective(Kind kind, String argument, Set<String> blockers) {

    private static final Pattern HEAD = Pattern.compile("^!#([A-Za-z_][A-Za-z_0-9]*)(.*)$");

    enum Kind { IF, ELSE, ENDIF, INCLUDE, AFFINITY }

    static Optional<AdblockDirective> parse(SourceLine line) {
        String text = line.text().strip();
        if (!text.startsWith("!#") || text.startsWith("!##")) {
            return Optional.empty();
        }
        Matcher matcher = HEAD.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String name = matcher.group(1);
        String tail = matcher.group(2);
        String argument = tail.strip();
        Kind kind = switch (name) {
            case "if" -> Kind.IF;
            case "else" -> Kind.ELSE;
            case "endif" -> Kind.ENDIF;
            case "include" -> Kind.INCLUDE;
            case "safari_cb_affinity" -> Kind.AFFINITY;
            default -> null;
        };
        if (kind == null) {
            return Optional.empty();
        }
        Set<String> blockers = Set.of();
        switch (kind) {
            case IF, INCLUDE -> {
                if (tail.isEmpty() || !Character.isWhitespace(tail.charAt(0)) || argument.isEmpty()) {
                    throw failure(line, "指令需要空白分隔的非空参数: " + name);
                }
                if (kind == Kind.INCLUDE && (argument.startsWith("\"") || argument.startsWith("'"))) {
                    if (argument.length() < 2 || argument.charAt(argument.length() - 1) != argument.charAt(0)) {
                        throw failure(line, "include 路径引号未闭合");
                    }
                    argument = argument.substring(1, argument.length() - 1);
                    if (argument.isBlank()) {
                        throw failure(line, "include 路径为空");
                    }
                }
            }
            case ELSE, ENDIF -> {
                if (!argument.isEmpty()) {
                    throw failure(line, "指令不接受参数: " + name);
                }
            }
            case AFFINITY -> {
                if (!argument.isEmpty()) {
                    if (!tail.startsWith("(") || !argument.endsWith(")")) {
                        throw failure(line, "Safari affinity 参数必须使用括号包围");
                    }
                    blockers = Arrays.stream(argument.substring(1, argument.length() - 1).split(",", -1))
                            .map(String::strip).collect(Collectors.toUnmodifiableSet());
                    if (blockers.isEmpty() || blockers.stream().anyMatch(value ->
                            !value.equals("all") && !SafariRule.BLOCKERS.contains(value))) {
                        throw failure(line, "Safari affinity 包含非法内容拦截器");
                    }
                    if (blockers.contains("all")) {
                        if (blockers.size() != 1) {
                            throw failure(line, "Safari affinity 的 all 必须单独使用");
                        }
                        blockers = SafariRule.BLOCKERS;
                    }
                }
            }
        }
        return Optional.of(new AdblockDirective(kind, argument, blockers));
    }

    static Optional<Kind> recognizedKind(SourceLine line) {
        Matcher matcher = HEAD.matcher(line.text().strip());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (matcher.group(1)) {
            case "if" -> Kind.IF;
            case "else" -> Kind.ELSE;
            case "endif" -> Kind.ENDIF;
            case "include" -> Kind.INCLUDE;
            case "safari_cb_affinity" -> Kind.AFFINITY;
            default -> null;
        });
    }

    static RuleProcessingException failure(SourceLine line, String reason) {
        return new RuleProcessingException(reason);
    }
}
