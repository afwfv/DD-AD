package org.fordes.adg.rule.handler;

import cn.hutool.bloomfilter.BitSetBloomFilter;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
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

@Slf4j
@Component
@EnableAsync
public class RemoteRuleHandler extends RuleHandler {

    private Charset charset = Charset.defaultCharset();

    @Autowired
    public RemoteRuleHandler(FilterConfig config, BitSetBloomFilter filter) {
        super(config, filter);
    }

    @Override
    InputStream getContentStream(String ruleUrl) {
        try {
            HttpResponse response = HttpRequest.get(ruleUrl)
                    .setFollowRedirects(true)
                    .timeout(20000)
                    .execute();
            if (response.isOk()) {
                this.charset = Charset.forName(response.charset());
                return response.bodyStream();
            } else throw new RuntimeException("response status: " + response.getStatus());
        } catch (Exception e) {
            log.error(" 规则<{}> 获取失败 => {}", ruleUrl, e.getMessage());
            log.debug(ExceptionUtil.stacktraceToString(e));
        }
        return IoUtil.toStream(new byte[0]);
    }

    @Override
    Charset getCharset() {
        return charset;
    }

    @Override
    @Async("ruleExecutor")
    public void handle(RuleConfig.Prop prop, Map<RuleType, Set<BufferedOutputStream>> typeStreamMap) {
        super.handle(prop, typeStreamMap);
    }
}
