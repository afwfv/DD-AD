package dev.fordes.adfs.format.dns;

import java.util.List;
import java.util.Set;

import dev.fordes.adfs.config.EffectiveConfig.InputLimits;
import dev.fordes.adfs.config.EffectiveConfig.RuleConfig;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.format.LineTokens;
import dev.fordes.adfs.format.ParseResult;
import dev.fordes.adfs.format.RuleConsumer;
import dev.fordes.adfs.format.RuleParser;
import dev.fordes.adfs.format.TextSource;
import dev.fordes.adfs.format.adblock.AdblockSyntax;
import dev.fordes.adfs.rule.model.DomainEnvelope;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.DomainRule;
import dev.fordes.adfs.rule.model.ExactDomain;
import dev.fordes.adfs.rule.model.HostMappingRule;
import dev.fordes.adfs.rule.model.IpAddress;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.RuleAction;
import dev.fordes.adfs.rule.model.SuffixDomain;
import dev.fordes.adfs.source.SourceLine;
import dev.fordes.adfs.source.SourceSession;

public final class DnsParser implements RuleParser {

    private static final Set<String> VALUE_OPTIONS = Set.of("client", "denyallow", "dnstype", "dnsrewrite", "ctag");
    private static final Set<String> FLAG_OPTIONS = Set.of("important", "badfilter");

    private final InputLimits limits;
    private final RuleConfig rules;

    public DnsParser(InputLimits limits, RuleConfig rules) {
        this.limits = limits;
        this.rules = rules;
    }

    @Override
    public ParseResult parse(SourceSession session, RuleConsumer consumer) {
        TextSource.read(session, limits,
                text -> text.startsWith("#") || text.startsWith("!") || text.startsWith("["), line -> parseLine(line, consumer));
        return ParseResult.COMPLETE;
    }

    private void parseLine(SourceLine line, RuleConsumer consumer) {
        String stripped = line.text().strip();
        if (stripped.isEmpty() || stripped.startsWith("!") || stripped.startsWith("#")
                || stripped.startsWith("[")) {
            return;
        }
        List<String> tokens = LineTokens.beforeComment(stripped);
        if (tokens.size() >= 2 && looksLikeAddress(tokens.getFirst())) {
            int comment = stripped.indexOf('#');
            String uncommented = comment < 0 ? stripped : stripped.substring(0, comment).stripTrailing();
            String text = TextSource.ruleText(new SourceLine(line.source(), line.lineNumber(), uncommented),
                    rules.minLength(), rules.maxLength());
            tokens = LineTokens.beforeComment(text);
            IpAddress address = IpAddress.parse(tokens.getFirst());
            List<DomainName> domains = tokens.subList(1, tokens.size()).stream().map(DomainName::new).toList();
            for (DomainName domain : domains) {
                consumer.accept(address.isBlockingTarget()
                        ? new DomainRule(new ExactDomain(domain), RuleAction.BLOCK)
                        : new HostMappingRule(address, domain));
            }
            return;
        }
        String uncommented = stripDomainsOnlyComment(stripped);
        String text = TextSource.ruleText(new SourceLine(line.source(), line.lineNumber(), uncommented),
                rules.minLength(), rules.maxLength());
        boolean allow = text.startsWith("@@");
        String body = allow ? text.substring(2) : text;
        int separator = AdblockSyntax.optionSeparator(body);
        if (separator >= 0) {
            String optionText = body.substring(separator + 1);
            if (optionText.isEmpty()) {
                throw new RuleProcessingException("DNS 选项列表为空");
            }
            for (String option : AdblockSyntax.options(optionText)) {
                int equals = option.indexOf('=');
                String name = equals < 0 ? option : option.substring(0, equals);
                if (FLAG_OPTIONS.contains(name)) {
                    if (equals >= 0) {
                        throw new RuleProcessingException("DNS 标志选项不能携带参数: " + name);
                    }
                } else if (!VALUE_OPTIONS.contains(name) || equals < 0 || equals == option.length() - 1) {
                    throw new RuleProcessingException("DNS 选项不支持或缺少参数: " + name);
                }
            }
            consumer.accept(new OpaqueRule(RuleType.DNS, RuleDialect.ADGUARD, DomainEnvelope.UNKNOWN, text));
            return;
        }
        if (body.startsWith("/") && body.endsWith("/") && body.length() > 2 || body.indexOf('*') >= 0) {
            consumer.accept(new OpaqueRule(RuleType.DNS, RuleDialect.ADGUARD, DomainEnvelope.UNKNOWN, text));
            return;
        }
        if (body.startsWith("|") && !body.startsWith("||") && body.endsWith("|") && body.length() > 2) {
            consumer.accept(new DomainRule(new ExactDomain(new DomainName(body.substring(1, body.length() - 1))),
                    allow ? RuleAction.ALLOW : RuleAction.BLOCK));
            return;
        }
        if (body.startsWith("||") && body.endsWith("^")) {
            DomainName domain = new DomainName(body.substring(2, body.length() - 1));
            consumer.accept(new DomainRule(new SuffixDomain(domain), allow ? RuleAction.ALLOW : RuleAction.BLOCK));
            return;
        }
        if (!containsSyntax(body)) {
            consumer.accept(new DomainRule(new ExactDomain(new DomainName(body)),
                    allow ? RuleAction.ALLOW : RuleAction.BLOCK));
            return;
        }
        throw new RuleProcessingException("DNS/AdGuard 规则超出当前语义子集");
    }

    private static boolean looksLikeAddress(String value) {
        return value.indexOf(':') >= 0 || value.chars().allMatch(character -> Character.isDigit(character) || character == '.');
    }

    private static String stripDomainsOnlyComment(String text) {
        int comment = text.indexOf('#');
        if (comment < 0) {
            return text;
        }
        String domain = text.substring(0, comment).stripTrailing();
        return domain.isEmpty() || containsSyntax(domain) ? text : domain;
    }

    private static boolean containsSyntax(String value) {
        return value.indexOf('*') >= 0 || value.indexOf('/') >= 0 || value.indexOf('$') >= 0 || value.indexOf('|') >= 0;
    }
}
