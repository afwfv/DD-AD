package dev.fordes.adfs.format.adblock;

import dev.fordes.adfs.config.OutputSpec;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.error.OutputException;
import dev.fordes.adfs.format.FinishResult;
import dev.fordes.adfs.format.OutputRuleProcessor;
import dev.fordes.adfs.format.RuleWriter;
import dev.fordes.adfs.format.WriteResult;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public final class AdblockWriter implements RuleWriter {

    private static final String LF = "\n";
    private final OutputSpec target;
    private final String outputName;
    private final List<DomainName> whitelist;
    private final OutputDeduplicator deduplicator;
    private final OutputStream output;
    private final AdblockDialectDefinition dialect;
    private final OutputRuleProcessor<String> processor;
    private boolean finished;
    private boolean hasSafariAffinity;


    public AdblockWriter(
            OutputSpec target,
            ConversionPolicy policy,
            Set<String> whitelist,
            OutputDeduplicator deduplicator,
            OutputStream output) {
        this.target = target;
        this.outputName = target.path() + " (" + target.type().value()
                + (target.dialect() == RuleDialect.NONE ? "" : "/" + target.dialect().value()) + ")";
        this.whitelist = whitelist.stream().map(DomainName::new).sorted().toList();
        this.deduplicator = deduplicator;
        this.output = output;
        this.dialect = AdblockDialectDefinition.forDialect(target.dialect());
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
                String reason = entry instanceof DomainRule(ExactDomain _, var _)
                        ? "Adblock 域名规则会覆盖子域名, 当前策略禁止扩大匹配范围"
                        : "目标方言不能表达该规则类型或选项";
                log.debug("规则未转换:  {} --> {} | {} --> {}",
                        MDC.get(RuleSpool.INPUT), outputName, MDC.get(RuleSpool.INPUT_RULE), reason);
            }
            return WriteResult.UNSUPPORTED;
        }
        Encoding value = encoded.orElseThrow();
        byte[] bytes = value.text().getBytes(StandardCharsets.UTF_8);
        WriteResult result = processor.process(new ProjectedRule<>(value.text(), bytes, value.effectiveRule(),
                value.decision(), value.passthrough(), DedupMode.SET_LIKE));
        if ((result == WriteResult.WRITTEN || result == WriteResult.PASSTHROUGH)
                && entry instanceof SafariRule && target.dialect() == RuleDialect.ADGUARD) {
            hasSafariAffinity = true;
        }
        return result;
    }

    private Optional<Encoding> encode(RuleEntry entry) {
        return switch (entry) {
            case SafariRule safari -> encodeSafari(safari);
            case DomainRule(DomainPattern pattern, RuleAction action) -> encodeDomain(pattern, action);
            case AdblockNetworkRule rule -> encodeNetwork(rule);
            case CosmeticRule rule -> AdblockDialectConverter.convert(rule, target.dialect())
                    .map(projected -> new Encoding(projected.text(), false, rule, exact(projected.reason())));
            case OpaqueRule opaque -> opaque.type() == RuleType.ADBLOCK
                    ? AdblockDialectConverter.convert(opaque, target.dialect())
                            .map(projected -> new Encoding(
                                    projected.text(), true, opaque, exact(projected.reason())))
                    : Optional.empty();
            case DnsAddressRule _, HostMappingRule _, IpCidrRule _, RouteRule _ -> Optional.empty();
        };
    }

    private Optional<Encoding> encodeSafari(SafariRule safari) {
        return encode(safari.rule()).map(encoded -> {
            if (target.dialect() == RuleDialect.ADGUARD) {
                return new Encoding("!#safari_cb_affinity(" + safari.affinity() + ")" + LF + encoded.text()
                        + LF + "!#safari_cb_affinity", encoded.passthrough(), safari, encoded.decision());
            }
            return new Encoding(encoded.text(), encoded.passthrough(), encoded.effectiveRule(),
                    encoded.decision().with(ConversionScope.EXPANDED,
                            ConversionLoss.DROPPED_PLATFORM_CONSTRAINT,
                            "目标方言不能保留 Safari 内容拦截器范围"));
        });
    }

    private Optional<Encoding> encodeDomain(DomainPattern pattern, RuleAction action) {
        if (!(pattern instanceof ExactDomain || pattern instanceof SuffixDomain)) {
            return Optional.empty();
        }
        ConversionScope scope = pattern instanceof ExactDomain ? ConversionScope.EXPANDED : ConversionScope.EXACT;
        DomainRule effective = new DomainRule(new SuffixDomain(new DomainName(pattern.value())), action);
        return Optional.of(new Encoding((action == RuleAction.ALLOW ? "@@" : "")
                + "||" + pattern.value() + "^", false, effective,
                new ConversionDecision(scope,
                        scope == ConversionScope.EXPANDED
                                ? Set.of(ConversionLoss.ROOT_EXPANDED_TO_SUBDOMAINS) : Set.of(),
                        "Adblock 域名规则覆盖根域名及子域")));
    }

    private Optional<Encoding> encodeNetwork(AdblockNetworkRule rule) {
        if (rule.isBadfilter()) {
            return Optional.empty();
        }
        for (var resource : rule.includedResourceTypes()) {
            if (dialect.option(resource.value()) != AdblockCapability.SEMANTIC) {
                return Optional.empty();
            }
        }
        for (var resource : rule.excludedResourceTypes()) {
            if (dialect.option(resource.value()) != AdblockCapability.SEMANTIC) {
                return Optional.empty();
            }
        }
        if (rule.action() == RuleAction.ALLOW
                && rule.includedResourceTypes().stream().anyMatch(resource -> resource.value().equals("document"))
                && (rule.dialect() == RuleDialect.UBO) != (target.dialect() == RuleDialect.UBO)) {
            return Optional.empty();
        }
        if (rule.dialect() != target.dialect()
                && (rule.includedResourceTypes().stream().anyMatch(resource -> resource.value().equals("popup"))
                        || rule.excludedResourceTypes().stream()
                                .anyMatch(resource -> resource.value().equals("popup")))) {
            return Optional.empty();
        }
        if (rule.important() && dialect.option("important") != AdblockCapability.SEMANTIC) {
            return Optional.empty();
        }
        for (AdblockModifier modifier : rule.modifiers()) {
            if (dialect.option(modifier.type().value()) != AdblockCapability.SEMANTIC) {
                return Optional.empty();
            }
        }
        StringBuilder text = new StringBuilder();
        if (rule.action() == RuleAction.ALLOW) {
            text.append("@@");
        }
        text.append(switch (rule.pattern().kind()) {
            case DOMAIN_ANCHOR -> "||" + rule.pattern().value() + "^";
            case URL -> target.dialect() == RuleDialect.UBO && rule.dialect() != RuleDialect.UBO
                    && DomainName.tryParse(rule.pattern().value()).isPresent()
                            ? rule.pattern().value() + "*" : rule.pattern().value();
            case REGEX -> "/" + rule.pattern().value() + "/";
        });
        List<String> options = new ArrayList<>();
        rule.includedResourceTypes().stream().sorted().forEach(type -> options.add(type.value()));
        rule.excludedResourceTypes().stream().sorted().forEach(type -> options.add("~" + type.value()));
        if (!rule.domainConstraints().isEmpty()) {
            options.add("domain=" + String.join("|", rule.domainConstraints().stream()
                    .map(domain -> (domain.excluded() ? "~" : "") + domain.domain().value()).toList()));
        }
        switch (rule.partyConstraint()) {
            case FIRST_PARTY -> options.add("~third-party");
            case THIRD_PARTY -> options.add("third-party");
            case ANY -> {
            }
        }
        if (rule.matchCase()) {
            options.add("match-case");
        }
        if (rule.important()) {
            options.add("important");
        }
        rule.modifiers().forEach(modifier -> options.add(modifier.type().value()
                + (modifier.value().isEmpty() ? "" : "=" + modifier.value())));
        if (!options.isEmpty()) {
            text.append('$').append(String.join(",", options));
        }
        return Optional.of(new Encoding(text.toString(), false, rule, exact("Adblock 网络规则精确转换")));
    }

    @Override
    public FinishResult finish() {
        long added = 0;
        long duplicates = 0;
        if (finished) {
            return FinishResult.EMPTY;
        }
        for (DomainName domain : whitelist) {
            String exception = "@@||" + domain.value() + "^";
            if (hasSafariAffinity) {
                exception = "!#safari_cb_affinity(all)" + LF + exception + LF + "!#safari_cb_affinity";
            }
            byte[] bytes = exception.getBytes(StandardCharsets.UTF_8);
            if (deduplicator.add(bytes)) {
                writeBytes(exception + LF);
                added++;
            } else {
                duplicates++;
            }
        }
        try {
            output.flush();
            finished = true;
        } catch (IOException exception) {
            throw new OutputException("刷新 Adblock 输出失败: " + target.path(), exception);
        }
        return new FinishResult(added, duplicates);
    }

    private void writeBytes(String value) {
        try {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new OutputException("写入 Adblock 输出失败: " + target.path(), exception);
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
            throw new OutputException("关闭 Adblock 输出失败: " + target.path(), exception);
        }
    }

    private record Encoding(
            String text,
            boolean passthrough,
            RuleEntry effectiveRule,
            ConversionDecision decision) {
    }
}
