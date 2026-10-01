package dev.fordes.adfs.rule.model;

import dev.fordes.adfs.error.RuleProcessingException;

public record AdblockModifier(Type type, String value) {

    public AdblockModifier {
        value = value == null ? "" : value;
        if (type.requiresValue() && value.isEmpty()
                || type == Type.BADFILTER && !value.isEmpty()) {
            throw new RuleProcessingException("Adblock 修饰符参数不符合要求: " + type.value());
        }
    }

    public enum Type {
        REDIRECT("redirect", true),
        REDIRECT_RULE("redirect-rule", true),
        REMOVEPARAM("removeparam", false),
        CSP("csp", false),
        PERMISSIONS("permissions", false),
        REPLACE("replace", true),
        URLTRANSFORM("urltransform", true),
        BADFILTER("badfilter", false);

        private final String value;
        private final boolean requiresValue;

        Type(String value, boolean requiresValue) {
            this.value = value;
            this.requiresValue = requiresValue;
        }

        public String value() {
            return value;
        }

        public boolean requiresValue() {
            return requiresValue;
        }
    }
}
