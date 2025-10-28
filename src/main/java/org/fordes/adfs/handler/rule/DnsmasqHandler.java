package org.fordes.adfs.handler.rule;

import org.fordes.adfs.enums.RuleSet;
import org.fordes.adfs.model.Rule;
import org.fordes.adfs.util.Util;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static org.fordes.adfs.constant.Constants.*;

/**
 * @author fordes123 on 2024/5/27
 */
@Component
public final class DnsmasqHandler extends Handler implements InitializingBean {

    @Override
    public Rule parse(String line) {

        String content = Util.subAfter(line, DNSMASQ_HEADER, true);
        List<String> data = Util.splitIgnoreBlank(content, SLASH);
        if (data.size() == 1 || data.size() == 2) {
            String domain = data.getFirst();
            String ip = data.size() > 1 ? data.get(1) : null;

            Rule rule = new Rule();
            rule.setSource(RuleSet.DNSMASQ);
            rule.setOrigin(line);
            rule.setTarget(domain);
            rule.setDest(ip);
            rule.setScope(Rule.Scope.DOMAIN);
            rule.setType(Rule.Type.BASIC);
            rule.setMode((ip == null || LOCAL_IP.contains(ip)) ? Rule.Mode.DENY : Rule.Mode.REWRITE);
            return rule;
        }
        return Rule.EMPTY;
    }

    @Override
    public String format(Rule rule) {
        if (Rule.Scope.DOMAIN == rule.getScope() &&
                Rule.Type.BASIC == rule.getType() &&
                Rule.Mode.ALLOW != rule.getMode()) {

            StringBuilder builder = new StringBuilder();
            builder.append(DNSMASQ_HEADER)
                    .append(rule.getTarget());
            if (Rule.Mode.REWRITE.equals(rule.getMode())) {
                builder.append(SLASH)
                        .append(rule.getDest());
            }
            builder.append(SLASH);
            return builder.toString();
        }
        return null;
    }

    @Override
    public String commented(String value) {
        return Util.splitIgnoreBlank(value, LF).stream()
                .map(e -> HASH + WHITESPACE + e.trim())
                .collect(Collectors.joining(CRLF));
    }

    @Override
    public boolean isComment(String line) {
        return line.startsWith(HASH);
    }

    @Override
    public void afterPropertiesSet() {
        this.register(RuleSet.DNSMASQ, this);
    }
}