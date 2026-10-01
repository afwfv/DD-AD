package dev.fordes.adfs.validation.dns;

import java.util.Map;

import dev.fordes.adfs.rule.model.DomainName;

public interface DnsResolver {

    DnsResult resolve(DomainName domain);

    int cacheSize();

    /**
     * 查询失败的分类统计（原因 -> 次数），用于诊断失败到底是超时、SERVFAIL 还是网络不可达。
     * 默认返回空，测试替身无需实现。
     */
    default Map<String, Long> failureCauses() {
        return Map.of();
    }
}
