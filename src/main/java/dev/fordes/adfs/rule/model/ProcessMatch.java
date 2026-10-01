package dev.fordes.adfs.rule.model;

public record ProcessMatch(ProcessField field, String value) implements MatchExpression {

    public ProcessMatch {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("进程匹配值不得为空");
        }
    }

    public enum ProcessField {
        NAME("name"),
        PATH("path"),
        PACKAGE("package");

        private final String value;

        ProcessField(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
