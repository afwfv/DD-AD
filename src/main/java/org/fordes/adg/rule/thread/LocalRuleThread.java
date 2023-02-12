package org.fordes.adg.rule.thread;

import cn.hutool.core.io.FileUtil;
import com.google.common.hash.BloomFilter;
import org.fordes.adg.rule.enums.RuleType;

import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * 本地规则处理
 *
 * @author ChengFengsheng on 2022/7/7
 */
public class LocalRuleThread extends AbstractRuleThread {


    public LocalRuleThread(String ruleUrl, Map<RuleType, Set<File>> typeFileMap, BloomFilter<String> filter) {
        super(ruleUrl, typeFileMap, filter);
    }

    @Override
    InputStream getContentStream() {
        return FileUtil.getInputStream(getRuleUrl());
    }
}
