package org.fordes.adfs.handler.rule;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.fordes.adfs.constant.Constants;
import org.fordes.adfs.enums.RuleSet;
import org.fordes.adfs.model.Rule;

import java.util.HashMap;
import java.util.Map;

public abstract sealed class Handler permits EasylistHandler, DnsmasqHandler, ClashHandler,
        SmartdnsHandler, HostsHandler {

    private static final Map<RuleSet, Handler> handlerMap = new HashMap<>(RuleSet.values().length, 1);

    /**
     * 解析规则<br/>
     * 返回 {@link Rule#EMPTY} 即表示解析失败
     *
     * @param line 规则文本
     * @return {@link Rule}
     */
    public abstract @Nonnull Rule parse(String line);

    /**
     * 转换规则<br/>
     *
     * @param rule {@link Rule} null 表示无法转换或失败
     * @return 规则文本
     */
    public abstract @Nullable String format(Rule rule);

    /**
     * 生成注释
     * @param value 目标内容
     * @return  注释
     */
    public abstract String commented(String value);

    /**
     * 某些规则格式拥有固定的头部内容，可实现此方法以返回
     */
    public String headFormat() {
        return Constants.Symbol.EMPTY;
    }

    /**
     * 某些规则格式拥有固定的尾部内容，可实现此方法以返回
     */
    public String tailFormat() {
        return Constants.Symbol.EMPTY;
    }

    /**
     * 验证规则文本是否为注释<br/>
     * 并不强制子类实现此方法，且不是注释不表示此规则有效
     *
     * @param line 规则文本
     * @return 默认 false
     */
    public boolean isComment(String line) {
        return false;
    }

    /**
     * 根据 RuleSet 获取 Handler
     *
     * @param type {@link RuleSet}
     * @return {@link Handler}
     */
    public static Handler getHandler(RuleSet type) {
        return handlerMap.get(type);
    }

    protected void register(RuleSet type, Handler handler) {
        handlerMap.put(type, handler);
    }
}
