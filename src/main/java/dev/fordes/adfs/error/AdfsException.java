package dev.fordes.adfs.error;

import java.io.Serial;

public abstract class AdfsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ExitCode exitCode;
    private final String stage;

    protected AdfsException(ExitCode exitCode, String stage, String message) {
        super(message);
        this.exitCode = exitCode;
        this.stage = stage;
    }

    protected AdfsException(ExitCode exitCode, String stage, String message, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
        this.stage = stage;
    }

    public ExitCode exitCode() {
        return exitCode;
    }

    public String stage() {
        return stage;
    }
}
