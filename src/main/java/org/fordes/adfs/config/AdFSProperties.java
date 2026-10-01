package org.fordes.adfs.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.fordes.adfs.enums.RuleSet;
import org.fordes.adfs.model.Rule;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

import java.util.*;

/**
 *
 * @author fordes on 2025/10/29
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "application")
public class AdFSProperties implements InitializingBean {

    @NotNull
    private Config config;

    @NotNull
    @NotEmpty
    private Set<InputProperties> input;

    @NotNull
    private OutputProperties output;

    /**
     * @see #input
     */
    @Deprecated
    private Map<String, Set<InputProperties>> rule;

    @Override
    public void afterPropertiesSet() {
        if (rule != null && !rule.isEmpty()) {
            rule.forEach((k, v) -> this.input.addAll(v));
        }
    }

    public record OutputProperties(

            @DefaultValue("")
            String fileHeader,

            @DefaultValue("rule")
            String path,

            @NotEmpty
            Set<@Valid @NotNull OutputItem> files
    ) {
    }

    public record InputProperties(
            @NotBlank
            String name,

            @DefaultValue("EASYLIST")
            RuleSet type,

            @NotBlank
            String path
    ) {

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof InputProperties prop) {
                return prop.path.equals(this.path) || prop.name.equals(this.name);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }
    }

    public record OutputItem(
            @NotBlank
            String name,

            @NotNull
            RuleSet type,

            @DefaultValue("")
            String desc,

            @DefaultValue("")
            String fileHeader,

            @NotEmpty
            @DefaultValue({})
            Set<Rule.Type> filter,

            @NotEmpty
            @DefaultValue({})
            Set<@NotBlank String> rule

    ) {

    }

    public record Config(
            @Positive @DefaultValue("0.0001")
            Double faultTolerance,

            @Positive @DefaultValue("2000000")
            Long expectedQuantity,

            @Min(1) @DefaultValue("6")
            Integer warnLimit,

            @DefaultValue()
            Set<String> exclude,

            @DefaultValue
            DomainDetect domainDetect,

            @DefaultValue
            Tracking tracking
    ) {

    }

    public record DomainDetect(
            @DefaultValue("false")
            Boolean enable,

            @Positive @DefaultValue("1000")
            Integer timeout,

            @Positive @DefaultValue("600")
            Integer cacheTtlMin,

            @Positive @DefaultValue("86400")
            Integer cacheTtlMax,

            @Positive @DefaultValue("300")
            Integer cacheNegativeTtl,

            @Positive @DefaultValue("128")
            Integer concurrency,

            @DefaultValue
            List<String> provider) {

    }

    public record Tracking(@DefaultValue("false") Boolean enable,
                           @DefaultValue("logs/tracking.list") String path) {
    }
}