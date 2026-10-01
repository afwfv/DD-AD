package dev.fordes.adfs.error;

public enum ExitCode {
    SUCCESS(0),
    CONFIGURATION(2),
    INPUT(3),
    RULE_PROCESSING(4),
    DNS(5),
    OUTPUT(6);

    private final int value;

    ExitCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
