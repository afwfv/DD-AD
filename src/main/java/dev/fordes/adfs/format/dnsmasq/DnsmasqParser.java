package dev.fordes.adfs.format.dnsmasq;

import java.util.ArrayList;
import java.util.Arrays;
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
import dev.fordes.adfs.rule.model.IpAddress;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.RuleEntry;
import dev.fordes.adfs.rule.model.SuffixDomain;
import dev.fordes.adfs.source.SourceLine;
import dev.fordes.adfs.source.SourceSession;

public final class DnsmasqParser implements RuleParser {

    private static final String ADDRESS = "address=";
    private static final String LONG_ADDRESS = "--address=";
    private static final String SERVER = "server=";
    private static final String LONG_SERVER = "--server=";
    private final InputLimits limits;
    private final RuleConfig rules;

    public DnsmasqParser(InputLimits limits, RuleConfig rules) {
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
        String body;
        if (text.startsWith(ADDRESS)) {
            body = text.substring(ADDRESS.length());
        } else if (text.startsWith(LONG_ADDRESS)) {
            body = text.substring(LONG_ADDRESS.length());
        } else if (text.startsWith(SERVER) || text.startsWith(LONG_SERVER)) {
            DnsmasqSyntax.server(text.substring(text.indexOf('=') + 1));
            consumer.accept(new OpaqueRule(RuleType.DNSMASQ, RuleDialect.NONE, DomainEnvelope.UNKNOWN, text));
            return;
        } else {
            throw new RuleProcessingException("不接受非规则型 dnsmasq 配置");
        }
        if (!body.startsWith("/")) {
            throw new RuleProcessingException("dnsmasq address 缺少域名段");
        }
        List<String> segments = Arrays.asList(body.substring(1).split("/", -1));
        if (segments.size() < 2) {
            throw new RuleProcessingException("dnsmasq address 结构非法");
        }
        String target = segments.getLast();
        IpAddress address = target.isEmpty() || target.equals("#") ? null : IpAddress.parse(target);
        DnsResponse response = target.isEmpty() ? DnsResponse.NXDOMAIN
                : target.equals("#") || target.equals("0.0.0.0") || target.equals("::")
                        ? DnsResponse.NULL_ADDRESS : DnsResponse.ADDRESS;
        Set<IpFamily> families = address == null ? Set.of(IpFamily.IPV4, IpFamily.IPV6) : Set.of(address.family());
        List<RuleEntry> entries = new ArrayList<>();
        for (String domain : segments.subList(0, segments.size() - 1)) {
            DnsmasqSyntax.selector(domain);
            if (domain.isEmpty() || domain.equals("#") || domain.indexOf('*') >= 0) {
                entries.add(new OpaqueRule(RuleType.DNSMASQ, RuleDialect.NONE,
                        DomainEnvelope.UNKNOWN, "address=/" + domain + "/" + target));
                continue;
            }
            DomainPattern pattern = new SuffixDomain(new DomainName(domain.startsWith(".") ? domain.substring(1) : domain));
            entries.add(new DnsAddressRule(RuleType.DNSMASQ, pattern, response, families,
                    response == DnsResponse.ADDRESS ? List.of(address) : List.of()));
        }
        entries.forEach(consumer::accept);
    }
}
