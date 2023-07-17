package org.fordes.adg.rule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 过滤器配置
 *
 * @author fordes123 on 2023/2/27
 */
@Data
@Component
@ConfigurationProperties(prefix = "application.config")
public class FilterConfig {

    private long expectedQuantity = 1000000;

    private double faultTolerance = 0.0001;

    private long warnLimit = 8;

    private long batchSize = 2000;

}

    
    