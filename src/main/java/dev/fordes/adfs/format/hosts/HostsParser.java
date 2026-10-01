package dev.fordes.adfs.format.hosts;

import java.util.List;

import dev.fordes.adfs.config.EffectiveConfig.InputLimits;
import dev.fordes.adfs.config.EffectiveConfig.RuleConfig;
import dev.fordes.adfs.error.RuleProcessingException;
import dev.fordes.adfs.format.LineTokens;
import dev.fordes.adfs.format.ParseResult;
import dev.fordes.adfs.format.RuleConsumer;
import dev.fordes.adfs.format.RuleParser;
import dev.fordes.adfs.format.TextSource;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.DomainRule;
import dev.fordes.adfs.rule.model.ExactDomain;
import dev.fordes.adfs.rule.model.HostMappingRule;
import dev.fordes.adfs.rule.model.IpAddress;
import dev.fordes.adfs.rule.model.RuleAction;
import dev.fordes.adfs.source.SourceLine;
import dev.fordes.adfs.source.SourceSession;

public final class HostsParser implements RuleParser {

    private final InputLimits limits;
    private final RuleConfig rules;

    public HostsParser(InputLimits limits, RuleConfig rules) {
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
        int comment = stripped.indexOf('#');
        String uncommented = comment < 0 ? stripped : stripped.substring(0, comment).stripTrailing();
        if (uncommented.isEmpty()) {
            return;
        }
        String text = TextSource.ruleText(new SourceLine(line.source(), line.lineNumber(), uncommented),
                rules.minLength(), rules.maxLength());
        List<String> tokens = LineTokens.beforeComment(text);
        if (tokens.size() < 2) {
            throw new RuleProcessingException("Hosts 记录至少需要地址和主机名");
        }
        IpAddress address = IpAddress.parse(tokens.getFirst());
        List<DomainName> domains = tokens.subList(1, tokens.size()).stream().map(DomainName::new).toList();
        for (DomainName domain : domains) {
            if (address.isBlockingTarget()) {
                consumer.accept(new DomainRule(new ExactDomain(domain), RuleAction.BLOCK));
            } else {
                consumer.accept(new HostMappingRule(address, domain));
            }
        }
    }
}
