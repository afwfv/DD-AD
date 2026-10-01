package dev.fordes.adfs.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("adfs.config.conversion")
public final class ConversionProperties {

    private boolean allowExpansion;
    private boolean allowReduction;

}
