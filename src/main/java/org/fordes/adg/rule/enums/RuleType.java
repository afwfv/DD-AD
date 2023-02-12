package org.fordes.adg.rule.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Chengfs on 2022/9/19
 */
@Getter
@AllArgsConstructor
public enum RuleType {

    /**
     * 域名规则，形如 xxx.com、xx.oo.com
     */
    DOMAIN("域名规则", true, null, new String[]{"^([\\w,\\d,-]+\\.)+[\\w,\\d,-]+(\\^$)?$"}, null),

    /**
     * Hosts规则
     */
    HOSTS("Hosts规则", true, null, new String[]{"^\\d+\\.\\d+\\.\\d+\\.\\d+\\s+.*$"}, null),

    /**
     * 正则规则，包含修饰规则
     */
    REGEX("正则规则", true, null, new String[]{},
            new String[]{"[/,#,&,=,:]", "^[\\*,@,\\-,_,\\.,&,\\?]","[\\$][^\\s]", "[\\^][^\\s]"}),


    /**
     * 修饰规则，不被adGuardHome支持
     */
    MODIFY("修饰规则", false, null, null, null)
    ;


    /**
     * 描述
     */
    private final String desc;

    /**
     * 支持性，true则adGuardHome支持
     */
    private final boolean usually;

    /**
     * 识别标识，包含即通过
     */
    private final String[] identify;

    /**
     * 正向 正则，匹配一个即为通过
     */
    private final String[] match;

    /**
     * 排除 正则，全部不匹配即为通过
     */
    private final String[] exclude;
}
