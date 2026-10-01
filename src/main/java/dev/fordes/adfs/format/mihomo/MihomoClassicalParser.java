package dev.fordes.adfs.format.mihomo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.fordes.adfs.config.EffectiveConfig.InputLimits;
import dev.fordes.adfs.config.EffectiveConfig.RuleConfig;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.format.RuleConsumer;
import dev.fordes.adfs.format.ParseResult;
import dev.fordes.adfs.format.RuleParser;
import dev.fordes.adfs.format.TextSource;
import dev.fordes.adfs.rule.model.DomainEnvelope;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.DomainRule;
import dev.fordes.adfs.rule.model.ExactDomain;
import dev.fordes.adfs.rule.model.IpCidr;
import dev.fordes.adfs.rule.model.IpCidrMatch;
import dev.fordes.adfs.rule.model.IpCidrRule;
import dev.fordes.adfs.rule.model.KeywordDomain;
import dev.fordes.adfs.rule.model.MatchSide;
import dev.fordes.adfs.rule.model.NetworkMatch;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.PortMatch;
import dev.fordes.adfs.rule.model.ProcessMatch;
import dev.fordes.adfs.rule.model.RegexDomain;
import dev.fordes.adfs.rule.model.RouteRule;
import dev.fordes.adfs.rule.model.RuleAction;
import dev.fordes.adfs.rule.model.SuffixDomain;
import dev.fordes.adfs.rule.model.WildcardSyntax;
import dev.fordes.adfs.rule.model.WildcardDomain;
import dev.fordes.adfs.source.SourceLine;
import dev.fordes.adfs.source.SourceSession;

public final class MihomoClassicalParser implements RuleParser {

    private final InputLimits limits;
    private final RuleConfig rules;
    private final MihomoYaml container = new MihomoYaml();

    public MihomoClassicalParser(InputLimits limits, RuleConfig rules) {
        this.limits = limits;
        this.rules = rules;
    }

    @Override
    public ParseResult parse(SourceSession session, RuleConsumer consumer) {
        TextSource.read(session, limits, text -> text.startsWith("#"), line -> parseLine(line, consumer));
        return ParseResult.COMPLETE;
    }

    private void parseLine(SourceLine line, RuleConsumer consumer) {
        String text = container.read(line);
        if (text == null) {
            return;
        }
        text = TextSource.ruleText(new SourceLine(line.source(), line.lineNumber(), text),
                rules.minLength(), rules.maxLength());
        int separator = text.indexOf(',');
        if (separator < 0) {
            throw syntax(line, "Mihomo classical 至少需要类型和参数");
        }
        String leadingType = text.substring(0, separator).strip().toUpperCase(Locale.ROOT);
        if (leadingType.equals("DOMAIN-REGEX")) {
            String expression = text.substring(separator + 1).strip();
            if (expression.isEmpty()) {
                throw syntax(line, "Mihomo DOMAIN-REGEX 表达式不得为空");
            }
            consumer.accept(new DomainRule(new RegexDomain(expression), RuleAction.BLOCK));
            return;
        }
        List<String> fields = csv(text, line);
        if (fields.size() < 2) {
            throw syntax(line, "Mihomo classical 至少需要类型和参数");
        }
        String type = fields.getFirst().toUpperCase(Locale.ROOT);
        String parameter = fields.get(1);
        if (fields.stream().anyMatch(String::isEmpty)) {
            throw syntax(line, "Mihomo classical 包含空字段");
        }
        if (fields.size() > 2 && !type.equals("AND") && !type.equals("OR") && !type.equals("NOT")) {
            if (fields.size() != 3 || !fields.get(2).equals("no-resolve")
                    || !(type.equals("IP-CIDR") || type.equals("IP-CIDR6"))) {
                throw syntax(line, "Mihomo classical 包含不支持的尾部字段");
            }
            IpCidr.parse(parameter);
            consumer.accept(new OpaqueRule(RuleType.MIHOMO, RuleDialect.CLASSICAL, DomainEnvelope.UNKNOWN, text));
            return;
        }
        switch (type) {
            case "DOMAIN" -> consumer.accept(new DomainRule(new ExactDomain(new DomainName(parameter)), RuleAction.BLOCK));
            case "DOMAIN-SUFFIX" -> consumer.accept(
                    new DomainRule(new SuffixDomain(new DomainName(parameter)), RuleAction.BLOCK));
            case "DOMAIN-KEYWORD" -> consumer.accept(new DomainRule(new KeywordDomain(parameter), RuleAction.BLOCK));
            case "DOMAIN-WILDCARD" -> consumer.accept(new DomainRule(new WildcardDomain(parameter, WildcardSyntax.MIHOMO_CLASSICAL), RuleAction.BLOCK));
            case "IP-CIDR", "IP-CIDR6" -> {
                IpCidr cidr = IpCidr.parse(parameter);
                consumer.accept(new IpCidrRule(cidr.network(), cidr.prefixLength(), RuleAction.BLOCK));
            }
            case "SRC-IP-CIDR" -> {
                IpCidr cidr = IpCidr.parse(parameter);
                consumer.accept(new RouteRule(
                        new IpCidrMatch(MatchSide.SOURCE, cidr.network(), cidr.prefixLength())));
            }
            case "DST-PORT" -> consumer.accept(new RouteRule(parsePort(parameter, line, MatchSide.DESTINATION)));
            case "SRC-PORT" -> consumer.accept(new RouteRule(parsePort(parameter, line, MatchSide.SOURCE)));
            case "NETWORK" -> consumer.accept(new RouteRule(new NetworkMatch(parseNetwork(parameter, line))));
            case "PROCESS-NAME" -> consumer.accept(
                    new RouteRule(new ProcessMatch(ProcessMatch.ProcessField.NAME, parameter)));
            case "PROCESS-PATH" -> consumer.accept(
                    new RouteRule(new ProcessMatch(ProcessMatch.ProcessField.PATH, parameter)));
            default -> consumer.accept(new OpaqueRule(
                    RuleType.MIHOMO, RuleDialect.CLASSICAL, DomainEnvelope.UNKNOWN, text));
        }
    }


    private static List<String> csv(String text, SourceLine line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                fields.add(field.toString().strip());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw syntax(line, "Mihomo classical CSV 引号未闭合");
        }
        fields.add(field.toString().strip());
        return List.copyOf(fields);
    }

    private static PortMatch parsePort(String value, SourceLine line, MatchSide side) {
        int separator = value.indexOf('-');
        try {
            int first = Integer.parseInt(separator < 0 ? value : value.substring(0, separator));
            int last = Integer.parseInt(separator < 0 ? value : value.substring(separator + 1));
            return new PortMatch(side, first, last);
        } catch (IllegalArgumentException exception) {
            throw new RuleProcessingException("Mihomo 端口范围非法: " + value, exception);
        }
    }

    private static NetworkMatch.Network parseNetwork(String value, SourceLine line) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "tcp" -> NetworkMatch.Network.TCP;
            case "udp" -> NetworkMatch.Network.UDP;
            default -> throw syntax(line, "Mihomo NETWORK 只支持 tcp/udp");
        };
    }

    private static RuleProcessingException syntax(SourceLine line, String message) {
        return new RuleProcessingException(message);
    }
}
