package dev.fordes.adfs.report;

public record DnsMetrics(
        long checked,
        long valid,
        long invalid,
        long failed,
        long skipped,
        long merged,
        int cacheEntries) {
}
