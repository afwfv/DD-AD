package dev.fordes.adfs.rule.model;

import dev.fordes.adfs.error.RuleProcessingException;

public record AdblockPattern(Kind kind, String value) {

    public AdblockPattern {
        value = value.strip();
        if (value.isEmpty()) {
            throw new RuleProcessingException("Adblock 匹配主体不得为空");
        }
    }

    public enum Kind {
        DOMAIN_ANCHOR("domain-anchor"),
        URL("url"),
        REGEX("regex");

        private final String value;

        Kind(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
