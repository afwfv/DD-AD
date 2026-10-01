package dev.fordes.adfs.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("adfs.config.conversion")
public final class ConversionProperties {

    private boolean allowExpansion;
    /**
     * 示例配置（{@code config/application-example.yaml}）声明的默认值为 {@code true}，
     * 此前留空即 false 会让所有需要 REDUCED 目标的转换被静默丢弃 —— 例如 hosts 输出整表为空。
     */
    private boolean allowReduction = true;

}
