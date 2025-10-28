package org.fordes.adfs.config;

import lombok.Data;
import org.fordes.adfs.constant.Constants;
import org.fordes.adfs.enums.RuleSet;
import org.fordes.adfs.handler.rule.Handler;
import org.fordes.adfs.model.Rule;
import org.fordes.adfs.util.Util;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;

import static org.fordes.adfs.constant.Constants.*;

/**
 * 输出配置
 *
 * @author fordes123 on 2022/9/19
 */
@Data
@Component
@ConfigurationProperties(prefix = "application.output")
public class OutputProperties implements InitializingBean {

    private String fileHeader;
    private String path = "rule";
    private Set<OutputFile> files;

    public record OutputFile(String name, RuleSet type, Set<Rule.Type> filter, String desc, String fileHeader) {

        public OutputFile(String name, RuleSet type, Set<Rule.Type> filter, String desc, String fileHeader) {
            this.name = Optional.ofNullable(name).filter(StringUtils::hasText).orElseThrow(() -> new IllegalArgumentException("application.output.files.name is required"));
            this.type = Optional.ofNullable(type).orElseThrow(() -> new IllegalArgumentException("application.output.files.type is required"));
            this.desc = Optional.ofNullable(desc).filter(StringUtils::hasText).orElse(Constants.EMPTY);
            this.filter = Optional.ofNullable(filter).orElse(Set.of(Rule.Type.values()));
            this.fileHeader = Optional.ofNullable(fileHeader).filter(StringUtils::hasText).orElse(null);
        }

        public String displayHeader(Handler handler, String parentHeader) {
            StringBuilder builder = new StringBuilder();
            //文件头
            Optional.ofNullable(Optional.ofNullable(this.fileHeader()).orElse(parentHeader))
                    .filter(StringUtils::hasText)
                    .ifPresent(e -> {
                        String header = handler.commented(e
                                .replace(HEADER_DATE, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                                .replace(HEADER_NAME, this.name())
                                .replace(HEADER_DESC, this.desc())
                                .replace(HEADER_TYPE, this.type().name().toLowerCase()));
                        builder.append(header).append(System.lineSeparator());
                    });

            //格式头
            Optional.ofNullable(handler.headFormat()).filter(StringUtils::hasText)
                    .ifPresent(e -> builder.append(e).append(System.lineSeparator()));

            return builder.toString();
        }
    }

    @Override
    public void afterPropertiesSet() {
        this.files = Optional.ofNullable(files)
                .filter(e -> !e.isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("application.output.files is required"));

        this.path = Optional.ofNullable(path).filter(StringUtils::hasText)
                .map(Util::normalizePath)
                .orElseThrow(() -> new IllegalArgumentException("application.output.path is required"));
    }

    public boolean isEmpty() {
        return files == null || files.isEmpty();
    }
}
