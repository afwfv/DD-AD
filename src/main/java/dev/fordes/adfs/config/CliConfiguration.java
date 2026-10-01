package dev.fordes.adfs.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.micronaut.context.env.AbstractPropertySourceLoader;
import io.micronaut.context.env.EnvironmentPropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.SystemPropertiesPropertySource;
import io.micronaut.context.env.yaml.YamlPropertySourceLoader;
import io.micronaut.core.annotation.Nullable;

import dev.fordes.adfs.error.ConfigurationException;

public final class CliConfiguration {

    private CliConfiguration() {
    }

    public static PropertySource[] load(@Nullable Path explicitFile) {
        List<PropertySource> sources = new ArrayList<>();
        String bundled = "classpath:/application.yml";
        try (InputStream input = CliConfiguration.class.getResourceAsStream("/application.yml")) {
            if (input == null) {
                throw new ConfigurationException("包内配置不存在: " + bundled);
            }
            sources.add(read(bundled, input, AbstractPropertySourceLoader.DEFAULT_POSITION));
        } catch (IOException exception) {
            throw new ConfigurationException("读取包内配置失败: " + bundled, exception);
        }
        Path selected = explicitFile;
        if (selected == null) {
            Path directory = ProgramLocation.directory();
            for (Path candidate : List.of(
                    directory.resolve("application.yml"),
                    directory.resolve("application.yaml"),
                    directory.resolve("config/application.yml"),
                    directory.resolve("config/application.yaml"))) {
                if (!Files.notExists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    selected = candidate;
                    break;
                }
            }
        }
        if (selected != null) {
            Path file = selected.toAbsolutePath().normalize();
            if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
                throw new ConfigurationException("配置文件不存在、不是普通文件或不可读: " + file);
            }
            try (InputStream input = Files.newInputStream(file)) {
                sources.add(read(file.toString(), input, AbstractPropertySourceLoader.DEFAULT_POSITION + 1));
            } catch (IOException exception) {
                throw new ConfigurationException("读取配置文件失败: " + file + ": " + exception.getMessage(), exception);
            }
        }
        sources.add(new EnvironmentPropertySource());
        sources.add(new SystemPropertiesPropertySource());
        return sources.toArray(PropertySource[]::new);
    }

    private static PropertySource read(String name, InputStream input, int order) {
        try {
            return PropertySource.of(name, new YamlPropertySourceLoader().read(name, input), order);
        } catch (IOException | RuntimeException exception) {
            throw new ConfigurationException("解析 YAML 配置失败: " + name + ": " + exception.getMessage(), exception);
        }
    }
}
