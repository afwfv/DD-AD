package dev.fordes.adfs.testing;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import dev.fordes.adfs.config.ContainerFormat;
import dev.fordes.adfs.config.EffectiveConfig;
import dev.fordes.adfs.config.EffectiveConfig.ConversionConfig;
import dev.fordes.adfs.config.EffectiveConfig.DnsCacheConfig;
import dev.fordes.adfs.config.EffectiveConfig.DnsConfig;
import dev.fordes.adfs.config.EffectiveConfig.HttpConfig;
import dev.fordes.adfs.config.EffectiveConfig.InputLimits;
import dev.fordes.adfs.config.EffectiveConfig.PreprocessorConfig;
import dev.fordes.adfs.config.EffectiveConfig.RuleConfig;
import dev.fordes.adfs.config.InputSpec;
import dev.fordes.adfs.config.InputSpec.LocalSource;
import dev.fordes.adfs.config.OutputSpec;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;

public final class TestConfigs {

    private TestConfigs() {
    }

    public static EffectiveConfig create(
            Path input,
            RuleType inputType,
            RuleDialect inputDialect,
            Path outputDir,
            List<OutputSpec> outputs,
            boolean dnsEnabled) {
        InputSpec inputSpec = new InputSpec("test-input", new LocalSource(input), inputType, inputDialect);
        return new EffectiveConfig(
                outputDir,
                new InputLimits(1_048_576, 262_144),
                new HttpConfig(Duration.ofSeconds(1), Duration.ofSeconds(1), 2, 0, "AdFS-Test/1"),
                rules(),
                new ConversionConfig(false, false),
                new DnsConfig(dnsEnabled, false, List.of(), 4, Duration.ofSeconds(1), 0, 4,
                        new DnsCacheConfig(1_024, Duration.ofMinutes(1), Duration.ofSeconds(30))),
                List.of(inputSpec),
                outputs);
    }

    public static RuleConfig rules() {
        return new RuleConfig(1, 65_536, Set.of(), new PreprocessorConfig(16, 8, Set.of()));
    }

    public static OutputSpec textOutput(String name, RuleType type, RuleDialect dialect) {
        return new OutputSpec(Path.of(name), type, dialect, ContainerFormat.TEXT, "");
    }
}
