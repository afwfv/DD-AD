package dev.fordes.adfs.format.conversion;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** 仅转换 JavaScript 与目标 RE2 共有且能够在本地验证的 ASCII 正则子集。 */
public final class RegexCompatibility {

    private RegexCompatibility() {
    }

    public static Optional<String> adblockToDomain(String expression, boolean matchCase) {
        if (expression.isEmpty() || expression.chars().anyMatch(value -> value < 32 || value > 126)
                || expression.contains("&&") || expression.contains("[^")
                || expression.contains("{ ") || expression.contains("*+") || expression.contains("++")
                || expression.contains("?+") || expression.contains("}+")) {
            return Optional.empty();
        }
        boolean characterClass = false;
        for (int index = 0; index < expression.length(); index++) {
            char value = expression.charAt(index);
            if (value == '\\') {
                if (++index == expression.length()) {
                    return Optional.empty();
                }
                char escaped = expression.charAt(index);
                if (Character.isLetterOrDigit(escaped) && "dDsSwW".indexOf(escaped) < 0) {
                    return Optional.empty();
                }
            } else if (value == '[') {
                if (characterClass) {
                    return Optional.empty();
                }
                characterClass = true;
            } else if (value == ']') {
                characterClass = false;
            } else if (value == '(' && index + 1 < expression.length() && expression.charAt(index + 1) == '?'
                    && !expression.startsWith("(?:", index)) {
                return Optional.empty();
            } else if (value == '{') {
                int end = expression.indexOf('}', index);
                if (end < 0) {
                    return Optional.empty();
                }
                String repetition = expression.substring(index + 1, end);
                if (!repetition.matches("[0-9]{1,4}(,[0-9]{0,4})?")) {
                    return Optional.empty();
                }
                for (String number : repetition.split(",")) {
                    if (Integer.parseInt(number) > 1_000) {
                        return Optional.empty();
                    }
                }
                index = end;
            }
        }
        try {
            Pattern.compile(expression);
        } catch (PatternSyntaxException exception) {
            return Optional.empty();
        }
        return Optional.of(matchCase ? expression : "(?i:" + expression + ")");
    }
}
