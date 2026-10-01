package dev.fordes.adfs.format.smartdns;

import java.util.ArrayList;
import java.util.List;

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
import dev.fordes.adfs.rule.model.DnsAddressRule;
import dev.fordes.adfs.rule.model.DnsResponse;
import dev.fordes.adfs.rule.model.IpFamily;
import java.util.Set;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.DomainPattern;
import dev.fordes.adfs.rule.model.ExactDomain;
import dev.fordes.adfs.rule.model.IpAddress;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.SuffixDomain;
import dev.fordes.adfs.rule.model.Subdomain;
import dev.fordes.adfs.rule.model.WildcardSyntax;
import dev.fordes.adfs.rule.model.WildcardDomain;
import dev.fordes.adfs.source.SourceLine;
import dev.fordes.adfs.source.SourceSession;

public final class SmartDnsParser implements RuleParser {

    private final InputLimits limits;
    private final RuleConfig rules;

    public SmartDnsParser(InputLimits limits, RuleConfig rules) {
        this.limits = limits;
        this.rules = rules;
    }

    @Override
    public ParseResult parse(SourceSession session, RuleConsumer consumer) {
        TextSource.read(session, limits, text -> text.startsWith("#"), line -> parseLine(line, consumer));
        return ParseResult.COMPLETE;
    }

    private void parseLine(SourceLine line, RuleConsumer consumer) {
        String stripped = line.text().strip();
        if (stripped.isEmpty() || stripped.startsWith("#")) {
            return;
        }
        String text = TextSource.ruleText(line, rules.minLength(), rules.maxLength());
        ParsedDirective directive = scanDirective(text, line);
        validateSelector(directive.pattern(), line);
        switch (directive.command()) {
            case "address" -> parseAddress(directive, text, consumer, line);
            case "nameserver", "domain-rules" -> consumer.accept(opaque(text, directive.pattern()));
            default -> throw failure(line, "SmartDNS 规则集不接受指令: " + directive.command());
        }
    }

    private static void parseAddress(
            ParsedDirective directive,
            String text,
            RuleConsumer consumer,
            SourceLine line) {
        List<String> values = splitValues(directive.value(), line);
        String value = values.getFirst();
        DnsResponse response = values.size() == 1 ? switch (value) {
            case "#", "#4", "#6" -> DnsResponse.SOA;
            case "-", "-4", "-6" -> DnsResponse.IGNORE;
            default -> DnsResponse.ADDRESS;
        } : DnsResponse.ADDRESS;
        List<IpAddress> addresses = response == DnsResponse.ADDRESS
                ? values.stream().map(IpAddress::parse).toList() : List.of();
        Set<IpFamily> families = response == DnsResponse.ADDRESS
                ? addresses.stream().map(IpAddress::family).collect(java.util.stream.Collectors.toUnmodifiableSet())
                : value.endsWith("4") ? Set.of(IpFamily.IPV4) : value.endsWith("6") ? Set.of(IpFamily.IPV6)
                : Set.of(IpFamily.IPV4, IpFamily.IPV6);
        if (directive.pattern().equals(".") || directive.pattern().startsWith("domain-set:")) {
            consumer.accept(opaque(text, directive.pattern()));
            return;
        }
        consumer.accept(new DnsAddressRule(RuleType.SMARTDNS, parsePattern(directive.pattern(), line),
                response, families, addresses));
    }

    private static ParsedDirective scanDirective(String text, SourceLine line) {
        int separator = firstWhitespace(text);
        if (separator < 0) {
            throw failure(line, "SmartDNS 指令缺少参数");
        }
        String command = text.substring(0, separator);
        int start = skipWhitespace(text, separator);
        if (start >= text.length() || text.charAt(start) != '/') {
            throw failure(line, "SmartDNS 指令缺少 /domain/ 区段");
        }
        StringBuilder pattern = new StringBuilder();
        boolean escaped = false;
        int index = start + 1;
        for (; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) {
                pattern.append(character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '/') {
                break;
            } else {
                pattern.append(character);
            }
        }
        if (escaped || index >= text.length()) {
            throw failure(line, "SmartDNS /domain/ 区段未闭合");
        }
        String value = text.substring(index + 1).strip();
        if (pattern.isEmpty() || value.isEmpty()) {
            throw failure(line, "SmartDNS 域名或规则值为空");
        }
        return new ParsedDirective(command, pattern.toString(), value);
    }

    private static void validateSelector(String value, SourceLine line) {
        if (value.equals(".")) {
            return;
        }
        if (value.startsWith("domain-set:")) {
            if (value.substring("domain-set:".length()).isBlank()
                    || value.chars().anyMatch(Character::isWhitespace)) {
                throw failure(line, "SmartDNS domain-set 名称非法");
            }
            return;
        }
        parsePattern(value, line);
    }

    private static DomainPattern parsePattern(String value, SourceLine line) {
        if (value.equals(".") || value.startsWith("domain-set:")) {
            throw failure(line, "SmartDNS 全局规则或 domain-set 不能安全建模");
        }
        int wildcard = value.indexOf('*');
        boolean validWildcard = wildcard < 0 || value.startsWith("*.") || value.startsWith("*-");
        if (!validWildcard || wildcard >= 0 && value.indexOf('*', wildcard + 1) >= 0) {
            throw failure(line, "SmartDNS 通配标记仅允许出现在域名开头");
        }
        if (value.startsWith("-.")) {
            return new ExactDomain(new DomainName(value.substring(2)));
        }
        if (value.startsWith("*.")) {
            DomainName suffix = new DomainName(value.substring(2));
            return new Subdomain(suffix);
        }
        if (value.startsWith("*-")) {
            new DomainName(value.substring(2));
            return new WildcardDomain(value, WildcardSyntax.SMARTDNS);
        }
        return new SuffixDomain(new DomainName(value));
    }

    private static List<String> splitValues(String text, SourceLine line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) {
                current.append(character);
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '"') {
                quoted = !quoted;
            } else if (character == ',' && !quoted) {
                addValue(values, current, line);
            } else {
                current.append(character);
            }
        }
        if (escaped || quoted) {
            throw failure(line, "SmartDNS 地址值包含未闭合引号或转义");
        }
        addValue(values, current, line);
        return List.copyOf(values);
    }

    private static void addValue(List<String> values, StringBuilder current, SourceLine line) {
        String value = current.toString().strip();
        if (value.isEmpty()) {
            throw failure(line, "SmartDNS 地址列表包含空值");
        }
        values.add(value);
        current.setLength(0);
    }

    private static OpaqueRule opaque(String text, String pattern) {
        DomainEnvelope envelope = pattern.startsWith("-.") ? DomainEnvelope.EXACT
                : pattern.equals(".") || pattern.startsWith("domain-set:") || pattern.indexOf('*') >= 0
                        ? DomainEnvelope.UNKNOWN : DomainEnvelope.SUFFIX;
        return new OpaqueRule(RuleType.SMARTDNS, RuleDialect.NONE, envelope, text);
    }

    private static int firstWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String value, int start) {
        int index = start;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static RuleProcessingException failure(SourceLine line, String message) {
        return new RuleProcessingException(message);
    }

    private record ParsedDirective(String command, String pattern, String value) {
    }
}
