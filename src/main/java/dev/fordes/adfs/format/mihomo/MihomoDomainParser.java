package dev.fordes.adfs.format.mihomo;

import dev.fordes.adfs.config.EffectiveConfig.InputLimits;
import dev.fordes.adfs.config.EffectiveConfig.RuleConfig;
import dev.fordes.adfs.config.RuleDialect;
import dev.fordes.adfs.config.RuleType;
import dev.fordes.adfs.format.RuleConsumer;
import dev.fordes.adfs.format.ParseResult;
import dev.fordes.adfs.format.RuleParser;
import dev.fordes.adfs.format.TextSource;
import dev.fordes.adfs.rule.model.DomainEnvelope;
import dev.fordes.adfs.rule.model.DomainName;
import dev.fordes.adfs.rule.model.DomainRule;
import dev.fordes.adfs.rule.model.ExactDomain;
import dev.fordes.adfs.rule.model.OpaqueRule;
import dev.fordes.adfs.rule.model.RuleAction;
import dev.fordes.adfs.rule.model.SuffixDomain;
import dev.fordes.adfs.rule.model.Subdomain;
import dev.fordes.adfs.rule.model.WildcardSyntax;
import dev.fordes.adfs.rule.model.WildcardDomain;
import dev.fordes.adfs.source.SourceLine;
import dev.fordes.adfs.source.SourceSession;

public final class MihomoDomainParser implements RuleParser {

    private final InputLimits limits;
    private final RuleConfig rules;
    private final MihomoYaml container = new MihomoYaml();

    public MihomoDomainParser(InputLimits limits, RuleConfig rules) {
        this.limits = limits;
        this.rules = rules;
    }

    @Override
    public ParseResult parse(SourceSession session, RuleConsumer consumer) {
        TextSource.read(session, limits, text -> text.startsWith("#"), line -> parseLine(line, consumer));
        return ParseResult.COMPLETE;
    }

    private void parseLine(SourceLine line, RuleConsumer consumer) {
        String value = container.read(line);
        if (value == null) {
            return;
        }
        value = TextSource.ruleText(new SourceLine(line.source(), line.lineNumber(), value),
                rules.minLength(), rules.maxLength());
        if (value.indexOf('*') >= 0) {
            consumer.accept(new DomainRule(new WildcardDomain(value, WildcardSyntax.MIHOMO_DOMAIN), RuleAction.BLOCK));
        } else if (value.startsWith("+.")) {
            consumer.accept(new DomainRule(new SuffixDomain(new DomainName(value.substring(2))), RuleAction.BLOCK));
        } else if (value.startsWith(".")) {
            consumer.accept(new DomainRule(new Subdomain(new DomainName(value.substring(1))), RuleAction.BLOCK));
        } else {
            consumer.accept(new DomainRule(new ExactDomain(new DomainName(value)), RuleAction.BLOCK));
        }
    }

}
