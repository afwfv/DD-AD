package dev.fordes.adfs.error;

import java.io.Serial;

public final class RuleProcessingException extends AdfsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RuleProcessingException(String message) {
        super(ExitCode.RULE_PROCESSING, "rule-processing", message);
    }

    public RuleProcessingException(String message, Throwable cause) {
        super(ExitCode.RULE_PROCESSING, "rule-processing", message, cause);
    }
}
