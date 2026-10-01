package dev.fordes.adfs.error;

import java.io.Serial;

public final class InputException extends AdfsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InputException(String message) {
        super(ExitCode.INPUT, "input", message);
    }

    public InputException(String message, Throwable cause) {
        super(ExitCode.INPUT, "input", message, cause);
    }
}
