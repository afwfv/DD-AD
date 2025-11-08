package org.fordes.adfs.handler.rule;

import lombok.extern.slf4j.Slf4j;
import org.fordes.adfs.enums.RuleSet;
import org.fordes.adfs.model.Rule;
import org.fordes.adfs.util.Util;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.fordes.adfs.constant.Constants.*;

/**
 * @author fordes123 on 2024/5/27
 */
@Slf4j
@Component
public final class EasylistHandler extends Handler implements InitializingBean {

    @Override
    public Rule parse(String line) {
        Rule rule = new Rule();
        rule.setOrigin(line);
        rule.setSourceType(RuleSet.EASYLIST);
        rule.setMode(Rule.Mode.DENY);

        if (line.startsWith(DOUBLE_AT)) {
            rule.setMode(Rule.Mode.ALLOW);
            line = line.substring(2);
        }

        int _head = 0;
        if (line.startsWith(Symbol.OR)) {
            _head = Symbol.OR.length();
            rule.getControls().add(Rule.Control.OVERLAY);
        }


        //修饰部分
        int _tail = line.indexOf(Symbol.CARET);
        if (_tail > 0) {
            rule.getControls().add(Rule.Control.QUALIFIER);

            String modify = line.substring(_tail + 1);
            if (!modify.isEmpty()) {
                modify = modify.startsWith(Symbol.DOLLAR) ? modify.substring(1) : modify;
                String[] array = modify.split(Symbol.COMMA);
                if (Arrays.stream(array).allMatch(IMPORTANT::equals)) {
                    rule.getControls().add(Rule.Control.IMPORTANT);
                } else {
                    rule.setType(Rule.Type.UNKNOWN);
                    return rule;
                }
            }
        } else if (line.endsWith(Symbol.DOLLAR + IMPORTANT)) {
            rule.getControls().add(Rule.Control.IMPORTANT);
            _tail = line.length() - (Symbol.DOLLAR.length() + IMPORTANT.length());
        }


        //内容部分
        String content = line.substring(_head, _tail > 0 ? _tail : line.length());

        if (content.startsWith(Symbol.SLASH) && content.endsWith(Symbol.SLASH)) {
            content = content.substring(1, content.length() - 1);
            rule.setType(Rule.Type.UNKNOWN);
        }

        //判断是否为基本或通配规则
        Util.isBaseRule(content, (origin, e) -> {
            if (rule.getType() == null) {
                rule.setType(e);
            }
            rule.setScope(Rule.Scope.DOMAIN);
            rule.setTarget(origin);
            if (Rule.Mode.DENY.equals(rule.getMode())) {
                rule.setDest(UNKNOWN_IP);
            }
        }, e -> {
            Map.Entry<String, String> entry = Util.parseHosts(e);
            if (entry != null) {
                rule.setSourceType(RuleSet.HOSTS);
                rule.setTarget(entry.getValue());
                rule.setMode(LOCAL_IPS.contains(entry.getKey()) && !LOCAL_DOMAINS.contains(entry.getValue()) ? Rule.Mode.DENY : Rule.Mode.REWRITE);
                rule.setDest(Rule.Mode.DENY == rule.getMode() ? UNKNOWN_IP : entry.getKey());
                rule.setScope(Rule.Scope.DOMAIN);
                rule.setType(Rule.Type.BASIC);
            } else {
                rule.setType(Rule.Type.UNKNOWN);
            }
        });
        return rule;
    }

    @Override
    public String format(Rule rule) {
        if (Rule.Type.UNKNOWN != rule.getType() && Rule.Mode.REWRITE != rule.getMode()) {

            StringBuilder builder = new StringBuilder();
            Optional.of(rule.getMode())
                    .filter(Rule.Mode.ALLOW::equals)
                    .ifPresent(m -> builder.append(DOUBLE_AT));

            Optional.of(rule.getControls())
                    .filter(e -> e.contains(Rule.Control.OVERLAY))
                    .ifPresent(c -> builder.append(Symbol.OR));

            builder.append(rule.getTarget());

            Optional.of(rule.getControls())
                    .filter(e -> e.contains(Rule.Control.QUALIFIER))
                    .ifPresent(c -> builder.append(Symbol.CARET));

            Optional.of(rule.getControls())
                    .filter(e -> e.contains(Rule.Control.IMPORTANT))
                    .ifPresent(c -> builder.append(Symbol.DOLLAR).append(IMPORTANT));
            return builder.toString();
        }

        //同源未知规则可直接写出
        if (Rule.Type.UNKNOWN == rule.getType() && RuleSet.EASYLIST == rule.getSourceType()) {
            return rule.getOrigin();
        }
        return null;
    }

    @Override
    public String commented(String value) {
        return Util.splitIgnoreBlank(value, Symbol.LF).stream()
                .map(e -> Symbol.EXCLAMATION + Symbol.WHITESPACE + e.trim())
                .collect(Collectors.joining(Symbol.CRLF));
    }

    @Override
    public void afterPropertiesSet() {
        this.register(RuleSet.EASYLIST, this);
    }

    @Override
    public boolean isComment(String line) {
        return Util.startWithAny(line, Symbol.HASH, Symbol.EXCLAMATION) || Util.between(line, Symbol.LEFT_BRACKETS, Symbol.RIGHT_BRACKETS);
    }
}