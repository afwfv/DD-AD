package dev.fordes.adfs.format.smartdns;

import dev.fordes.adfs.config.OutputSpec;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.error.OutputException;
import dev.fordes.adfs.format.*;
import dev.fordes.adfs.format.conversion.AdblockDomainConversion;
import dev.fordes.adfs.format.conversion.DedupMode;
import dev.fordes.adfs.format.conversion.ProjectedRule;
import dev.fordes.adfs.rule.conversion.ConversionDecision;
import dev.fordes.adfs.rule.conversion.ConversionLoss;
import dev.fordes.adfs.rule.conversion.ConversionPolicy;
import dev.fordes.adfs.rule.conversion.ConversionScope;
import dev.fordes.adfs.rule.dedup.OutputDeduplicator;
import dev.fordes.adfs.rule.model.*;
import dev.fordes.adfs.rule.spool.RuleSpool;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public final class SmartDnsWriter implements RuleWriter {

    private static final String LF = "\n";
    private final OutputSpec target;
    private final String outputName;
    private final List<DomainName> whitelist;
    private final OutputDeduplicator deduplicator;
    private final OutputStream output;
    private final OutputRuleProcessor<String> processor;
    private boolean finished;


    public SmartDnsWriter(
            OutputSpec target,
            ConversionPolicy policy,
            Set<String> whitelist,
            OutputDeduplicator deduplicator,
            OutputStream output) {
        this.target = target;
        this.outputName = target.path() + " (" + target.type().value() + ")";
        this.whitelist = whitelist.stream().map(DomainName::new).sorted().toList();
        this.deduplicator = deduplicator;
        this.output = output;
        this.processor = new OutputRuleProcessor<>(policy, deduplicator, this::writeProjected);
    }

    @Override
    public WriteResult write(RuleEntry entry) {
        if (finished) {
            throw new IllegalStateException("Writer finish 后不能继续写入");
        }
        Optional<Encoding> encoded = encode(entry);
        if (encoded.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("规则未转换:  {} --> {} | {} --> SmartDNS 无法表达该规则类型、域名模式或约束",
                        MDC.get(RuleSpool.INPUT), outputName, MDC.get(RuleSpool.INPUT_RULE));
            }
            return WriteResult.UNSUPPORTED;
        }
        Encoding value = encoded.orElseThrow();
        byte[] bytes = value.text().getBytes(StandardCharsets.UTF_8);
        return processor.process(new ProjectedRule<>(value.text(), bytes, value.effectiveRule(),
                value.decision(), value.passthrough(), DedupMode.ORDERED));
    }

    private Optional<Encoding> encode(RuleEntry entry) {
        return switch (entry) {
            case SafariRule safari -> encodeSafari(safari);
            case DomainRule(DomainPattern pattern, RuleAction action) -> encodeDomain(pattern, action);
            case HostMappingRule(var address, DomainName hostname) -> Optional.of(
                    new Encoding("address /-." + hostname.value() + "/" + address.text(), false,
                            exact("SmartDNS 地址映射精确转换"), new HostMappingRule(address, hostname)));
            case DnsAddressRule rule -> DnsAddressEncoding.encode(rule, target.type())
                    .map(text -> new Encoding(text, false, exact("保留原生 DNS 响应赋值"), rule));
            case AdblockNetworkRule rule -> encodeAdblockDomain(rule);
            case OpaqueRule opaque -> opaque.type() == RuleType.SMARTDNS && opaque.dialect() == RuleDialect.NONE
                    ? Optional.of(new Encoding(opaque.payload(), true, exact("同格式透传"), opaque)) : Optional.empty();
            case CosmeticRule _, IpCidrRule _, RouteRule _ -> Optional.empty();
        };
    }

    private Optional<Encoding> encodeSafari(SafariRule safari) {
        return encode(safari.rule()).map(encoded -> new Encoding(encoded.text(), encoded.passthrough(),
                encoded.decision().with(ConversionScope.EXPANDED, ConversionLoss.DROPPED_PLATFORM_CONSTRAINT,
                        "目标格式不能保留 Safari 内容拦截器范围"), encoded.effectiveRule()));
    }

    private Optional<Encoding> encodeAdblockDomain(AdblockNetworkRule rule) {
        if (!AdblockDomainConversion.supportsSubject(rule)) {
            return Optional.empty();
        }
        ConversionDecision decision = AdblockDomainConversion.decide(rule, false,
                new ConversionDecision(ConversionScope.EXACT, "SmartDNS 后缀域名规则精确转换"));
        return encodeDomain(new SuffixDomain(new DomainName(rule.pattern().value())), rule.action())
                .map(encoding -> new Encoding(encoding.text(), encoding.passthrough(), decision,
                        encoding.effectiveRule()));
    }

    private static Optional<Encoding> encodeDomain(DomainPattern pattern, RuleAction action) {
        String domain = switch (pattern) {
            case ExactDomain _ -> "-." + pattern.value();
            case SuffixDomain _ -> pattern.value();
            case Subdomain _ -> "*." + pattern.value();
            case WildcardDomain wildcard -> wildcard.syntax() == WildcardSyntax.SMARTDNS ? wildcard.value() : "";
            default -> "";
        };
        if (domain.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Encoding("address /" + domain + "/"
                + (action == RuleAction.BLOCK ? "#" : "-"), false,
                exact("SmartDNS 域名规则精确转换"), new DomainRule(pattern, action)));
    }

    @Override
    public FinishResult finish() {
        long added = 0;
        long duplicates = 0;
        if (finished) {
            return FinishResult.EMPTY;
        }
        for (DomainName domain : whitelist) {
            String rule = "address /" + domain.value() + "/-";
            deduplicator.recordOrdered();
            writeBytes(rule + LF);
            added++;
        }
        try {
            output.flush();
            finished = true;
        } catch (IOException exception) {
            throw new OutputException("刷新 SmartDNS 输出失败: " + target.path(), exception);
        }
        return new FinishResult(added, duplicates);
    }

    private void writeBytes(String value) {
        try {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new OutputException("写入 SmartDNS 输出失败: " + target.path(), exception);
        }
    }

    private void writeProjected(String text) {
        writeBytes(text + LF);
        log.trace("规则转换成功:  {} --> {} | {} --> {}",
                MDC.get(RuleSpool.INPUT), outputName, MDC.get(RuleSpool.INPUT_RULE), text);
    }

    private static ConversionDecision exact(String reason) {
        return new ConversionDecision(ConversionScope.EXACT, reason);
    }

    @Override
    public void close() {
        try (output) {
            finish();
        } catch (IOException exception) {
            throw new OutputException("关闭 SmartDNS 输出失败: " + target.path(), exception);
        }
    }

    private record Encoding(
            String text,
            boolean passthrough,
            ConversionDecision decision,
            RuleEntry effectiveRule) {
    }
}
