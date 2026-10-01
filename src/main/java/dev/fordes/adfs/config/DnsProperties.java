package dev.fordes.adfs.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.validation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties("adfs.config.dns")
public final class DnsProperties {

    private boolean enabled;

    private boolean strictMode;

    @NotNull
    private List<String> servers = List.of();

    @Min(1)
    @Max(1_024)
    private int concurrency = 64;

    @NotNull
    private Duration timeout = Duration.ofSeconds(3);

    @Min(0)
    @Max(3)
    private int retries = 1;

    @Min(1)
    @Max(64)
    private int maxCnameDepth = 16;

}
