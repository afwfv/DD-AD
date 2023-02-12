package org.fordes.adg.rule.thread;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.LineHandler;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.hash.BloomFilter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adg.rule.Util;
import org.fordes.adg.rule.enums.RuleType;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
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
@Data
public abstract class AbstractRuleThread implements Runnable {

    private final String ruleUrl;

    private final Map<RuleType, Set<File>> typeFileMap;

    private final BloomFilter<String> filter;

    public AbstractRuleThread(String ruleUrl, Map<RuleType, Set<File>> typeFileMap, BloomFilter<String> filter) {
        this.ruleUrl = ruleUrl;
        this.typeFileMap = typeFileMap;
        this.filter = filter;
    }

    private Charset charset = Charset.defaultCharset();

    abstract InputStream getContentStream();

    @Override
    public void run() {
        TimeInterval interval = DateUtil.timer();
        AtomicReference<Integer> invalid = new AtomicReference<>(0);
        Map<File, Set<String>> fileDataMap = MapUtil.newHashMap();
        try {
            //按行读取并处理
            IoUtil.readLines(getContentStream(), charset, (LineHandler) line -> {
                if (StrUtil.isNotBlank(line)) {
                    String content = Util.clearRule(line);
                    if (StrUtil.isNotBlank(content)) {
                        if (!filter.mightContain(line)) {
                            filter.put(line);

                            if (Util.validRule(content, RuleType.DOMAIN)) {
                                typeFileMap.getOrDefault(RuleType.DOMAIN, Collections.emptySet())
                                        .forEach(item -> Util.safePut(fileDataMap, item, line));
                                log.debug("域名规则: {}", line);

                            } else if (Util.validRule(content, RuleType.HOSTS)) {
                                typeFileMap.getOrDefault(RuleType.HOSTS, Collections.emptySet())
                                        .forEach(item -> Util.safePut(fileDataMap, item, line));
                                log.debug("Hosts规则: {}", line);

                            } else if (Util.validRule(content, RuleType.MODIFY)) {

                                if (Util.validRule(content, RuleType.REGEX)) {
                                    typeFileMap.getOrDefault(RuleType.REGEX, Collections.emptySet())
                                            .forEach(item -> Util.safePut(fileDataMap, item, line));
                                    log.debug("正则规则: {}", line);

                                } else {

                                    typeFileMap.getOrDefault(RuleType.MODIFY, Collections.emptySet())
                                            .forEach(item -> Util.safePut(fileDataMap, item, line));
                                    log.debug("修饰规则: {}", line);
                                }
                            } else {
                                invalid.getAndSet(invalid.get() + 1);
                                log.debug("无效规则: {}", line);
                            }
                        }
                    } else {
                        invalid.getAndSet(invalid.get() + 1);
                        log.debug("不是规则: {}", line);
                    }
                }
            });
        } catch (Exception e) {
            log.error(ExceptionUtil.stacktraceToString(e));
        } finally {
            fileDataMap.forEach((k, v) -> Util.writeToFile(k, v, ruleUrl));
            log.info("规则<{}> 耗时 => {} ms 无效数 => {}",
                    ruleUrl, interval.intervalMs(), invalid.get());
        }
    }
}
