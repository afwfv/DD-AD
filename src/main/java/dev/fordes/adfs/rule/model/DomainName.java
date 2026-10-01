package dev.fordes.adfs.rule.model;

import java.net.IDN;
import java.util.Locale;
import java.util.Optional;

import dev.fordes.adfs.error.RuleProcessingException;

/** 规则格式共享的 DNS 名称; 支持 IDN、根标签尾点以及未转义的 LDH/下划线标签。 */
public record DomainName(String value) implements Comparable<DomainName> {

    private static final int MAX_NAME_LENGTH = 253;
    private static final int MAX_LABEL_LENGTH = 63;

    public DomainName {
        value = normalize(value);
    }

    public static Optional<DomainName> tryParse(String value) {
        try {
            return Optional.of(new DomainName(value));
        } catch (RuleProcessingException exception) {
            return Optional.empty();
        }
    }

    private static String normalize(String input) {
        if (input == null) {
            throw new RuleProcessingException("域名不得为 null");
        }
        String original = input;
        String ascii;
        try {
            ascii = IDN.toASCII(input.strip()).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new RuleProcessingException("域名 IDN 转换失败: " + original, exception);
        }
        if (ascii.endsWith(".")) {
            ascii = ascii.substring(0, ascii.length() - 1);
        }
        if (ascii.isEmpty() || ascii.length() > MAX_NAME_LENGTH || ascii.startsWith(".") || ascii.endsWith(".")) {
            throw new RuleProcessingException("域名长度或边界非法: " + original);
        }
        for (String label : ascii.split("\\.", -1)) {
            validateLabel(label, original);
        }
        return ascii;
    }

    private static void validateLabel(String label, String domain) {
        if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH) {
            throw new RuleProcessingException("域名标签长度非法: " + domain + " --> " + label);
        }
        boolean valid = label.chars().allMatch(character -> character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9' || character == '-' || character == '_');
        if (!valid) {
            throw new RuleProcessingException("域名标签包含非法字符: " + domain + " --> " + label);
        }
    }

    @Override
    public int compareTo(DomainName other) {
        return value.compareTo(other.value);
    }
}
