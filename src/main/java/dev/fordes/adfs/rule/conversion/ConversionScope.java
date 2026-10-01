package dev.fordes.adfs.rule.conversion;

public enum ConversionScope {
    EXACT("exact"),
    EXPANDED("expanded"),
    REDUCED("reduced"),
    MIXED("mixed"),
    UNSUPPORTED("unsupported");

    private final String value;

    ConversionScope(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public ConversionScope combine(ConversionScope other) {
        if (this == UNSUPPORTED || other == UNSUPPORTED) {
            return UNSUPPORTED;
        }
        boolean expansion = this == EXPANDED || this == MIXED || other == EXPANDED || other == MIXED;
        boolean reduction = this == REDUCED || this == MIXED || other == REDUCED || other == MIXED;
        if (expansion && reduction) {
            return MIXED;
        }
        if (expansion) {
            return EXPANDED;
        }
        if (reduction) {
            return REDUCED;
        }
        return EXACT;
    }
}
