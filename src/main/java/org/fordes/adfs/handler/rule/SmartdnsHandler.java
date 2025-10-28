package org.fordes.adfs.handler.rule;

import org.fordes.adfs.enums.RuleSet;
import org.fordes.adfs.model.Rule;
import org.fordes.adfs.util.Util;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.fordes.adfs.constant.Constants.*;
import static org.fordes.adfs.model.Rule.Mode.ALLOW;

/**
 * @author fordes123 on 2024/5/27
 */
@Component
public final class SmartdnsHandler extends Handler implements InitializingBean {

    @Override
    public Rule parse(String line) {

        String content = Util.subAfter(line, SMARTDNS_HEADER, true);
        List<String> data = Util.splitIgnoreBlank(content, SLASH);
        if (data.size() == 2) {
            String domain = data.getFirst();
            String control = data.get(1);
            Rule rule = new Rule();
            rule.setOrigin(line);
            rule.setSource(RuleSet.EASYLIST);

            switch (control) {
                case HASH -> rule.setMode(Rule.Mode.DENY);
                case DASH -> rule.setMode(ALLOW);
                default -> {
                    //未知或不支持的控制符 如 #6 #4
                    rule.setType(Rule.Type.UNKNOWN);
                    return rule;
                }
            }

            //仅匹配主域名
            if (domain.startsWith(DASH)) {
                domain = domain.substring(0, line.length() - DASH.length());
            } else {
                rule.setControls(Set.of(Rule.Control.OVERLAY));
            }

            if (domain.startsWith(DOT)) {
                domain = domain.substring(0, line.length() - DOT.length());
            }

            rule.setType(domain.startsWith(ASTERISK) ? Rule.Type.WILDCARD : Rule.Type.BASIC);
            rule.setTarget(domain);
            rule.setDest(UNKNOWN_IP);
            rule.setScope(Rule.Scope.DOMAIN);
            return rule;
        }
        return Rule.EMPTY;
    }

    @Override
    public String format(Rule rule) {
        if (Rule.Type.UNKNOWN == rule.getType()) {
            if (RuleSet.SMARTDNS == rule.getSource()) {
                return rule.getOrigin();
            }
            return null;
        } else if (rule.getMode() != Rule.Mode.REWRITE && rule.getScope() == Rule.Scope.DOMAIN) {

            switch (rule.getType()) {
                case BASIC -> {
                    return SMARTDNS_HEADER +
                            (!rule.getControls().contains(Rule.Control.OVERLAY) ?
                                    (ASTERISK + DOT) : EMPTY) +
                            rule.getTarget() +
                            SLASH +
                            (Rule.Mode.DENY.equals(rule.getMode()) ? HASH : DASH);
                }
                case WILDCARD -> {
                    String domain = rule.getTarget();
                    if (domain.lastIndexOf(ASTERISK) == 0) {
                        return SMARTDNS_HEADER + domain + SLASH + DASH;
                    }
                }
            }
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
        this.register(RuleSet.SMARTDNS, this);
    }
}