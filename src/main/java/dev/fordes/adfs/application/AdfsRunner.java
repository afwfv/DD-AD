package dev.fordes.adfs.application;

import dev.fordes.adfs.config.ConfigValidator;
import dev.fordes.adfs.config.EffectiveConfig;
import dev.fordes.adfs.error.ExitCode;
import dev.fordes.adfs.report.RunReporter;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public final class AdfsRunner {

    private final ConfigValidator configValidator;
    private final ProcessingPipeline pipeline;
    private final RunReporter reporter;

    public ExitCode run() {
        EffectiveConfig config = configValidator.validate();
        ProcessingResult result = pipeline.process(config);
        reporter.report(result);
        return ExitCode.SUCCESS;
    }
}
