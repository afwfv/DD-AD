package org.fordes.adg.rule.thread;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.google.common.hash.BloomFilter;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adg.rule.enums.RuleType;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

@Slf4j
public class RemoteRuleThread extends AbstractRuleThread {


    public RemoteRuleThread(String ruleUrl, Map<RuleType, Set<File>> typeFileMap, BloomFilter<String> filter) {
        super(ruleUrl, typeFileMap, filter);
    }

    @Override
    InputStream getContentStream() {
        try {
            HttpResponse response = HttpRequest.get(getRuleUrl())
                    .setFollowRedirects(true)
                    .timeout(20000)
                    .execute();
            if (response.isOk()) {
                setCharset(Charset.forName(response.charset()));
                return response.bodyStream();
            }
        }catch (Exception e) {
            log.error(" 规则<{}> 获取失败 => {}", getRuleUrl(), e.getMessage());
            log.debug(ExceptionUtil.stacktraceToString(e));
        }
        return IoUtil.toStream(new byte[0]);
    }

}
