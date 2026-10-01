package dev.fordes.adfs.rule.model;

public enum CosmeticOperator {
    ELEMENT_HIDE("##"),
    ELEMENT_HIDE_EXCEPTION("#@#"),
    EXTENDED_CSS("#?#"),
    EXTENDED_CSS_EXCEPTION("#@?#"),
    SCRIPTLET("#%#"),
    SCRIPTLET_EXCEPTION("#@%#"),
    CSS_INJECTION("#$#"),
    CSS_INJECTION_EXCEPTION("#@$#"),
    EXTENDED_CSS_INJECTION("#$?#"),
    EXTENDED_CSS_INJECTION_EXCEPTION("#@$?#"),
    HTML_FILTER("$$"),
    HTML_FILTER_EXCEPTION("$@$");

    private final String value;

    CosmeticOperator(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
