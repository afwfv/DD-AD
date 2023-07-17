package org.fordes.adg.rule;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adg.rule.config.OutputConfig;
import org.fordes.adg.rule.config.RuleConfig;
import org.fordes.adg.rule.enums.RuleType;
import org.fordes.adg.rule.handler.RuleHandler;
import org.fordes.adg.rule.util.RuleUtil;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.BufferedOutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@EnableAsync
@AllArgsConstructor
@SpringBootApplication
public class AdgRuleApplication {

    private final RuleConfig ruleConfig;

    private final OutputConfig outputConfig;


    public static void main(String[] args) {
        SpringApplication.run(AdgRuleApplication.class, args);
    }

    @Bean
    ApplicationRunner ruleRunner() {
        return args -> {
            if (ruleConfig.isEmpty() || outputConfig.isEmpty()) {
                log.warn("未配置规则或输出文件");
                return;
            }
            //建立文件
            Map<String, BufferedOutputStream> fileStream = outputConfig.getFiles().keySet().stream()
                    .map(fileName -> Map.entry(fileName,
                            RuleUtil.createFile(outputConfig.getPath(), fileName, outputConfig.getFileHeader())))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            Map<RuleType, Set<BufferedOutputStream>> typeStreamMap = new ConcurrentHashMap<>();
            outputConfig.getFiles().forEach((fileName, types) ->
                    types.forEach(type -> RuleUtil.safePut(typeStreamMap, type, fileStream.get(fileName))));

            //处理规则
            ruleConfig.getRuleMap().forEach((type, set) ->
                    set.forEach(rule -> RuleHandler.getHandler(type).handle(rule, typeStreamMap)));
        };
    }
}
