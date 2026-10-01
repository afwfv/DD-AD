package dev.fordes.adfs.source;

import dev.fordes.adfs.report.InputMetrics;

final class SourceCounters {

    private long bytes;
    private long lines;
    private long blankLines;
    private long commentLines;
    private long invalidRules;
    private long retries;

    void bytes(int count) {
        if (count > 0) {
            bytes += count;
        }
    }

    void line(String text) {
        lines++;
        if (text.isBlank()) {
            blankLines++;
        }
    }

    void comment() {
        commentLines++;
    }

    void invalidRule() {
        invalidRules++;
    }

    void retry() {
        retries++;
    }

    InputMetrics snapshot(long rules) {
        return new InputMetrics(bytes, lines, blankLines, commentLines, rules, invalidRules, retries);
    }
}
