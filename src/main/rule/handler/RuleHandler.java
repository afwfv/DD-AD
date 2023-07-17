package org.fordes.adg.rule.handler;

import cn.hutool.bloomfilter.BitSetBloomFilter;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.LineHandler;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adg.rule.config.RuleConfig;
import org.fordes.adg.rule.util.SpringUtil;
import org.fordes.adg.rule.util.RuleUtil;
import org.fordes.adg.rule.config.FilterConfig;
import org.fordes.adg.rule.enums.HandleType;
import org.fordes.adg.rule.enums.RuleType;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 规则处理线程抽象
 *
 * @author ChengFengsheng on 2022/7/7
 */
@Slf4j
@AllArgsConstructor
public abstract class RuleHandler {

    private FilterConfig config;

    private BitSetBloomFilter bloomFilter;

    abstract InputStream getContentStream(String ruleUrl);

    abstract Charset getCharset();

    public void handle(RuleConfig.Prop prop, final Map<RuleType, Set<BufferedOutputStream>> typeStreamMap) {

        TimeInterval interval = DateUtil.timer();
        AtomicReference<Integer> invalid = new AtomicReference<>(0);
        AtomicReference<Integer> repeat = new AtomicReference<>(0);

        Map<RuleType, Set<String>> dataMap = MapUtil.newHashMap();
        //按行读取并处理
        try {
            BufferedReader bufferedReader = IoUtil.getReader(getContentStream(prop.getPath()), getCharset());
            IoUtil.readLines(bufferedReader, (LineHandler) line -> {
                String content = RuleUtil.clearRule(line);
                if (StrUtil.isEmpty(content)) {
                    invalid.getAndSet(invalid.get() + 1);
                } else if (bloomFilter.contains(content)) {
                    repeat.getAndSet(repeat.get() + 1);
                } else {
                    bloomFilter.add(content);
                    if (content.length() < config.getWarnLimit()) {
                        log.warn("发现短规则: {} -> {}，来源: {}", line, content, prop.getName());
                    }

                    Arrays.stream(RuleType.values()).filter(ruleType -> RuleUtil.validRule(content, ruleType))
                            .findFirst().ifPresent(ruleType -> RuleUtil.safePut(dataMap, ruleType, line));

                    if (dataMap.values().stream().map(Set::size).reduce(Integer::sum).orElse(0) > config.getBatchSize()) {
                        dataMap.forEach((type, data) -> typeStreamMap.getOrDefault(type, Collections.emptySet())
                                .forEach(e -> RuleUtil.writeToStream(e, data, prop, getCharset())));
                        dataMap.clear();
                    }
                }
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.stacktraceToString(e));
        } finally {
            dataMap.forEach((type, data) -> typeStreamMap.getOrDefault(type, Collections.emptySet())
                    .forEach(e -> RuleUtil.writeToStream(e, data, prop, getCharset())));
            dataMap.clear();
            log.info("规则<{}> 耗时 => {} ms 无效数 => {} 重复数 => {}",
                    prop.getName(), interval.intervalMs(), invalid.get(), repeat.get());
        }
    }

    public static RuleHandler getHandler(HandleType handleType) {
        return switch (handleType) {
            case LOCAL -> SpringUtil.getBean(LocalRuleHandler.class);
            case REMOTE -> SpringUtil.getBean(RemoteRuleHandler.class);
        };
    }
}
