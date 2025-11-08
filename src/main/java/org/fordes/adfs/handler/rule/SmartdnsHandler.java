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
        List<String> data = Util.splitIgnoreBlank(content, Symbol.SLASH);
        if (data.size() == 2) {
            String domain = data.getFirst();
            String control = data.get(1);
            Rule rule = new Rule();
            rule.setOrigin(line);
            rule.setSourceType(RuleSet.EASYLIST);

            switch (control) {
                case Symbol.HASH -> rule.setMode(Rule.Mode.DENY);
                case Symbol.DASH -> rule.setMode(ALLOW);
                default -> {
                    //未知或不支持的控制符 如 #6 #4
                    rule.setType(Rule.Type.UNKNOWN);
                    return rule;
                }
            }

            //仅匹配主域名
            if (domain.startsWith(Symbol.DASH)) {
                domain = domain.substring(0, line.length() - Symbol.DASH.length());
            } else {
                rule.setControls(Set.of(Rule.Control.OVERLAY));
            }

            if (domain.startsWith(Symbol.DOT)) {
                domain = domain.substring(0, line.length() - Symbol.DOT.length());
            }

            rule.setType(domain.startsWith(Symbol.ASTERISK) ? Rule.Type.WILDCARD : Rule.Type.BASIC);
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
            if (RuleSet.SMARTDNS == rule.getSourceType()) {
                return rule.getOrigin();
            }
            return null;
        } else if (rule.getMode() != Rule.Mode.REWRITE && rule.getScope() == Rule.Scope.DOMAIN) {

            switch (rule.getType()) {
                case BASIC -> {
                    return SMARTDNS_HEADER +
                            (!rule.getControls().contains(Rule.Control.OVERLAY) ?
                                    (Symbol.ASTERISK + Symbol.DOT) : Symbol.EMPTY) +
                            rule.getTarget() +
                            Symbol.SLASH +
                            (Rule.Mode.DENY.equals(rule.getMode()) ? Symbol.HASH : Symbol.DASH);
                }
                case WILDCARD -> {
                    String domain = rule.getTarget();
                    if (domain.lastIndexOf(Symbol.ASTERISK) == 0) {
                        return SMARTDNS_HEADER + domain + Symbol.SLASH + Symbol.DASH;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public String commented(String value) {
        return Util.splitIgnoreBlank(value, Symbol.LF).stream()
                .map(e -> Symbol.HASH + Symbol.WHITESPACE + e.trim())
                .collect(Collectors.joining(Symbol.CRLF));
    }

    @Override
    public boolean isComment(String line) {
        return line.startsWith(Symbol.HASH);
    }

    @Override
    public void afterPropertiesSet() {
        this.register(RuleSet.SMARTDNS, this);
    }
}