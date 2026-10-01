package dev.fordes.adfs.config;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.Validated;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@EachProperty(value = "adfs.output", list = true)
public final class OutputProperties {

    private final int index;

    @NotBlank
    private String name;

    @NotBlank
    private String type;

    private String dialect;
    private String container;
    private String fileHeader;

    public OutputProperties(@Parameter int index) {
        this.index = index;
    }

    public void setFileHeader(@Nullable String fileHeader) {
        this.fileHeader = fileHeader;
    }

}
