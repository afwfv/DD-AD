package dev.fordes.adfs.config;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;

import dev.fordes.adfs.error.ConfigurationException;

public final class ProgramLocation {

    private ProgramLocation() {
    }

    public static Path directory() {
        if ("runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            String command = ProcessHandle.current().info().command()
                    .orElseThrow(() -> new ConfigurationException("无法确定原生程序路径"));
            return Path.of(command).toAbsolutePath().normalize().getParent();
        }
        CodeSource source = ProgramLocation.class.getProtectionDomain().getCodeSource();
        if (source == null) {
            throw new ConfigurationException("无法确定程序路径: CodeSource 不可用");
        }
        try {
            Path location = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
            return Files.isDirectory(location) ? location : location.getParent();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new ConfigurationException("无法确定程序路径: " + source.getLocation(), exception);
        }
    }
}
