package dev.fordes.adfs.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.validation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties("adfs.config.input")
public final class InputLimitProperties {

    @Min(1_048_576)
    @Max(1_073_741_824)
    private long maxSize = 67_108_864L;

    @Min(1_024)
    @Max(1_048_576)
    private int maxLineLength = 262_144;

}
