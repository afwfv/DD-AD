package org.fordes.adg.rule.handler;

import cn.hutool.bloomfilter.BloomFilter;
import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adg.rule.enums.RuleType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * 本地规则处理
 *
 * @author ChengFengsheng on 2022/7/7
 */
@Slf4j
@EnableAsync
@Component
public class LocalRuleHandler extends AbstractRuleHandler {


    @Override
    InputStream getContentStream(String ruleUrl) {
        return FileUtil.getInputStream(ruleUrl);
    }

    @Override
    @Async("ruleExecutor")
    public void handle(String ruleUrl, BloomFilter filter, Map<RuleType, Set<BufferedOutputStream>> typeFileMap) {
        super.handle(ruleUrl, filter, typeFileMap);
    }
}
