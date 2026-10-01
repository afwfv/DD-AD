package dev.fordes.adfs.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.fordes.adfs.error.ConfigurationException;

final class ConfigValidatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesDefaultsAndNormalizesConfiguredDomains() throws Exception {
        Path input = temporaryDirectory.resolve("rules.txt");
        Files.writeString(input, "0.0.0.0 ads.example\n");
        RuleProperties rules = new RuleProperties();
        rules.setWhitelist(List.of("BÜCHER.Example."));
        PreprocessorProperties preprocessor = new PreprocessorProperties();
        preprocessor.setTrueTokens(List.of("desktop"));

        EffectiveConfig config = validator(
                temporaryDirectory.resolve("output"), input, "hosts", null,
                "result.txt", "hosts", null, null, rules, preprocessor).validate();

        assertEquals(67_108_864L, config.inputLimits().maxSize());
        assertEquals(Duration.ofSeconds(10), config.http().connectTimeout());
        assertEquals(RuleDialect.NONE, config.inputs().getFirst().dialect());
        assertEquals(ContainerFormat.TEXT, config.outputs().getFirst().container());
        assertEquals(List.of("desktop"), config.rules().preprocessor().trueTokens().stream().toList());
        assertEquals(List.of("xn--bcher-kva.example"), config.rules().whitelist().stream().toList());
        assertFalse(config.dns().enabled());
    }

    @Test
    void rejectsIncompatibleDialect() throws Exception {
        Path input = temporaryDirectory.resolve("input.txt");
        Files.writeString(input, "example.com\n");
        RuleProperties rules = new RuleProperties();
        PreprocessorProperties preprocessor = new PreprocessorProperties();

        ConfigValidator incompatible = validator(
                temporaryDirectory.resolve("output"), input, "hosts", "abp",
                "result.txt", "hosts", null, null, rules, preprocessor);
        assertThrows(ConfigurationException.class, incompatible::validate);
    }

    @Test
    void allowsInputUnderOutputDirectoryAndRejectsOutputInputConflict() throws Exception {
        Path input = temporaryDirectory.resolve("input.txt");
        Files.writeString(input, "example.com\n");
        RuleProperties rules = new RuleProperties();
        PreprocessorProperties preprocessor = new PreprocessorProperties();

        ConfigValidator separatePaths = validator(
                temporaryDirectory, input, "hosts", null,
                "result.txt", "hosts", null, null, rules, preprocessor);
        assertDoesNotThrow(separatePaths::validate);

        ConfigValidator conflictingPaths = validator(
                temporaryDirectory, input, "hosts", null,
                "input.txt", "hosts", null, null, rules, preprocessor);
        assertThrows(ConfigurationException.class, conflictingPaths::validate);
    }

    @Test
    void rejectsCrossFieldLimitsAndInvalidDnsLiteral() throws Exception {
        Path input = temporaryDirectory.resolve("rules.txt");
        Files.writeString(input, "example.com\n");
        RuleProperties rules = new RuleProperties();
        rules.setMinLength(20);
        rules.setMaxLength(10);
        PreprocessorProperties preprocessor = new PreprocessorProperties();
        ConfigValidator limits = validator(
                temporaryDirectory.resolve("output"), input, "hosts", null,
                "result.txt", "hosts", null, null, rules, preprocessor);
        assertThrows(ConfigurationException.class, limits::validate);

        rules.setMinLength(1);
        rules.setMaxLength(10);
        ConfigValidator dns = validator(
                temporaryDirectory.resolve("output"), input, "hosts", null,
                "result.txt", "hosts", null, "resolver.example", rules, preprocessor);
        assertThrows(ConfigurationException.class, dns::validate);
    }

    private static ConfigValidator validator(
            Path outputDir,
            Path inputPath,
            String inputType,
            String inputDialect,
            String outputName,
            String outputType,
            String outputDialect,
            String dnsServer,
            RuleProperties rules,
            PreprocessorProperties preprocessor) {
        AdfsProperties adfs = new AdfsProperties();
        adfs.setOutputDir(outputDir);
        InputProperties input = new InputProperties(0);
        input.setName("input");
        input.setPath(inputPath.toString());
        input.setType(inputType);
        input.setDialect(inputDialect);
        OutputProperties output = new OutputProperties(0);
        output.setName(outputName);
        output.setType(outputType);
        output.setDialect(outputDialect);
        DnsProperties dns = new DnsProperties();
        dns.setServers(dnsServer == null ? List.of() : List.of(dnsServer));
        return new ConfigValidator(
                adfs,
                new InputLimitProperties(),
                new HttpProperties(),
                rules,
                preprocessor,
                new ConversionProperties(),
                dns,
                new DnsCacheProperties(),
                List.of(input),
                List.of(output));
    }
}
