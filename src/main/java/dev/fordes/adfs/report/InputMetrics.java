package dev.fordes.adfs.report;

public record InputMetrics(
        long bytes,
        long lines,
        long blankLines,
        long commentLines,
        long rules,
        long invalidRules,
        long httpRetries) {
}
