package dev.fordes.adfs.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.error.OutputException;
import dev.fordes.adfs.testing.TestConfigs;

final class OutputPublisherTest {

    @TempDir
    Path directory;

    @Test
    void recoversPreviousOutputAndRejectsConcurrentWriter() throws IOException {
        Path output = directory.resolve("output");
        Path previous = Files.createDirectory(directory.resolve(".output.adfs.previous"));
        Files.writeString(previous.resolve("hosts"), "previous\n");
        StagingWorkspace staging = new StagingWorkspace();
        try (var workspace = staging.open(output)) {
            assertEquals("previous\n", Files.readString(output.resolve("hosts")));
            assertFalse(Files.exists(previous));
            assertThrows(OutputException.class, () -> staging.open(output));
            assertEquals(output, workspace.outputDir());
        }
    }

    @Test
    void rejectsChangedManifestWithoutReplacingOutput() throws IOException {
        Path output = Files.createDirectory(directory.resolve("output"));
        Files.writeString(output.resolve("hosts"), "previous\n");
        try (var workspace = new StagingWorkspace().open(output)) {
            Files.writeString(workspace.nextDir().resolve("hosts"), "next\n");
            PublishManifest manifest = PublishManifest.create(workspace,
                    List.of(TestConfigs.textOutput("hosts", RuleType.HOSTS, RuleDialect.NONE)));
            Files.writeString(workspace.nextDir().resolve("hosts"), "changed\n");
            assertThrows(OutputException.class, () -> new OutputPublisher().publish(workspace, manifest));
            assertEquals("previous\n", Files.readString(output.resolve("hosts")));
        }
    }
}
