package dev.fordes.adfs.report;

import dev.fordes.adfs.application.ProcessingResult;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Map;

@Slf4j
@Singleton
public final class RunReporter {


    public void report(ProcessingResult result) {
        long inputLines = 0;
        long invalidRules = 0;
        for (InputMetrics metrics : result.sources().values()) {
            inputLines = Math.addExact(inputLines, metrics.lines());
            invalidRules = Math.addExact(invalidRules, metrics.invalidRules());
        }

        long parsedRules = Math.addExact(result.semanticRules(), result.opaqueRules());
        log.info("处理汇总: 读取 {} 个来源、{} 行, 解析 {} 条规则; 保留 {} 条唯一规则, 移除 {} 条重复规则, 跳过 {} 条非法规则",
                formatCount(result.inputs()), formatCount(inputLines), formatCount(parsedRules),
                formatCount(result.uniqueRules()), formatCount(result.duplicateRules()), formatCount(invalidRules));

        if (hasDnsMetrics(result.dns())) {
            DnsMetrics dns = result.dns();
            log.info("DNS 检测: 检查 {} 条规则; 有效 {} 条, 无效 {} 条, 失败 {} 条, 跳过 {} 条, 合并 {} 条",
                    formatCount(dns.checked()), formatCount(dns.valid()), formatCount(dns.invalid()),
                    formatCount(dns.failed()), formatCount(dns.skipped()), formatCount(dns.merged()));
        }

        result.outputs().forEach((path, metrics) -> {
            long emittedRules = Math.addExact(metrics.written(), metrics.passthrough());
            long totalRules = Math.addExact(emittedRules, metrics.whitelistAdded());
            log.info("输出「{}」: 生成 {} 条规则, 覆盖率 {}; 转换 {} 条, 透传 {} 条, 未支持 {} 条",
                    path, formatCount(totalRules), percentage(emittedRules, result.uniqueRules()),
                    formatCount(metrics.written()), formatCount(metrics.passthrough()),
                    formatCount(metrics.unsupported()));
        });

        Map<ProcessingStage, Long> stages = result.stages();
        log.info("耗时汇总: 输入解析 {}, 规则处理 {}, 产物校验 {}, 发布 {}, 总计 {}",
                formatDuration(stages.getOrDefault(ProcessingStage.INPUT, 0L)),
                formatDuration(stages.getOrDefault(ProcessingStage.PROCESSING, 0L)),
                formatDuration(stages.getOrDefault(ProcessingStage.MANIFEST, 0L)),
                formatDuration(stages.getOrDefault(ProcessingStage.PUBLISH, 0L)),
                formatDuration(result.elapsedMillis()));
    }

    private static boolean hasDnsMetrics(DnsMetrics metrics) {
        return metrics.checked() != 0 || metrics.valid() != 0 || metrics.invalid() != 0 || metrics.failed() != 0
                || metrics.skipped() != 0 || metrics.merged() != 0;
    }

    private static String percentage(long numerator, long denominator) {
        double value = denominator == 0 ? 0.0 : numerator * 100.0 / denominator;
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private static String formatCount(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatDuration(long millis) {
        if (millis < 1000) {
            return formatCount(millis) + " ms";
        }
        return String.format(Locale.ROOT, "%.2f s", millis / 1000.0);
    }
}
