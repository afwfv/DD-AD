package dev.fordes.adfs.config;

import java.nio.file.Path;

public record OutputSpec(Path path, RuleType type, RuleDialect dialect, ContainerFormat container, String fileHeader) {
}
