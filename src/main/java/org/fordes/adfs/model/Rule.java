package org.fordes.adfs.model;

import lombok.Data;
import org.fordes.adfs.enums.RuleSet;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author fordes123 on 2024/5/27
 */
@Data
public class Rule {
    private RuleSet source;
    private String origin;

    private String target;
    private String dest;

    private Mode mode;
    private Scope scope;
    private Type type;
    private Set<Control> controls = new HashSet<>(Control.values().length, 1.0f);

    public static final Rule EMPTY = new Rule();

    /**
     * 规则控制参数
     */
    public enum Control {
        /**
         * 最高优先级
         */
        IMPORTANT,

        /**
         * 覆盖子域名
         */

        OVERLAY,

        /**
         * 限定符，通常是 ^
         */
        QUALIFIER,

        ;
    }

    /**
     * 规则模式
     */
    public enum Mode {

        /**
         * 阻止
         */
        DENY,

        /**
         * 解除阻止
         */
        ALLOW,

        /**
         * 重写<br/>
         * 通常 hosts规则指向特定ip(非localhost)时即为重写
         */
        REWRITE,

        ;
    }

    /**
     * 规则类型
     */
    public enum Type {
        /**
         * 基本规则，不包含任何控制、匹配符号, 可以转换为 hosts
         */
        BASIC,

        /**
         * 通配规则，仅使用通配符
         */
        WILDCARD,

        /**
         * 其他规则，如使用了正则、高级修饰符号等，这表示目前无法支持
         */
        UNKNOWN,

        ;
    }

    /**
     * 作用域
     */
    public enum Scope {
        /**
         * ipv4或ipv6地址
         */
        HOST,

        /**
         * 域名
         */
        DOMAIN,

        /**
         * 路径、文件等
         */
        PATH,

        ;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof Rule rule) {
            if (Type.UNKNOWN == this.type || Type.UNKNOWN == rule.getType()) {
                return Objects.equals(this.origin, rule.origin);
            }
            return Objects.equals(this.target, rule.target) &&
                    this.mode == rule.mode &&
                    this.scope == rule.scope &&
                    this.type == rule.type;
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (Type.UNKNOWN == this.type) {
            return Objects.hash(this.origin);
        }
        return Objects.hash(getTarget(), getMode(), getScope(), getType());
    }

    @Override
    public String toString() {
        if (Type.UNKNOWN == this.type) {
            return "Rule{" +
                    "origin='" + origin + '\'' +
                    '}';
        }
        return "Rule{" +
                "target='" + target + '\'' +
                ", mode=" + mode +
                ", scope=" + scope +
                ", type=" + type +
                '}';
    }
}