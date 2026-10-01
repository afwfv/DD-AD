package dev.fordes.adfs.format.adblock;

import java.util.Set;

import dev.fordes.adfs.error.RuleProcessingException;

final class BooleanExpression {

    private static final int MAX_EXPRESSION_DEPTH = 128;
    private final String source;
    private final Set<String> trueTokens;
    private int index;
    private int depth;

    private BooleanExpression(String source, Set<String> trueTokens) {
        this.source = source;
        this.trueTokens = trueTokens;
    }

    static boolean evaluate(String source, Set<String> trueTokens) {
        BooleanExpression expression = new BooleanExpression(source, trueTokens);
        boolean result = expression.parseOr();
        expression.skipWhitespace();
        if (expression.index != source.length()) {
            throw expression.failure("条件表达式包含多余内容");
        }
        return result;
    }

    private boolean parseOr() {
        boolean value = parseAnd();
        while (consume("||")) {
            boolean right = parseAnd();
            value = value || right;
        }
        return value;
    }

    private boolean parseAnd() {
        boolean value = parseUnary();
        while (consume("&&")) {
            boolean right = parseUnary();
            value = value && right;
        }
        return value;
    }

    private boolean parseUnary() {
        if (++depth > MAX_EXPRESSION_DEPTH) {
            throw failure("条件表达式嵌套超过上限: " + MAX_EXPRESSION_DEPTH);
        }
        try {
            return parseOperand();
        } finally {
            depth--;
        }
    }

    private boolean parseOperand() {
        skipWhitespace();
        if (consume("!")) {
            return !parseUnary();
        }
        if (consume("(")) {
            boolean value = parseOr();
            if (!consume(")")) {
                throw failure("条件表达式缺少右括号");
            }
            return value;
        }
        String token = parseToken();
        return token.equals("true") || !token.equals("false") && trueTokens.contains(token);
    }

    private String parseToken() {
        skipWhitespace();
        int start = index;
        while (index < source.length()) {
            char character = source.charAt(index);
            if (!(character >= 'a' && character <= 'z' || character >= '0' && character <= '9'
                    || character == '_')) {
                break;
            }
            index++;
        }
        if (start == index || !Character.isLowerCase(source.charAt(start))) {
            throw failure("条件表达式需要小写 token");
        }
        return source.substring(start, index);
    }

    private boolean consume(String token) {
        skipWhitespace();
        if (!source.startsWith(token, index)) {
            return false;
        }
        index += token.length();
        return true;
    }

    private void skipWhitespace() {
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
            index++;
        }
    }

    private RuleProcessingException failure(String message) {
        return new RuleProcessingException(message + ": " + source + " --> 偏移 " + index);
    }
}
