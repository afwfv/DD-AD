package dev.fordes.adfs.format.adblock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.fordes.adfs.error.RuleProcessingException;

/** 保留选项原文, 并识别正则、引号和转义中的分隔符。 */
public final class AdblockSyntax {

    private static final List<String> COSMETIC_OPERATORS = List.of(
            "#@$?#", "#$?#", "#@?#", "#@%#", "#@$#", "#@#", "#?#", "#%#", "#$#", "$@$", "##", "$$");

    private AdblockSyntax() {
    }

    public static List<String> networkOptions(String text) {
        if (cosmeticOperator(text) != null) {
            return List.of();
        }
        int separator = optionSeparator(text);
        return separator < 0 ? List.of() : options(text.substring(separator + 1));
    }

    public static CosmeticLocation cosmeticOperator(String text) {
        boolean escaped = false;
        char quote = 0;
        int depth = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                }
            } else if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '(') {
                depth++;
            } else if (character == ')' && depth > 0) {
                depth--;
            } else if (depth == 0) {
                for (String operator : COSMETIC_OPERATORS) {
                    if (text.startsWith(operator, index) && cosmeticPrefix(text.substring(0, index))) {
                        return new CosmeticLocation(index, operator);
                    }
                }
            }
        }
        return null;
    }

    private static boolean cosmeticPrefix(String prefix) {
        if (prefix.isEmpty() || prefix.startsWith("[")) {
            return true;
        }
        if (prefix.startsWith("@@")) {
            return false;
        }
        if (prefix.startsWith("/")) {
            return prefix.length() > 1 && prefix.endsWith("/");
        }
        return prefix.indexOf('/') < 0 && prefix.indexOf('|') < 0
                && prefix.indexOf('^') < 0 && prefix.indexOf('$') < 0;
    }

    public static int optionSeparator(String text) {
        int start = text.startsWith("@@") ? 2 : 0;
        int regexEnd = text.startsWith("/", start) ? regexEnd(text, start) : -1;
        if (regexEnd >= 0) {
            return regexEnd + 1 == text.length() ? -1 : regexEnd + 1;
        }
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '$') {
                return index;
            }
        }
        return -1;
    }

    private static int regexEnd(String text, int start) {
        boolean escaped = false;
        boolean characterClass = false;
        for (int index = start + 1; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '[') {
                characterClass = true;
            } else if (character == ']') {
                characterClass = false;
            } else if (character == '/' && !characterClass
                    && (index + 1 == text.length() || text.charAt(index + 1) == '$')) {
                return index;
            }
        }
        return -1;
    }

    public static List<String> options(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        int start = 0;
        int depth = 0;
        int regexSlashes = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            if (index == start) {
                int equals = text.indexOf('=', start);
                String name = equals < 0 ? "" : text.substring(start, equals).strip().toLowerCase(Locale.ROOT);
                if (name.equals("replace") || name.equals("urltransform")
                        || name.equals("uritransform") || name.equals("urlskip")) {
                    int end = rawOptionEnd(text, equals + 1);
                    options.add(requireOption(text.substring(start, end)));
                    if (end == text.length()) {
                        return List.copyOf(options);
                    }
                    index = end;
                    start = end + 1;
                    continue;
                }
            }
            char character = text.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (regexSlashes > 0) {
                if (character == '/') {
                    regexSlashes--;
                }
            } else if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                }
            } else if (character == '/' && regexValueStart(text, start, index)) {
                int equals = text.indexOf('=', start);
                String name = text.substring(start, equals).strip().toLowerCase(Locale.ROOT);
                regexSlashes = name.equals("replace") || name.equals("urltransform") ? 2 : 1;
            } else if (character == '\'' || character == '"') {
                quote = character;
            } else if (character == '(') {
                depth++;
            } else if (character == ')') {
                if (--depth < 0) {
                    throw new RuleProcessingException("Adblock 选项包含多余右括号");
                }
            } else if (character == ',' && depth == 0) {
                options.add(requireOption(text.substring(start, index)));
                start = index + 1;
            }
        }
        if (escaped || quote != 0 || depth != 0 || regexSlashes != 0) {
            throw new RuleProcessingException("Adblock 选项包含未闭合的转义、引号、正则或括号");
        }
        options.add(requireOption(text.substring(start)));
        return List.copyOf(options);
    }

    private static String requireOption(String text) {
        String option = text.strip();
        if (option.isEmpty()) {
            throw new RuleProcessingException("Adblock 选项列表包含空项");
        }
        return option;
    }

    private static boolean regexValueStart(String text, int optionStart, int slash) {
        int equals = text.indexOf('=', optionStart);
        if (equals < 0 || equals >= slash) {
            return false;
        }
        int entryStart = text.lastIndexOf('|', slash - 1);
        entryStart = Math.max(equals, entryStart) + 1;
        String prefix = text.substring(entryStart, slash).strip();
        return prefix.isEmpty() || prefix.equals("~");
    }

    private static int rawOptionEnd(String text, int start) {
        boolean escaped = false;
        for (int index = start; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == ',') {
                return index;
            }
        }
        if (escaped) {
            throw new RuleProcessingException("Adblock 替换选项的转义未结束");
        }
        return text.length();
    }

    public record CosmeticLocation(int index, String operator) {
    }
}
