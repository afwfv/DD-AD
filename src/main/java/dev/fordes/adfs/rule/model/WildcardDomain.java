package dev.fordes.adfs.rule.model;

import java.util.Locale;

public record WildcardDomain(String value, WildcardSyntax syntax) implements DomainPattern {

    public WildcardDomain {
        value = value.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.indexOf('*') < 0) {
            throw new IllegalArgumentException("通配域名必须包含 *");
        }
        java.util.Objects.requireNonNull(syntax);
        if (value.chars().anyMatch(character -> Character.isWhitespace(character) || Character.isISOControl(character))) {
            throw new IllegalArgumentException("通配域名不得包含空白或控制字符");
        }
        if (syntax == WildcardSyntax.MIHOMO_DOMAIN) {
            String labels = value.startsWith("+.") ? value.substring(2) : value.startsWith(".") ? value.substring(1) : value;
            for (String label : labels.split("\\.", -1)) {
                if (!label.equals("*")) {
                    new DomainName(label);
                }
            }
        } else if (syntax == WildcardSyntax.SMARTDNS) {
            if (!value.startsWith("*-")) {
                throw new IllegalArgumentException("SmartDNS 前缀通配符必须以 *- 开始");
            }
            new DomainName(value.substring(2));
        }
    }
}
