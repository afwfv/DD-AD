package dev.fordes.adfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

final class AdfsCommandTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void publishesLocalRulesWithExplicitConfigurationAndDefaultOptions() throws IOException {
        Path input = temporaryDirectory.resolve("rules.txt");
        Path output = temporaryDirectory.resolve("output");
        Path config = temporaryDirectory.resolve("custom.yml");
        Files.writeString(input, "||ads.example^\n");
        Files.writeString(config, """
                adfs:
                  input:
                    - name: local
                      path: '%s'
                      type: adblock
                  output:
                    - name: rules.txt
                      type: adblock
                  config:
                    output-dir: '%s'
                """.formatted(input, output));

        assertEquals(0, AdfsCommand.execute(new String[]{"-c", config.toString()}));
        assertTrue(Files.readString(output.resolve("rules.txt")).contains("||ads.example^"));
    }

    @Test
    void handlesHelpAndVersionBeforeLoadingConfiguration() {
        String missing = temporaryDirectory.resolve("missing.yml").toString();
        assertEquals(0, AdfsCommand.execute(new String[]{"--help", "-c", missing}));
        assertEquals(0, AdfsCommand.execute(new String[]{"--version", "-c", missing}));
        assertEquals(2, AdfsCommand.execute(new String[]{"-c", missing}));
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void mapsConfigurationBindingFailureToConfigurationExitCode() {
        String key = "adfs.config.dns.concurrency";
        String previous = System.setProperty(key, "0");
        try {
            assertEquals(2, AdfsCommand.execute(new String[0]));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }
}
