package org.fordes.adg.rule.handler;

import cn.hutool.bloomfilter.BitSetBloomFilter;
import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adg.rule.config.FilterConfig;
import org.fordes.adg.rule.config.RuleConfig;
import org.fordes.adg.rule.enums.RuleType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
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
public class LocalRuleHandler extends RuleHandler {

    @Autowired
    public LocalRuleHandler(FilterConfig config, BitSetBloomFilter filter) {
        super(config, filter);
    }

    @Override
    InputStream getContentStream(String ruleUrl) {
        return FileUtil.getInputStream(ruleUrl);
    }

    @Override
    Charset getCharset() {
        return Charset.defaultCharset();
    }

    @Async("ruleExecutor")
    @Override
    public void handle(RuleConfig.Prop prop, Map<RuleType, Set<BufferedOutputStream>> typeStreamMap) {
        super.handle(prop, typeStreamMap);
    }
}
