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
@ConfigurationProperties("adfs.config.rules")
public final class RuleProperties {

    @Min(1)
    @Max(1_048_576)
    private int minLength = 1;

    @Min(1)
    @Max(1_048_576)
    private int maxLength = 65_536;

    @NotNull
    private List<String> whitelist = List.of();

}
