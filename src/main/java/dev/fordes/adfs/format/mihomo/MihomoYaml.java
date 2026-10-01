package dev.fordes.adfs.format.mihomo;

import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.source.SourceLine;

final class MihomoYaml {

    private boolean decided;
    private boolean yaml;
    private boolean empty;
    private int indentation = -1;

    MihomoYaml() {
    }

    String read(SourceLine line) {
        String text = stripComment(line.text().strip());
        if (text.isBlank()) {
            return null;
        }
        if (!decided) {
            decided = true;
            yaml = text.equals("payload:") || text.equals("payload: []");
            empty = text.equals("payload: []");
            if (yaml) {
                if (!line.text().startsWith("payload:")) {
                    throw failure(line, "payload 必须位于顶层");
                }
                return null;
            }
        }
        if (!yaml) {
            return line.text().strip();
        }
        int indent = line.text().length() - line.text().stripLeading().length();
        if (empty || !text.startsWith("- ") || line.text().substring(0, indent).indexOf('\t') >= 0
                || indentation >= 0 && indent != indentation) {
            throw failure(line, "payload 只接受相同缩进的字符串序列");
        }
        indentation = indent;
        return scalar(text.substring(2).strip(), line);
    }

    private static String scalar(String text, SourceLine line) {
        if (text.isEmpty()) {
            throw failure(line, "序列项不得为空");
        }
        char quote = text.charAt(0);
        if (quote != '\'' && quote != '"') {
            if ("&*!|>{[".indexOf(quote) >= 0 || text.contains(": ")) {
                throw failure(line, "不支持该 YAML 标量形式");
            }
            return text;
        }
        StringBuilder result = new StringBuilder();
        for (int index = 1; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == quote) {
                if (quote == '\'' && index + 1 < text.length() && text.charAt(index + 1) == '\'') {
                    result.append('\'');
                    index++;
                } else if (index == text.length() - 1) {
                    return result.toString();
                } else {
                    throw failure(line, "引号结束后存在额外内容");
                }
            } else if (current == '\\' && quote == '"') {
                if (++index == text.length()) {
                    throw failure(line, "转义未结束");
                }
                char escape = text.charAt(index);
                if (escape == 'x' || escape == 'u' || escape == 'U') {
                    int digits = escape == 'x' ? 2 : escape == 'u' ? 4 : 8;
                    if (index + digits >= text.length()) {
                        throw failure(line, "Unicode 转义未结束");
                    }
                    try {
                        int point = Integer.parseInt(text.substring(index + 1, index + 1 + digits), 16);
                        if (!Character.isValidCodePoint(point) || point >= 0xD800 && point <= 0xDFFF) {
                            throw failure(line, "Unicode 码点非法");
                        }
                        result.appendCodePoint(point);
                    } catch (NumberFormatException exception) {
                        throw failure(line, "Unicode 转义非法");
                    }
                    index += digits;
                } else {
                    result.append(switch (escape) {
                        case '0' -> '\0';
                        case 'a' -> '\u0007';
                        case 'b' -> '\b';
                        case 't', '\t' -> '\t';
                        case 'n' -> '\n';
                        case 'v' -> '\u000b';
                        case 'f' -> '\f';
                        case 'r' -> '\r';
                        case 'e' -> '\u001b';
                        case ' ', '"', '/', '\\' -> escape;
                        case 'N' -> '\u0085';
                        case '_' -> '\u00a0';
                        case 'L' -> '\u2028';
                        case 'P' -> '\u2029';
                        default -> throw failure(line, "未知 YAML 转义");
                    });
                }
            } else {
                result.append(current);
            }
        }
        throw failure(line, "引号未闭合");
    }

    private static RuleProcessingException failure(SourceLine line, String reason) {
        return new RuleProcessingException("Mihomo YAML 非法: " + reason);
    }

    static String stripComment(String line) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaped = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\' && doubleQuoted) {
                escaped = true;
            } else if (character == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (character == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (character == '#' && !singleQuoted && !doubleQuoted
                    && (index == 0 || Character.isWhitespace(line.charAt(index - 1)))) {
                return line.substring(0, index).stripTrailing();
            }
        }
        return line;
    }
}
