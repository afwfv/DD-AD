package dev.fordes.adfs.error;

import java.io.Serial;

public final class ConfigurationException extends AdfsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(ExitCode.CONFIGURATION, "configuration", message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(ExitCode.CONFIGURATION, "configuration", message, cause);
    }
}
