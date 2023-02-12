package org.fordes.adg.rule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "application.rule")
public class RuleConfig {

    /**
     * 远程规则，http或https
     */
    private List<String> remote;

    /**
     * 本地规则
     */
    private List<String> local;
}
