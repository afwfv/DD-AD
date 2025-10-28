package org.fordes.adfs.handler.rule;

import lombok.extern.slf4j.Slf4j;
import org.fordes.adfs.enums.RuleSet;
import org.fordes.adfs.model.Rule;
import org.fordes.adfs.util.Util;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.fordes.adfs.constant.Constants.*;

/**
 * @author fordes123 on 2024/5/27
 */
@Slf4j
@Component
public final class HostsHandler extends Handler implements InitializingBean {

    @Override
    public Rule parse(String line) {
        Map.Entry<String, String> entry = Util.parseHosts(line);
        if (entry == null || Objects.equals(entry.getKey(), entry.getValue())) {
            return Rule.EMPTY;
        }

        Rule rule = new Rule();
        rule.setSource(RuleSet.HOSTS);
        rule.setOrigin(line);
        rule.setTarget(entry.getValue());
        rule.setMode(LOCAL_IP.contains(entry.getKey()) && !LOCAL_DOMAIN.contains(entry.getValue()) ? Rule.Mode.DENY : Rule.Mode.REWRITE);
        rule.setDest(Rule.Mode.DENY == rule.getMode() ? UNKNOWN_IP : entry.getKey());
        rule.setScope(Rule.Scope.DOMAIN);
        rule.setType(Rule.Type.BASIC);
        return rule;
    }

    @Override
    public String format(Rule rule) {
        if (Rule.Scope.DOMAIN == rule.getScope() &&
                Rule.Type.BASIC == rule.getType() &&
                Rule.Mode.ALLOW != rule.getMode()) {
            return Optional.ofNullable(rule.getDest()).orElse(UNKNOWN_IP) + TAB + rule.getTarget();
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
        this.register(RuleSet.HOSTS, this);
    }
}