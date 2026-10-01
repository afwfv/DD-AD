package dev.fordes.adfs.format.mihomo;

import dev.fordes.adfs.config.EffectiveConfig.InputLimits;
import dev.fordes.adfs.config.EffectiveConfig.RuleConfig;
import dev.fordes.adfs.format.RuleConsumer;
import dev.fordes.adfs.format.ParseResult;
import dev.fordes.adfs.format.RuleParser;
import dev.fordes.adfs.format.TextSource;
import dev.fordes.adfs.rule.model.IpCidr;
import dev.fordes.adfs.rule.model.IpCidrRule;
import dev.fordes.adfs.rule.model.RuleAction;
import dev.fordes.adfs.source.SourceLine;
import dev.fordes.adfs.source.SourceSession;

public final class MihomoIpCidrParser implements RuleParser {

    private final InputLimits limits;
    private final RuleConfig rules;
    private final MihomoYaml container = new MihomoYaml();

    public MihomoIpCidrParser(InputLimits limits, RuleConfig rules) {
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
        IpCidr cidr = IpCidr.parse(text);
        consumer.accept(new IpCidrRule(cidr.network(), cidr.prefixLength(), RuleAction.BLOCK));
    }
}
