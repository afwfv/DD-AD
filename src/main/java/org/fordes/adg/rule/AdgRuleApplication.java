package org.fordes.adg.rule;

import cn.hutool.bloomfilter.BitSetBloomFilter;
import cn.hutool.bloomfilter.BloomFilterUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adg.rule.config.FilterConfig;
import org.fordes.adg.rule.config.OutputConfig;
import org.fordes.adg.rule.config.RuleConfig;
import org.fordes.adg.rule.enums.RuleType;
import org.fordes.adg.rule.handler.AbstractRuleHandler;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.BufferedOutputStream;
import java.io.File;
import java.util.Map;
import java.util.Set;

@Slf4j
@EnableAsync
@AllArgsConstructor
@SpringBootApplication
public class AdgRuleApplication {

    private final RuleConfig ruleConfig;

    private final OutputConfig outputConfig;

    private final FilterConfig filterConfig;

    private final ThreadPoolTaskExecutor ruleExecutor;

    private final AbstractRuleHandler remoteRuleHandler;

    private final AbstractRuleHandler localRuleHandler;

    private final ApplicationContext applicationContext;

    public static void main(String[] args) {
        SpringApplication.run(AdgRuleApplication.class, args);
    }

    @Bean
    ApplicationRunner ruleRunner() {
        return args -> {
            TimeInterval interval = DateUtil.timer();
            Map<RuleType, Set<BufferedOutputStream>> typeFileMap = MapUtil.newHashMap();

            // 初始化，根据配置建立文件
            if (outputConfig.getFiles().isEmpty()) {
                log.info("未配置输出文件，程序退出");
                return;
            }
            outputConfig.getFiles().forEach((fileName, types) -> {
                File file = Util.createFile(outputConfig.getPath() + File.separator + fileName);
                types.forEach(type -> Util.safePut(typeFileMap, type, FileUtil.getOutputStream(file)));
            });

            //使用布隆过滤器实现去重
            long numOfBits = Util.optimalNumOfBits(filterConfig.getExpectedQuantity(), filterConfig.getFaultTolerance());
            int numOfHashFunctions = Util.optimalNumOfHashFunctions(filterConfig.getExpectedQuantity(), numOfBits);
            BitSetBloomFilter filter = BloomFilterUtil.createBitSet((int) numOfBits, (int) numOfBits, numOfHashFunctions);

            //远程规则
            ruleConfig.getRemote().stream()
                    .filter(StrUtil::isNotBlank)
                    .map(URLUtil::normalize)
                    .forEach(e -> remoteRuleHandler.handle(e, filter, typeFileMap));

            //本地规则
            ruleConfig.getLocal().stream()
                    .filter(StrUtil::isNotBlank)
                    .map(e -> {
                        e = FileUtil.normalize(e);
                        return FileUtil.isAbsolutePath(e) ?
                                e : FileUtil.normalize(Constant.LOCAL_RULE_SUFFIX + File.separator + e);
                    })
                    .forEach(e -> localRuleHandler.handle(e, filter, typeFileMap));

            do {
                ThreadUtil.safeSleep(1000);
            } while (ruleExecutor.getActiveCount() > 0);

            //关闭文件流
            typeFileMap.values().forEach(e -> e.forEach(Util::safeClose));
            log.info("规则数量: {}, 总耗时：{} s", Util.count, interval.intervalSecond());
            SpringApplication.exit(applicationContext);
        };
    }
}
