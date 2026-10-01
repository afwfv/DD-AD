package dev.fordes.adfs.report;

public record OutputMetrics(
        long written,
        long passthrough,
        long duplicates,
        long whitelistRemoved,
        long unsupported,
        long whitelistAdded,
        long bytes) {
}
