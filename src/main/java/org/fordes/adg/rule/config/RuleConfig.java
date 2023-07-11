package org.fordes.adg.rule.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.fordes.adg.rule.constant.Constants;
import org.fordes.adg.rule.enums.HandleType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "application.rule")
public class RuleConfig {

    /**
     * 远程规则，http或https
     */
    private Collection<Prop> remote;

    /**
     * 本地规则
     */
    private Collection<Prop> local;

    @Getter
    private final Map<HandleType, List<Prop>> ruleMap = new HashMap<>();

    public void setLocal(List<Prop> local) {
        List<Prop> temp = local.stream().filter(e -> StrUtil.isNotBlank(e.path)).peek(e -> {
            String url = FileUtil.normalize(e.path);
            e.path = FileUtil.isAbsolutePath(url) ?
                    url : FileUtil.normalize(Constants.LOCAL_RULE_SUFFIX + File.separator + url);
            e.name = StrUtil.isBlank(e.name) ? e.path : e.name;
        }).distinct().toList();
        ruleMap.put(HandleType.LOCAL, temp);
    }


    public void setRemote(List<Prop> remote) {
        List<Prop> temp = remote.stream().filter(e -> StrUtil.isNotBlank(e.path)).peek(e -> {
            e.path = URLUtil.normalize(e.path);
            e.name = StrUtil.isBlank(e.name) ? e.path : e.name;
        }).distinct().toList();
        ruleMap.put(HandleType.REMOTE, temp);
    }

    public boolean isEmpty() {
        return ruleMap.isEmpty() || ruleMap.values().stream().allMatch(Collection::isEmpty);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Prop {

        private String name;
        private String path;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Prop prop) {
                return prop.path.equals(this.path);
            }
            return false;
        }

        public int hashCode() {
            return path.hashCode();
        }

        public Prop(String path) {
            this.path = path;
//            this.name = url;
        }
    }
}
