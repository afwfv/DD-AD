package org.fordes.adg.rule.config;

import lombok.Data;
import org.fordes.adg.rule.enums.RuleType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 输出配置
 *
 * @author fordes123 on 2022/9/19
 */
@Data
@Component
@ConfigurationProperties(prefix = "application.output")
public class OutputConfig {

    /**
     * 输出文件路径
     */
    private String path;

    /**
     * 输出文件列表
     */
    private Map<String, Set<RuleType>> files;
}
