package dev.fordes.adfs.error;

import java.io.Serial;

public final class DnsException extends AdfsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DnsException(String message) {
        super(ExitCode.DNS, "dns", message);
    }

    public DnsException(String message, Throwable cause) {
        super(ExitCode.DNS, "dns", message, cause);
    }
}
