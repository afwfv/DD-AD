package dev.fordes.adfs.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;

@Getter
@Setter
@ConfigurationProperties("adfs.config")
public final class AdfsProperties {

    private Path outputDir = Path.of("rule");
    private String fileHeader = FileHeaderTemplate.DEFAULT;

    public void setFileHeader(@Nullable String fileHeader) {
        this.fileHeader = fileHeader;
    }

}
