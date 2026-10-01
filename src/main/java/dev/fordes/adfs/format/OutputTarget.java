package dev.fordes.adfs.format;

import java.util.ArrayList;
import java.util.List;

import dev.fordes.adfs.config.OutputSpec;
import dev.fordes.adfs.rule.dedup.CanonicalStore;
import dev.fordes.adfs.rule.dedup.OutputDeduplicator;
import dev.fordes.adfs.rule.model.RuleEntry;

public final class OutputTarget implements AutoCloseable {

    private final OutputSpec spec;
    private final RuleWriter writer;
    private final CanonicalStore store;
    private final OutputDeduplicator deduplicator;
    private boolean closed;

    public OutputTarget(OutputSpec spec, RuleWriter writer, CanonicalStore store, OutputDeduplicator deduplicator) {
        this.spec = spec;
        this.writer = writer;
        this.store = store;
        this.deduplicator = deduplicator;
    }

    public OutputSpec spec() {
        return spec;
    }

    public WriteResult write(RuleEntry entry) {
        return writer.write(entry);
    }

    public FinishResult finish() {
        return writer.finish();
    }

    /** 成功关闭后包含补充白名单在内的最终规则条数。 */
    public int ruleCount() {
        return deduplicator.size();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<RuntimeException> failures = new ArrayList<>();
        try {
            writer.close();
        } catch (RuntimeException exception) {
            failures.add(exception);
        }
        try {
            store.close();
        } catch (RuntimeException exception) {
            failures.add(exception);
        }
        if (!failures.isEmpty()) {
            RuntimeException first = failures.getFirst();
            failures.stream().skip(1).forEach(first::addSuppressed);
            throw first;
        }
    }
}
