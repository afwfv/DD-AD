package org.fordes.adg.rule.handler;

import cn.hutool.bloomfilter.BloomFilter;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adg.rule.Util;
import org.fordes.adg.rule.enums.RuleType;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 规则处理线程抽象
 *
 * @author ChengFengsheng on 2022/7/7
 */
@Slf4j
public abstract class AbstractRuleHandler {

    protected Charset charset = Charset.defaultCharset();

    private static final int BATCH_SIZE = 20000;

    abstract InputStream getContentStream(String ruleUrl);

    public void handle(String ruleUrl, BloomFilter filter, Map<RuleType, Set<BufferedOutputStream>> typeFileMap) {

        TimeInterval interval = DateUtil.timer();
        AtomicReference<Integer> invalid = new AtomicReference<>(0);
        Map<BufferedOutputStream, Set<String>> fileDataMap = MapUtil.newHashMap();
        //按行读取并处理
        int i = 0;
        try {
            BufferedReader bufferedReader = IoUtil.getReader(getContentStream(ruleUrl), Charset.defaultCharset());
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String content = Util.clearRule(line);
                if (StrUtil.isEmpty(content) || filter.contains(content)) {
                    invalid.getAndSet(invalid.get() + 1);
                    continue;
                }
                filter.add(content);

                for (RuleType ruleType : RuleType.values()) {
                    if (Util.validRule(content, ruleType)) {
                        Set<BufferedOutputStream> streams = typeFileMap.get(ruleType);
                        if (streams != null) {
                            for (BufferedOutputStream stream : streams) {
                                Util.safePut(fileDataMap, stream, line);
                            }
                        }
                        break;
                    }
                }

                if (i == BATCH_SIZE) {
                    fileDataMap.forEach((k, v) -> Util.writeToStream(k, v, ruleUrl));
                    fileDataMap.clear();
                    i = 0;
                } else {
                    i++;
                }
            }


        } catch (Exception e) {
            log.error(ExceptionUtil.stacktraceToString(e));
        } finally {
            fileDataMap.forEach((k, v) -> Util.writeToStream(k, v, ruleUrl));
            log.info("规则<{}> 耗时 => {} ms 无效数 => {}",
                    ruleUrl, interval.intervalMs(), invalid.get());
        }
    }
}
