package dev.fordes.adfs.config;

import java.net.URI;
import java.nio.file.Path;

public record InputSpec(String name, SourceLocation source, RuleType type, RuleDialect dialect) {

    public sealed interface SourceLocation permits HttpSource, LocalSource {
    }

    public record LocalSource(Path path) implements SourceLocation {
    }

    public record HttpSource(URI uri) implements SourceLocation {
    }
}
