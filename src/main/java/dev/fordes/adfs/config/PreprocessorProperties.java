package dev.fordes.adfs.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.validation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties("adfs.config.rules.preprocessor")
public final class PreprocessorProperties {

    @Min(1)
    @Max(128)
    private int maxDepth = 32;

    @Min(0)
    @Max(32)
    private int maxIncludeDepth = 8;

    @NotNull
    private List<String> trueTokens = List.of();

}
