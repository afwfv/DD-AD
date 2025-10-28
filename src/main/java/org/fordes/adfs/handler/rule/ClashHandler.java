package org.fordes.adfs.handler.rule;

import lombok.extern.slf4j.Slf4j;
import org.fordes.adfs.enums.RuleSet;
import org.fordes.adfs.model.Rule;
import org.fordes.adfs.util.Util;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.fordes.adfs.constant.Constants.*;
import static org.fordes.adfs.constant.RegConstants.PATTERN_DOMAIN;

/**
 * @author fordes123 on 2024/5/27
 */
@Slf4j
@Component
public final class ClashHandler extends Handler implements InitializingBean {

    @Override
    public Rule parse(String line) {
        //跳过文件头
        if (line.startsWith(PAYLOAD)) {
            return Rule.EMPTY;
        }

        Rule rule = new Rule();
        rule.setOrigin(line);
        rule.setSource(RuleSet.EASYLIST);

        //只匹配 domain 规则，ipcidr、classical 规则暂不支持
        String content = (line.startsWith(DASH) ? line.substring(DASH.length()) : line).trim();
        if (content.startsWith(SINGLE_QUOTE)) {
            content = Util.subBetween(line, SINGLE_QUOTE, SINGLE_QUOTE).trim();
        } else if (content.startsWith(QUOTE)) {
            content = Util.subBetween(line, QUOTE, QUOTE).trim();
        }

        //通配符 * 一次只能匹配一级域名，无法转换为easylist
        if (content.startsWith(ASTERISK)) {
            rule.setType(Rule.Type.UNKNOWN);
            return rule;
        }

        //通配符 +
        if (content.startsWith(ADD)) {
            content = content.substring(content.startsWith("+.") ? 2 : 1);
            rule.setControls(Set.of(Rule.Control.OVERLAY));
        }

        //判断是否是domain
        boolean haveAsterisk = content.contains(ASTERISK);
        String temp = haveAsterisk ? content.replace(ASTERISK, A) : content;
        if (PATTERN_DOMAIN.matcher(temp).matches()) {
            rule.setType(haveAsterisk ? Rule.Type.WILDCARD : Rule.Type.BASIC);
        }

        rule.setTarget(content);
        rule.setDest(UNKNOWN_IP);
        rule.setMode(Rule.Mode.DENY);
        rule.setScope(Rule.Scope.DOMAIN);
        if (rule.getType() == null) {
            rule.setType(Rule.Type.UNKNOWN);
        }
        return rule;
    }

    @Override
    public String format(Rule rule) {
        if (Rule.Type.UNKNOWN == rule.getType()) {
            if (RuleSet.CLASH == rule.getSource()) {
                return rule.getOrigin();
            }
            return null;
        } else if (rule.getMode() == Rule.Mode.DENY && rule.getScope() == Rule.Scope.DOMAIN) {
            StringBuilder builder = new StringBuilder();
            builder.append(WHITESPACE).append(WHITESPACE).append(DASH).append(WHITESPACE).append(QUOTE);

            Set<Rule.Control> controls = Optional.ofNullable(rule.getControls()).orElse(Set.of());
            if (controls.contains(Rule.Control.OVERLAY)) {
                builder.append(ADD).append(DOT);
            }
            builder.append(rule.getTarget());
            builder.append(QUOTE);
            return builder.toString();
        }
        return null;
    }

    @Override
    public String headFormat() {
        return PAYLOAD + COLON;
    }

    @Override
    public boolean isComment(String line) {
        return line.startsWith(HASH);
    }

    @Override
    public String commented(String value) {
        return Util.splitIgnoreBlank(value, LF).stream()
                .map(e -> HASH + e.trim())
                .collect(Collectors.joining(CRLF));
    }

    @Override
    public void afterPropertiesSet() {
        this.register(RuleSet.CLASH, this);
    }
}