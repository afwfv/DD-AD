package dev.fordes.adfs.publish;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

import dev.fordes.adfs.config.FileHeaderTemplate;
import dev.fordes.adfs.config.OutputSpec;
import dev.fordes.adfs.error.OutputException;
import dev.fordes.adfs.publish.StagingWorkspace.Workspace;

/** 在正文关闭后合成文件头; 所有操作只发生在本次临时工作区。 */
public final class OutputHeader {

    private OutputHeader() {
    }

    public static void prepend(Workspace workspace, OutputSpec spec, OffsetDateTime generatedAt, long total) {
        if (spec.fileHeader().isBlank()) {
            return;
        }
        String prefix = switch (spec.type()) {
            case ADBLOCK, DNS -> "! ";
            case HOSTS, DNSMASQ, SMARTDNS, MIHOMO -> "# ";
            case SING_BOX -> throw new OutputException("JSON 输出不支持文件头: " + spec.path());
        };
        String header = FileHeaderTemplate.render(spec, generatedAt, total).lines()
                .map(line -> prefix + line)
                .collect(Collectors.joining("\n", "", "\n"));
        Path body = workspace.nextDir().resolve(spec.path());
        try {
            Path combined = Files.createTempFile(workspace.runDir(), "header-", ".tmp");
            try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(combined))) {
                output.write(header.getBytes(StandardCharsets.UTF_8));
                Files.copy(body, output);
            }
            Files.move(combined, body, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new OutputException("合成文件头失败: " + body + " --> " + exception.getMessage(), exception);
        }
    }
}
