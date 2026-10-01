package dev.fordes.adfs.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

public record EffectiveConfig(
        Path outputDir,
        InputLimits inputLimits,
        HttpConfig http,
        RuleConfig rules,
        ConversionConfig conversion,
        DnsConfig dns,
        List<InputSpec> inputs,
        List<OutputSpec> outputs) {

    public EffectiveConfig {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    public record InputLimits(long maxSize, int maxLineLength) {
    }

    public record HttpConfig(
            Duration connectTimeout,
            Duration readTimeout,
            int maxRedirects,
            int retries,
            String userAgent) {
    }

    public record PreprocessorConfig(int maxDepth, int maxIncludeDepth, Set<String> trueTokens) {

        public PreprocessorConfig {
            trueTokens = Set.copyOf(trueTokens);
        }
    }

    public record RuleConfig(int minLength, int maxLength, Set<String> whitelist, PreprocessorConfig preprocessor) {

        public RuleConfig {
            whitelist = Set.copyOf(whitelist);
        }
    }

    public record ConversionConfig(boolean allowExpansion, boolean allowReduction) {
    }

    public record DnsCacheConfig(int maxEntries, Duration maxTtl, Duration maxNegativeTtl) {
    }

    public record DnsConfig(
            boolean enabled,
            boolean strictMode,
            List<String> servers,
            int concurrency,
            Duration timeout,
            int retries,
            int maxCnameDepth,
            DnsCacheConfig cache) {

        public DnsConfig {
            servers = List.copyOf(servers);
        }
    }
}
