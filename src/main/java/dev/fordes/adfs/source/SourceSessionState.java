package dev.fordes.adfs.source;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import dev.fordes.adfs.error.InputException;

final class SourceSessionState {

    private final ByteBudget budget;
    private final SourceCounters counters = new SourceCounters();
    private final int maxIncludeDepth;
    private final List<SourceStream> streams = new ArrayList<>();

    SourceSessionState(long maxSize, int maxIncludeDepth) {
        this.budget = new ByteBudget(maxSize);
        this.maxIncludeDepth = maxIncludeDepth;
    }

    SourceStream register(URI location, String description, int depth, InputStream input) {
        requireDepth(depth, description);
        streams.removeIf(SourceStream::isClosed);
        SourceStream stream = new SourceStream(location, description, depth,
                new SizeLimitedInputStream(input, budget, description), counters);
        streams.add(stream);
        return stream;
    }

    dev.fordes.adfs.report.InputMetrics metrics(long rules) {
        return counters.snapshot(rules);
    }

    void retry() {
        counters.retry();
    }

    void invalidRule() {
        counters.invalidRule();
    }

    long remainingBytes() {
        return budget.remaining();
    }

    SourceStream registerDownloaded(URI location, String description, int depth, DownloadedSource downloaded) {
        budget.consume(downloaded.size(), description);
        streams.removeIf(SourceStream::isClosed);
        SourceStream stream = new SourceStream(location, description, depth, downloaded.input(), counters);
        streams.add(stream);
        return stream;
    }

    void requireDepth(int depth, String description) {
        if (depth > maxIncludeDepth) {
            throw new InputException("include 深度超过上限: " + description + " --> " + maxIncludeDepth);
        }
    }

    void close() {
        List<InputException> failures = new ArrayList<>();
        for (int index = streams.size() - 1; index >= 0; index--) {
            try {
                streams.get(index).close();
            } catch (InputException exception) {
                failures.add(exception);
            }
        }
        if (!failures.isEmpty()) {
            InputException first = failures.getFirst();
            failures.stream().skip(1).forEach(first::addSuppressed);
            throw first;
        }
    }
}
