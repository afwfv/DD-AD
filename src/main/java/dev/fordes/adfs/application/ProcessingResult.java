package dev.fordes.adfs.application;

import java.nio.file.Path;
import java.util.Map;

import dev.fordes.adfs.report.DnsMetrics;
import dev.fordes.adfs.report.InputMetrics;
import dev.fordes.adfs.report.OutputMetrics;
import dev.fordes.adfs.report.ProcessingStage;

public record ProcessingResult(
        long inputs,
        long semanticRules,
        long opaqueRules,
        long uniqueRules,
        long duplicateRules,
        int hashEntries,
        int hashCapacity,
        int hashCollisions,
        DnsMetrics dns,
        Map<Path, OutputMetrics> outputs,
        long elapsedMillis,
        Map<String, InputMetrics> sources,
        Map<ProcessingStage, Long> stages) {

    public ProcessingResult {
        outputs = Map.copyOf(outputs);
        sources = Map.copyOf(sources);
        stages = Map.copyOf(stages);
    }
}
