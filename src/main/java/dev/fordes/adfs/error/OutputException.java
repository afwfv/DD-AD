package dev.fordes.adfs.error;

import java.io.Serial;

public final class OutputException extends AdfsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OutputException(String message) {
        super(ExitCode.OUTPUT, "output", message);
    }

    public OutputException(String message, Throwable cause) {
        super(ExitCode.OUTPUT, "output", message, cause);
    }
}
