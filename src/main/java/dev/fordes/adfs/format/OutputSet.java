package dev.fordes.adfs.format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OutputSet implements AutoCloseable {

    private final List<OutputTarget> targets = new ArrayList<>();
    private final List<OutputTarget> view = Collections.unmodifiableList(targets);

    public void add(OutputTarget target) {
        targets.add(target);
    }

    public List<OutputTarget> targets() {
        return view;
    }

    @Override
    public void close() {
        List<RuntimeException> failures = new ArrayList<>();
        for (int index = targets.size() - 1; index >= 0; index--) {
            try {
                targets.get(index).close();
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
        if (!failures.isEmpty()) {
            RuntimeException first = failures.getFirst();
            failures.stream().skip(1).forEach(first::addSuppressed);
            throw first;
        }
    }
}
