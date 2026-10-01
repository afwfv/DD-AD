package dev.fordes.adfs.config;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.validation.Validated;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@EachProperty(value = "adfs.input", list = true)
public final class InputProperties {

    private final int index;

    @NotBlank
    private String name;

    @NotBlank
    private String path;

    @NotBlank
    private String type;

    private String dialect;

    public InputProperties(@Parameter int index) {
        this.index = index;
    }

}
