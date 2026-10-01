package dev.fordes.adfs.format.singbox;

import dev.fordes.adfs.config.OutputSpec;
import dev.fordes.adfs.error.OutputException;
import dev.fordes.adfs.format.FinishResult;
import dev.fordes.adfs.format.OutputRuleProcessor;
import dev.fordes.adfs.format.RuleWriter;
import dev.fordes.adfs.format.WriteResult;
import dev.fordes.adfs.format.conversion.AdblockDomainConversion;
import dev.fordes.adfs.format.conversion.DedupMode;
import dev.fordes.adfs.format.conversion.ProjectedRule;
import dev.fordes.adfs.rule.conversion.*;
import dev.fordes.adfs.rule.dedup.OutputDeduplicator;
import dev.fordes.adfs.rule.model.*;
import dev.fordes.adfs.rule.spool.RuleSpool;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public final class SingBoxWriter implements RuleWriter {

    private static final int FORMAT_VERSION = 3;
    private final OutputSpec target;
    private final String outputName;
    private final List<DomainName> whitelist;
    private final OutputDeduplicator deduplicator;
    private final JsonFactory factory = new JsonFactory();
    private final JsonGenerator output;
    private final OutputRuleProcessor<byte[]> processor;
    private boolean finished;


    public SingBoxWriter(
            OutputSpec target,
            ConversionPolicy policy,
            Set<String> whitelist,
            OutputDeduplicator deduplicator,
            OutputStream stream) {
        this.target = target;
        this.outputName = target.path() + " (" + target.type().value() + ")";
        this.whitelist = whitelist.stream().map(DomainName::new).toList();
        this.deduplicator = deduplicator;
        try {
            output = factory.createGenerator(ObjectWriteContext.empty(), stream);
            output.writeStartObject();
            output.writeNumberProperty("version", FORMAT_VERSION);
            output.writeArrayPropertyStart("rules");
            processor = new OutputRuleProcessor<>(policy, this::shouldRemove,
                    deduplicator, this::writeProjected);
        } catch (JacksonException exception) {
            throw new OutputException("初始化 Sing-box 输出失败: " + target.path(), exception);
        }
    }

    @Override
    public WriteResult write(RuleEntry entry) {
        if (finished) {
            throw new IllegalStateException("Writer finish 后不能继续写入");
        }
        if (entry instanceof OpaqueRule opaque
                && (opaque.type() != target.type() || opaque.dialect() != target.dialect())) {
            if (log.isDebugEnabled()) {
                log.debug("规则未转换:  {} --> {} | {} --> 不透明规则不能跨格式或跨方言转换",
                        MDC.get(RuleSpool.INPUT), outputName, MDC.get(RuleSpool.INPUT_RULE));
            }
            return WriteResult.UNSUPPORTED;
        }
        Optional<SingEncoding> encoded = encode(entry);
        if (encoded.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("规则未转换:  {} --> {} | {} --> Sing-box 无法表达该规则类型、匹配条件或允许动作",
                        MDC.get(RuleSpool.INPUT), outputName, MDC.get(RuleSpool.INPUT_RULE));
            }
            return WriteResult.UNSUPPORTED;
        }
        SingEncoding value = encoded.orElseThrow();
        return processor.process(new ProjectedRule<>(value.bytes(), value.bytes(), value.effectiveRule(),
                value.decision(), value.passthrough(), DedupMode.SET_LIKE));
    }

    private Optional<SingEncoding> encode(RuleEntry entry) {
        return switch (entry) {
            case SafariRule safari -> encodeSafari(safari);
            case DomainRule(DomainPattern pattern, RuleAction action) -> action == RuleAction.ALLOW
                    || pattern instanceof WildcardDomain
                    ? Optional.empty() : Optional.of(encodeExpression(new DomainMatch(pattern), false));
            case IpCidrRule(var network, int prefixLength, RuleAction action) -> action == RuleAction.ALLOW
                    ? Optional.empty() : Optional.of(encodeExpression(
                            new IpCidrMatch(MatchSide.DESTINATION, network, prefixLength), false));
            case RouteRule(MatchExpression expression) -> supports(expression)
                    ? Optional.of(encodeExpression(expression, false)) : Optional.empty();
            case AdblockNetworkRule rule -> encodeAdblockDomain(rule);
            case CosmeticRule _ -> Optional.empty();
            case HostMappingRule _, DnsAddressRule _ -> Optional.empty();
            case OpaqueRule opaque -> opaque.type() == target.type() && opaque.dialect() == target.dialect()
                    ? Optional.of(new SingEncoding(opaque.payload().getBytes(StandardCharsets.UTF_8), true,
                            exact("同格式透传"), opaque))
                    : Optional.empty();
        };
    }

    private Optional<SingEncoding> encodeSafari(SafariRule safari) {
        return encode(safari.rule()).map(encoded -> new SingEncoding(encoded.bytes(), encoded.passthrough(),
                encoded.decision().with(ConversionScope.EXPANDED, ConversionLoss.DROPPED_PLATFORM_CONSTRAINT,
                        "目标格式不能保留 Safari 内容拦截器范围"), encoded.effectiveRule()));
    }

    private Optional<SingEncoding> encodeAdblockDomain(AdblockNetworkRule rule) {
        if (rule.action() == RuleAction.ALLOW || !AdblockDomainConversion.supportsSubject(rule)) {
            return Optional.empty();
        }
        ConversionDecision decision = AdblockDomainConversion.decide(rule, false,
                new ConversionDecision(ConversionScope.EXACT, "Sing-box 后缀域名规则精确转换"));
        SingEncoding encoding = encodeExpression(new DomainMatch(
                new SuffixDomain(new DomainName(rule.pattern().value()))), false);
        return Optional.of(new SingEncoding(encoding.bytes(), encoding.passthrough(), decision,
                encoding.effectiveRule()));
    }

    private static boolean supports(MatchExpression expression) {
        return switch (expression) {
            case DomainMatch(DomainPattern pattern) -> !(pattern instanceof WildcardDomain);
            case IpCidrMatch _, PortMatch _, NetworkMatch _, ProcessMatch _ -> true;
            case AllOf(List<MatchExpression> expressions) -> expressions.stream().allMatch(SingBoxWriter::supports);
            case AnyOf(List<MatchExpression> expressions) -> expressions.stream().allMatch(SingBoxWriter::supports);
            case Not(MatchExpression child) -> supports(child);
        };
    }

    private SingEncoding encodeExpression(MatchExpression expression, boolean passthrough) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator generator = factory.createGenerator(ObjectWriteContext.empty(), bytes)) {
            writeExpression(generator, expression, false);
            generator.flush();
            return new SingEncoding(bytes.toByteArray(), passthrough, exact("Sing-box 规则精确转换"),
                    new RouteRule(expression));
        } catch (JacksonException exception) {
            throw new OutputException("编码 Sing-box 规则失败: " + target.path(), exception);
        }
    }

    private static void writeExpression(JsonGenerator generator, MatchExpression expression, boolean invert)
            throws JacksonException {
        if (expression instanceof Not(MatchExpression child)) {
            writeExpression(generator, child, !invert);
            return;
        }
        generator.writeStartObject();
        switch (expression) {
            case DomainMatch(DomainPattern pattern) -> writeDomain(generator, pattern);
            case IpCidrMatch(MatchSide side, var network, int prefixLength) ->
                    generator.writeArrayPropertyStart(side == MatchSide.SOURCE ? "source_ip_cidr" : "ip_cidr").writeString(
                            new IpCidr(network, prefixLength).text()).writeEndArray();
            case PortMatch(MatchSide side, int first, int last) -> {
                if (first == last) {
                    generator.writeArrayPropertyStart(side == MatchSide.SOURCE ? "source_port" : "port")
                            .writeNumber(first).writeEndArray();
                } else {
                    generator.writeArrayPropertyStart(side == MatchSide.SOURCE ? "source_port_range" : "port_range")
                            .writeString(first + ":" + last).writeEndArray();
                }
            }
            case NetworkMatch(var network) ->
                    generator.writeArrayPropertyStart("network").writeString(network.value()).writeEndArray();
            case ProcessMatch(var field, String value) -> {
                String name = switch (field) {
                    case NAME -> "process_name";
                    case PATH -> "process_path";
                    case PACKAGE -> "package_name";
                };
                generator.writeArrayPropertyStart(name).writeString(value).writeEndArray();
            }
            case AllOf(List<MatchExpression> expressions) -> writeLogical(generator, "and", expressions);
            case AnyOf(List<MatchExpression> expressions) -> writeLogical(generator, "or", expressions);
            case Not _ -> throw new IllegalStateException("Not 已在对象开始前处理");
        }
        if (invert) {
            generator.writeBooleanProperty("invert", true);
        }
        generator.writeEndObject();
    }

    private static void writeDomain(JsonGenerator generator, DomainPattern pattern) throws JacksonException {
        String field = switch (pattern) {
            case ExactDomain _ -> "domain";
            case SuffixDomain _, Subdomain _ -> "domain_suffix";
            case KeywordDomain _ -> "domain_keyword";
            case RegexDomain _ -> "domain_regex";
            case WildcardDomain _ -> "";
        };
        if (field.isEmpty()) {
            throw new OutputException("Sing-box 不能表达 Mihomo 通配域名");
        }
        generator.writeArrayPropertyStart(field).writeString((pattern instanceof Subdomain ? "." : "") + pattern.value()).writeEndArray();
    }

    private static void writeLogical(JsonGenerator generator, String mode, List<MatchExpression> expressions)
            throws JacksonException {
        generator.writeStringProperty("type", "logical");
        generator.writeStringProperty("mode", mode);
        generator.writeArrayPropertyStart("rules");
        for (MatchExpression expression : expressions) {
            writeExpression(generator, expression, false);
        }
        generator.writeEndArray();
    }

    private boolean shouldRemove(RuleEntry entry) {
        return WhitelistMatcher.matches(entry, whitelist);
    }

    @Override
    public FinishResult finish() {
        if (finished) {
            return FinishResult.EMPTY;
        }
        try {
            output.writeEndArray();
            output.writeEndObject();
            output.flush();
            finished = true;
        } catch (JacksonException exception) {
            throw new OutputException("结束 Sing-box 输出失败: " + target.path(), exception);
        }
        return FinishResult.EMPTY;
    }

    @Override
    public void close() {
        try (output) {
            finish();
        } catch (JacksonException exception) {
            throw new OutputException("关闭 Sing-box 输出失败: " + target.path(), exception);
        }
    }

    private static ConversionDecision exact(String reason) {
        return new ConversionDecision(ConversionScope.EXACT, reason);
    }

    private void writeProjected(byte[] bytes) {
        try {
            String text = new String(bytes, StandardCharsets.UTF_8);
            output.writeRawValue(text);
            log.trace("规则转换成功:  {} --> {} | {} --> {}",
                    MDC.get(RuleSpool.INPUT), outputName, MDC.get(RuleSpool.INPUT_RULE), text);
        } catch (JacksonException exception) {
            throw new OutputException("写入 Sing-box 规则失败: " + target.path(), exception);
        }
    }
}

record SingEncoding(byte[] bytes, boolean passthrough, ConversionDecision decision, RuleEntry effectiveRule) {

    SingEncoding {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
