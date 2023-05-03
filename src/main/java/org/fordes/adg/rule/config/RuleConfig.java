package org.fordes.adg.rule.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import lombok.Getter;
import org.fordes.adg.rule.constant.Constants;
import org.fordes.adg.rule.enums.HandleType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "application.rule")
public class RuleConfig {

    /**
     * 远程规则，http或https
     */
    private Set<String> remote;

    /**
     * 本地规则
     */
    private Set<String> local;

    @Getter
    private final Map<HandleType, Set<String>> ruleMap = new HashMap<>();

    public void setLocal(Set<String> local) {
        Set<String> collect = local.stream()
                .filter(StrUtil::isNotBlank)
                .map(e -> {
                    e = FileUtil.normalize(e);
                    return FileUtil.isAbsolutePath(e) ?
                            e : FileUtil.normalize(Constants.LOCAL_RULE_SUFFIX + File.separator + e);
                }).collect(Collectors.toSet());
        ruleMap.put(HandleType.LOCAL, collect);
    }

    public void setRemote(Set<String> remote) {
        Set<String> collect = remote.stream()
                .filter(StrUtil::isNotBlank)
                .map(URLUtil::normalize).collect(Collectors.toSet());
        ruleMap.put(HandleType.REMOTE, collect);
    }

    public boolean isEmpty() {
        return ruleMap.isEmpty() || ruleMap.values().stream().allMatch(Set::isEmpty);
    }
}
