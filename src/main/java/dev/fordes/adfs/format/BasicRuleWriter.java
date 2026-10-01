package dev.fordes.adfs.format;

import dev.fordes.adfs.config.ContainerFormat;
import dev.fordes.adfs.config.OutputSpec;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.error.OutputException;
import dev.fordes.adfs.format.conversion.AdblockDomainConversion;
import dev.fordes.adfs.format.conversion.DedupMode;
import dev.fordes.adfs.format.conversion.ProjectedRule;
import dev.fordes.adfs.format.conversion.RegexCompatibility;
import dev.fordes.adfs.rule.conversion.*;
import dev.fordes.adfs.rule.dedup.OutputDeduplicator;
import dev.fordes.adfs.rule.model.*;
import dev.fordes.adfs.rule.spool.RuleSpool;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Slf4j
public final class BasicRuleWriter implements RuleWriter {

    private static final String LF = "\n";
    private final OutputSpec target;
    private final String outputName;
    private final List<DomainName> whitelist;
    private final OutputDeduplicator deduplicator;
    private final OutputStream output;
    private final OutputRuleProcessor<String> processor;
    private boolean finished;
    private boolean yamlStarted;

    public BasicRuleWriter(
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
        this.processor = new OutputRuleProcessor<>(policy, this::shouldRemoveForWhitelist,
                deduplicator, this::writeProjected);
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
        EncodingResult result = encode(entry);
        return switch (result) {
            case Unsupported(String reason) -> {
                if (log.isDebugEnabled()) {
                    log.debug("规则未转换:  {} --> {} | {} --> {}",
                            MDC.get(RuleSpool.INPUT), outputName, MDC.get(RuleSpool.INPUT_RULE), reason);
                }
                yield WriteResult.UNSUPPORTED;
            }
            case Encoded(String text, ConversionDecision decision, boolean passthrough, RuleEntry effectiveRule) -> {
                byte[] record = text.getBytes(StandardCharsets.UTF_8);
                if (text.codePoints().anyMatch(Character::isISOControl)) {
                    throw new OutputException("目标规则包含不可表达的控制字符: " + target.path());
                }
                DedupMode dedupMode = dev.fordes.adfs.rule.normalize.RuleOrder.requiresOrder(effectiveRule)
                        ? DedupMode.ORDERED : DedupMode.SET_LIKE;
                yield processor.process(new ProjectedRule<>(
                        text, record, effectiveRule, decision, passthrough, dedupMode));
            }
        };
    }

    private EncodingResult encode(RuleEntry entry) {
        return switch (entry) {
            case SafariRule safari -> encodeSafari(safari);
            case DomainRule(DomainPattern pattern, RuleAction action) -> encodeDomain(pattern, action);
            case HostMappingRule(IpAddress address, DomainName hostname) -> encodeHost(address, hostname);
            case IpCidrRule(IpAddress network, int prefixLength, RuleAction action) ->
                    encodeIpCidr(network, prefixLength, action);
            case RouteRule(MatchExpression expression) -> encodeRoute(expression);
            case AdblockNetworkRule rule -> encodeAdblockDomain(rule);
            case DnsAddressRule rule -> DnsAddressEncoding.encode(rule, target.type())
                    .<EncodingResult>map(text -> new Encoded(text,
                            new ConversionDecision(ConversionScope.EXACT, "保留原生 DNS 响应赋值"), false, rule))
                    .orElseGet(() -> new Unsupported("目标不能保留原生 DNS 响应语义"));
            case CosmeticRule _ -> new Unsupported("基础 Writer 不能表达元素规则");
            case OpaqueRule(var type, var dialect, var envelope, String payload) -> {
                if (type == target.type() && dialect == target.dialect()) {
                    yield new Encoded(payload, new ConversionDecision(ConversionScope.EXACT, "同方言透传"), true, entry);
                }
                yield new Unsupported("不透明规则不能跨格式或跨方言转换");
            }
        };
    }

    private EncodingResult encodeSafari(SafariRule safari) {
        return switch (encode(safari.rule())) {
            case Encoded(String text, ConversionDecision decision, boolean passthrough, RuleEntry effectiveRule) ->
                    new Encoded(text, decision.with(ConversionScope.EXPANDED,
                            ConversionLoss.DROPPED_PLATFORM_CONSTRAINT,
                            "目标格式不能保留 Safari 内容拦截器范围"), passthrough, effectiveRule);
            case Unsupported unsupported -> unsupported;
        };
    }

    private EncodingResult encodeAdblockDomain(AdblockNetworkRule rule) {
        if (!rule.modifiers().isEmpty()) {
            return new Unsupported("目标不能表达该 Adblock 动作修饰符");
        }
        if (rule.pattern().kind() == AdblockPattern.Kind.REGEX) {
            return encodeAdblockRegex(rule);
        }
        if (!AdblockDomainConversion.supportsSubject(rule)) {
            return new Unsupported("目标不能表达该 Adblock 规则主体");
        }
        boolean preserveImportant = target.type() == RuleType.DNS;
        EncodingResult result = preserveImportant
                ? encodeDnsAdblockDomain(rule)
                : encodeDomain(new SuffixDomain(new DomainName(rule.pattern().value())), rule.action());
        return switch (result) {
            case Encoded(String text, ConversionDecision decision, boolean passthrough, RuleEntry effectiveRule) ->
                    new Encoded(text, AdblockDomainConversion.decide(rule, preserveImportant, decision), passthrough, effectiveRule);
            case Unsupported unsupported -> unsupported;
        };
    }

    private EncodingResult encodeAdblockRegex(AdblockNetworkRule rule) {
        if (target.type() != RuleType.MIHOMO) {
            return new Unsupported("目标不能表达 Adblock 正则规则");
        }
        if (target.dialect() != RuleDialect.CLASSICAL) {
            return new Unsupported("Mihomo domain/ipcidr behavior 不支持 DOMAIN-REGEX; 请使用 classical behavior");
        }
        var expression = RegexCompatibility.adblockToDomain(rule.pattern().value(), rule.matchCase());
        if (expression.isEmpty()) {
            return new Unsupported("Adblock 正则不属于目标可验证的公共语法子集");
        }
        EncodingResult result = encodeMihomoDomain(new RegexDomain(expression.orElseThrow()), rule.action());
        boolean anchored = hasStringAnchor(rule.pattern().value());
        ConversionDecision projection = new ConversionDecision(
                anchored ? ConversionScope.MIXED : ConversionScope.REDUCED,
                Set.of(ConversionLoss.URL_REGEX_PROJECTED_TO_DOMAIN),
                anchored
                        ? "Adblock 正则匹配完整请求 URL, 带字符串锚点的 Mihomo DOMAIN-REGEX 改为匹配域名后范围不可比"
                        : "Mihomo DOMAIN-REGEX 仅匹配域名, 无法保留 Adblock 正则对完整请求 URL 的匹配");
        return switch (result) {
            case Encoded(String text, var decision, boolean passthrough, RuleEntry effectiveRule) ->
                    new Encoded(text, AdblockDomainConversion.decide(rule, false, projection), passthrough, effectiveRule);
            case Unsupported unsupported -> unsupported;
        };
    }

    private static EncodingResult encodeDnsAdblockDomain(AdblockNetworkRule rule) {
        String text = (rule.action() == RuleAction.ALLOW ? "@@" : "")
                + "||" + rule.pattern().value() + "^"
                + (rule.important() ? "$important" : "");
        return new Encoded(text,
                new ConversionDecision(ConversionScope.EXACT, "DNS/AdGuard 域名规则精确转换"), false, rule);
    }

    private EncodingResult encodeDomain(DomainPattern pattern, RuleAction action) {
        return switch (target.type()) {
            case HOSTS -> encodeHostsDomain(pattern, action);
            case DNS -> encodeDnsDomain(pattern, action);
            case DNSMASQ -> encodeDnsmasqDomain(pattern, action);
            case MIHOMO -> encodeMihomoDomain(pattern, action);
            case ADBLOCK, SING_BOX, SMARTDNS -> new Unsupported("目标格式尚未在基础 Writer 中实现");
        };
    }

    private static EncodingResult encodeHostsDomain(DomainPattern pattern, RuleAction action) {
        if (action == RuleAction.ALLOW) {
            return new Unsupported("Hosts 不能表达允许规则");
        }
        ConversionScope scope = pattern instanceof ExactDomain ? ConversionScope.EXACT : ConversionScope.REDUCED;
        if (!(pattern instanceof ExactDomain || pattern instanceof SuffixDomain)) {
            return new Unsupported("Hosts 不能表达关键词、通配符或正则域名");
        }
        return new Encoded("0.0.0.0 " + pattern.value(),
                new ConversionDecision(scope,
                        scope == ConversionScope.REDUCED ? Set.of(ConversionLoss.SUFFIX_REDUCED_TO_ROOT) : Set.of(),
                        "Hosts 只能写出单一主机名"), false,
                new DomainRule(new ExactDomain(new DomainName(pattern.value())), action));
    }

    private static EncodingResult encodeDnsDomain(DomainPattern pattern, RuleAction action) {
        String prefix = action == RuleAction.ALLOW ? "@@" : "";
        if (!(pattern instanceof ExactDomain || pattern instanceof SuffixDomain)) {
            return new Unsupported("DNS 基础 Writer 尚不能表达复杂域名模式");
        }
        String text = pattern instanceof ExactDomain ? "|" + pattern.value() + "|" : "||" + pattern.value() + "^";
        return new Encoded(prefix + text,
                new ConversionDecision(ConversionScope.EXACT, "DNS 域名边界精确转换"), false, new DomainRule(pattern, action));
    }

    private static EncodingResult encodeDnsmasqDomain(DomainPattern pattern, RuleAction action) {
        if (action == RuleAction.ALLOW) {
            return new Unsupported("Dnsmasq 规则集不能表达允许规则");
        }
        ConversionScope scope = pattern instanceof SuffixDomain ? ConversionScope.EXACT : ConversionScope.EXPANDED;
        if (!(pattern instanceof ExactDomain || pattern instanceof SuffixDomain)) {
            return new Unsupported("Dnsmasq 不能安全表达复杂域名模式");
        }
        return new Encoded("address=/" + pattern.value() + "/#",
                new ConversionDecision(scope,
                        scope == ConversionScope.EXPANDED ? Set.of(ConversionLoss.ROOT_EXPANDED_TO_SUBDOMAINS) : Set.of(),
                        "Dnsmasq 域名段覆盖根域名及子域"), false,
                new DomainRule(new SuffixDomain(new DomainName(pattern.value())), action));
    }

    private EncodingResult encodeMihomoDomain(DomainPattern pattern, RuleAction action) {
        if (action == RuleAction.ALLOW) {
            return new Unsupported("Mihomo 中性规则集不能表达允许动作");
        }
        if (target.dialect() == RuleDialect.IPCIDR) {
            return new Unsupported("Mihomo ipcidr 不能表达域名规则");
        }
        if (target.dialect() == RuleDialect.CLASSICAL) {
            String type = switch (pattern) {
                case ExactDomain _ -> "DOMAIN";
                case SuffixDomain _ -> "DOMAIN-SUFFIX";
                case Subdomain _ -> "DOMAIN-REGEX";
                case KeywordDomain _ -> "DOMAIN-KEYWORD";
                case WildcardDomain _ -> "DOMAIN-WILDCARD";
                case RegexDomain _ -> "DOMAIN-REGEX";
            };
            if (pattern instanceof WildcardDomain wildcard && wildcard.syntax() != WildcardSyntax.MIHOMO_CLASSICAL) {
                return new Unsupported("通配符语义与 Mihomo classical 不同");
            }
            String value = pattern instanceof Subdomain ? "^.+\\." + pattern.value().replace(".", "\\.") + "$"
                    : pattern instanceof RegexDomain ? pattern.value() : csvField(pattern.value());
            return new Encoded(type + "," + value,
                    new ConversionDecision(ConversionScope.EXACT, "Mihomo classical 域名规则精确转换"), false,
                    new DomainRule(pattern, action));
        }
        String prefix = pattern instanceof SuffixDomain ? "+." : pattern instanceof Subdomain ? "." : "";
        if (pattern instanceof RegexDomain || pattern instanceof KeywordDomain) {
            return new Unsupported("Mihomo domain behavior 不支持 DOMAIN-KEYWORD 或 DOMAIN-REGEX; 请使用 classical behavior");
        }
        if (pattern instanceof WildcardDomain wildcard && (wildcard.syntax() != WildcardSyntax.MIHOMO_DOMAIN
                || !supportsMihomoDomainWildcard(wildcard.value()))) {
            return new Unsupported("Mihomo domain behavior 的 * 只能作为完整域名段");
        }
        return new Encoded(prefix + pattern.value(),
                new ConversionDecision(ConversionScope.EXACT, "Mihomo domain 可精确表达域名模式"), false,
                new DomainRule(pattern, action));
    }

    private static boolean supportsMihomoDomainWildcard(String value) {
        String labels = value.startsWith("+.") ? value.substring(2) : value.startsWith(".") ? value.substring(1) : value;
        for (String label : labels.split("\\.", -1)) {
            if (label.isEmpty() || label.indexOf('*') >= 0 && !label.equals("*")) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasStringAnchor(String expression) {
        boolean escaped = false;
        boolean characterClass = false;
        for (int index = 0; index < expression.length(); index++) {
            char character = expression.charAt(index);
            if (escaped) {
                if (!characterClass && (character == 'A' || character == 'Z' || character == 'z')) {
                    return true;
                }
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '[') {
                characterClass = true;
            } else if (character == ']') {
                characterClass = false;
            } else if (!characterClass && (character == '^' || character == '$')) {
                return true;
            }
        }
        return false;
    }

    private EncodingResult encodeHost(IpAddress address, DomainName hostname) {
        return switch (target.type()) {
            case HOSTS, DNS -> new Encoded(address.text() + " " + hostname.value(),
                    new ConversionDecision(ConversionScope.EXACT, "目标支持 hosts 映射"), false,
                    new HostMappingRule(address, hostname));
            case DNSMASQ -> new Encoded("address=/" + hostname.value() + "/" + address.text(),
                    new ConversionDecision(ConversionScope.EXPANDED,
                            Set.of(ConversionLoss.HOST_MAPPING_EXPANDED_TO_SUBDOMAINS),
                            "Dnsmasq address 同时覆盖子域"), false,
                    new DomainRule(new SuffixDomain(hostname), RuleAction.BLOCK));
            case MIHOMO, ADBLOCK, SING_BOX, SMARTDNS -> new Unsupported("目标不能表达地址映射");
        };
    }

    private EncodingResult encodeIpCidr(IpAddress network, int prefixLength, RuleAction action) {
        if (target.type() != RuleType.MIHOMO || target.dialect() == RuleDialect.DOMAIN
                || action == RuleAction.ALLOW) {
            return new Unsupported("目标不能表达 IP/CIDR 阻断集合");
        }
        String cidr = new IpCidr(network, prefixLength).text();
        String text = target.dialect() == RuleDialect.CLASSICAL
                ? (network.family() == IpFamily.IPV4 ? "IP-CIDR," : "IP-CIDR6,") + cidr
                : cidr;
        return new Encoded(text,
                new ConversionDecision(ConversionScope.EXACT, "Mihomo ipcidr 精确转换"), false,
                new IpCidrRule(network, prefixLength, action));
    }

    private EncodingResult encodeRoute(MatchExpression expression) {
        if (target.type() != RuleType.MIHOMO || target.dialect() != RuleDialect.CLASSICAL) {
            return new Unsupported("目标不能表达复合路由规则");
        }
        String text = switch (expression) {
            case DomainMatch(DomainPattern pattern) -> switch (encodeMihomoDomain(pattern, RuleAction.BLOCK)) {
                case Encoded(String encoded, var decision, var passthrough, var effectiveRule) -> encoded;
                case Unsupported _ -> "";
            };
            case PortMatch(MatchSide side, int first, int last) ->
                    (side == MatchSide.DESTINATION ? "DST-PORT," : "SRC-PORT,")
                            + (first == last ? first : first + "-" + last);
            case NetworkMatch(var network) -> "NETWORK," + network.value();
            case ProcessMatch(var field, String value) -> switch (field) {
                case NAME -> "PROCESS-NAME," + csvField(value);
                case PATH -> "PROCESS-PATH," + csvField(value);
                case PACKAGE -> "";
            };
            case IpCidrMatch(MatchSide side, IpAddress network, int prefixLength) ->
                    (side == MatchSide.SOURCE ? "SRC-IP-CIDR," :
                            network.family() == IpFamily.IPV4 ? "IP-CIDR," : "IP-CIDR6,")
                            + new IpCidr(network, prefixLength).text();
            case AllOf _, AnyOf _, Not _ -> "";
        };
        if (text.isEmpty()) {
            return new Unsupported("Mihomo classical 尚不能表达该复合规则");
        }
        return new Encoded(text, new ConversionDecision(ConversionScope.EXACT, "Mihomo classical 路由规则精确转换"),
                false, new RouteRule(expression));
    }

    private static String csvField(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private boolean shouldRemoveForWhitelist(RuleEntry entry) {
        return target.type() != RuleType.DNS && WhitelistMatcher.matches(entry, whitelist);
    }

    private String wrap(String text) {
        if (target.container() == ContainerFormat.YAML) {
            if (!yamlStarted) {
                writeBytes("payload:" + LF);
                yamlStarted = true;
            }
            return "  - '" + text.replace("'", "''") + "'" + LF;
        }
        return text + LF;
    }

    private void writeProjected(String text) {
        String outputRule = wrap(text);
        writeBytes(outputRule);
        log.trace("规则转换成功:  {} --> {} | {} --> {}",
                MDC.get(RuleSpool.INPUT), outputName, MDC.get(RuleSpool.INPUT_RULE),
                outputRule.substring(0, outputRule.length() - LF.length()));
    }

    @Override
    public FinishResult finish() {
        long added = 0;
        long duplicates = 0;
        if (finished) {
            return FinishResult.EMPTY;
        }
        if (target.type() == RuleType.DNS) {
            for (DomainName domain : whitelist) {
                String exception = "@@||" + domain.value() + "^";
                byte[] record = exception.getBytes(StandardCharsets.UTF_8);
                if (deduplicator.add(record)) {
                    writeBytes(wrap(exception));
                    added++;
                } else {
                    duplicates++;
                }
            }
        }
        if (target.container() == ContainerFormat.YAML && !yamlStarted) {
            writeBytes("payload: []" + LF);
            yamlStarted = true;
        }
        try {
            output.flush();
            finished = true;
        } catch (IOException exception) {
            throw new OutputException("刷新目标输出失败: " + target.path(), exception);
        }
        return new FinishResult(added, duplicates);
    }

    private void writeBytes(String value) {
        try {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new OutputException("写入目标输出失败: " + target.path(), exception);
        }
    }

    @Override
    public void close() {
        try (output) {
            finish();
        } catch (IOException exception) {
            throw new OutputException("关闭目标输出失败: " + target.path(), exception);
        }
    }
}

sealed interface EncodingResult permits Encoded, Unsupported {
}

record Encoded(String text, ConversionDecision decision, boolean passthrough, RuleEntry effectiveRule) implements EncodingResult {
}

record Unsupported(String reason) implements EncodingResult {
}
