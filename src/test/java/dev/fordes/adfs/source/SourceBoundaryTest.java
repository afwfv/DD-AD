package dev.fordes.adfs.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.fordes.adfs.config.EffectiveConfig;
import dev.fordes.adfs.config.EffectiveConfig.InputLimits;
import dev.fordes.adfs.config.InputSpec;
import dev.fordes.adfs.config.InputSpec.LocalSource;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.error.InputException;
import dev.fordes.adfs.testing.TestConfigs;

final class SourceBoundaryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void decodesUtf8StrictlyAndEnforcesLineLengthBeforeGrowingBuffer() throws IOException {
        Path invalid = temporaryDirectory.resolve("invalid.txt");
        Files.write(invalid, new byte[] {(byte) 0xc3, 0x28});
        EffectiveConfig invalidConfig = config(invalid, 1_048_576, 16);

        try (SourceSession session = new LocalSourceReader().open(invalidConfig.inputs().getFirst(), invalidConfig);
                BoundedLineReader reader = new BoundedLineReader(session.root(), 16)) {
            assertThrows(InputException.class, reader::readLine);
        }

        Path longLine = temporaryDirectory.resolve("long.txt");
        Files.writeString(longLine, "12345", StandardCharsets.UTF_8);
        EffectiveConfig longConfig = config(longLine, 1_048_576, 4);
        try (SourceSession session = new LocalSourceReader().open(longConfig.inputs().getFirst(), longConfig);
                BoundedLineReader reader = new BoundedLineReader(session.root(), 4)) {
            InputException exception = assertThrows(InputException.class, reader::readLine);
            assertTrue(exception.getMessage().contains("物理行超过字符上限"));
            assertTrue(exception.getMessage().contains("--> 4"));
        }
    }

    @Test
    void rejectsIncludeTraversalAndSharesByteBudgetAcrossIncludes() throws IOException {
        Path rootDirectory = Files.createDirectory(temporaryDirectory.resolve("rules"));
        Path root = rootDirectory.resolve("root.txt");
        Path child = rootDirectory.resolve("child.txt");
        Files.writeString(root, "abc", StandardCharsets.UTF_8);
        Files.writeString(child, "def", StandardCharsets.UTF_8);
        EffectiveConfig config = config(root, 5, 16);

        try (SourceSession session = new LocalSourceReader().open(config.inputs().getFirst(), config)) {
            assertThrows(InputException.class, () -> session.openInclude(session.root(), "../outside.txt"));
            assertEquals(3, session.root().input().readAllBytes().length);
            SourceStream included = session.openInclude(session.root(), "child.txt");
            assertThrows(InputException.class, () -> included.input().readAllBytes());
        }
    }

    private static EffectiveConfig config(Path input, long maxSize, int maxLineLength) {
        EffectiveConfig base = TestConfigs.create(
                input, RuleType.HOSTS, RuleDialect.NONE, input.getParent().resolve("output"), List.of(), false);
        InputSpec inputSpec = new InputSpec("source-test", new LocalSource(input), RuleType.HOSTS, RuleDialect.NONE);
        return new EffectiveConfig(
                base.outputDir(), new InputLimits(maxSize, maxLineLength), base.http(), base.rules(),
                base.conversion(), base.dns(), List.of(inputSpec), List.of());
    }
}
