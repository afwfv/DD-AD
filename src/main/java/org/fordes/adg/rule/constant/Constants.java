package org.fordes.adg.rule.constant;

import java.io.File;

public class Constants {

    public static final String ROOT_PATH = System.getProperty("user.dir");

    public static final String LOCAL_RULE_SUFFIX = ROOT_PATH + File.separator + "rule";

    /**
     * 基本的有效性检测正则，!开头，[]包裹，非特殊标记的#号开头均视为无效规则
     */
    public static final String EFFICIENT_REGEX = "^!|^#[^#,^@,^%,^\\$]|^\\[.*\\]$";

    /**
     * 去除首尾基础修饰符号 的正则，方便对规则进行分类
     * 包含：@@、||、@@||、/ 开头，$important、/ 结尾
     */
    public static final String BASIC_MODIFY_REGEX = "^@@\\|\\||^\\|\\||^@@|(\\^)?\\$important$|\\s#[^#]*$|\\^$";

    /**
     * 分段规则来源
     */
    public static final String PART_TEMPLATE = "! 👇This Part Merge from: {}\n";

    /**
     * 日期占位符
     */
    public static final CharSequence HEADER_DATE = "${date}";

    /**
     * 名称占位符
     */
    public static final CharSequence HEADER_NAME = "${name}";
}
