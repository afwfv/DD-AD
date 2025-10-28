package org.fordes.adfs.util;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adfs.model.Rule;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.fordes.adfs.constant.Constants.*;
import static org.fordes.adfs.constant.RegConstants.*;

/**
 * @author fordes123 on 2022/9/19
 */
@Slf4j
public class Util {

    /**
     * 给定字符串是否以特定前缀开始
     *
     * @param str      给定字符串
     * @param prefixes 前缀
     * @return 给定字符串是否以特定前缀开始
     */
    public static boolean startWithAny(String str, String... prefixes) {
        if (!StringUtils.hasText(str) || ObjectUtils.isEmpty(prefixes)) {
            return false;
        }
        return Arrays.stream(prefixes).anyMatch(str::startsWith);
    }

    /**
     * 给定字符串是否以特定字符串开始和结束
     *
     * @param str   给定字符串
     * @param start 开始字符串
     * @param end   结束字符串
     * @return 给定字符串是否以特定字符串开始和结束
     */
    public static boolean between(String str, String start, String end) {
        if (StringUtils.hasLength(str) && StringUtils.hasLength(start) && StringUtils.hasLength(end)) {
            return str.startsWith(start) && str.endsWith(end);
        }
        return false;
    }

    /**
     * 截取分隔字符串之前的字符串，不包括分隔字符串<br/>
     * 截取不到时返回空串
     *
     * @param str    被截取的字符串
     * @param flag   分隔字符串
     * @param isLast 是否是最后一个
     * @return 分隔字符串之前的字符串
     */
    public static String subBefore(String str, String flag, boolean isLast) {
        if (StringUtils.hasLength(str) && StringUtils.hasLength(flag)) {
            int index = isLast ? str.lastIndexOf(flag) : str.indexOf(flag);
            if (index >= 0) {
                return str.substring(0, index);
            }
        }
        return EMPTY;
    }

    /**
     * 截取分隔字符串之后的字符串，不包括分隔字符串<br/>
     * 截取不到时返回空串
     *
     * @param content 被截取的字符串
     * @param flag    分隔字符串
     * @param isLast  是否是最后一个
     * @return 分隔字符串之后的字符串
     */
    public static String subAfter(String content, String flag, boolean isLast) {
        if (StringUtils.hasLength(content) && StringUtils.hasLength(flag)) {
            int index = isLast ? content.lastIndexOf(flag) : content.indexOf(flag);
            if (index >= 0) {
                return content.substring(index + flag.length());
            }
        }
        return EMPTY;
    }

    /**
     * 截取分隔字符串之间的字符串，不包括分隔字符串<br/>
     * 截取不到时返回空串
     *
     * @param content 被截取的字符串
     * @param start   开始分隔字符串
     * @param end     结束分隔字符串
     * @return 分隔字符串之间的字符串
     */
    public static String subBetween(String content, String start, String end) {
        if (StringUtils.hasLength(content) && StringUtils.hasLength(start) && StringUtils.hasLength(end)) {
            int startIndex = content.indexOf(start);
            int endIndex = content.lastIndexOf(end);
            if (startIndex >= 0 && endIndex > 0 && startIndex < endIndex) {
                return content.substring(startIndex + start.length(), endIndex);
            }
        }
        return EMPTY;
    }

    /**
     * 切分字符串并移除空项
     *
     * @param str  待切分字符串
     * @param flag 分隔符
     * @return 切分后的字符串
     */
    public static List<String> splitIgnoreBlank(String str, String flag) {
        if (!StringUtils.hasLength(str) || !StringUtils.hasLength(flag)) {
            return List.of();
        }
        return Arrays.stream(str.split(flag))
                .filter(e -> !e.isBlank())
                .toList();
    }

    /**
     * 给定字符串是等于任一字符串
     *
     * @param str    给定字符串
     * @param values 任意字符串
     * @return 给定字符串是等于任一字符串
     */
    public static boolean equalsAny(String str, String... values) {
        if (!StringUtils.hasLength(str) || ObjectUtils.isEmpty(values)) {
            return false;
        }
        return Arrays.asList(values).contains(str);
    }

    /**
     * 解析hosts规则，如不是则返回null
     *
     * @param content 待解析字符串
     * @return {@link Map.Entry} key:ip, value:域名
     */
    public static @Nullable Map.Entry<String, String> parseHosts(String content) {
        if (content.contains(TAB)) {
            content = content.replace(TAB, WHITESPACE);
        }
        List<String> list = splitIgnoreBlank(content, WHITESPACE);
        if (list.size() == 2) {
            String ip = list.get(0).trim();
            String domain = list.get(1).trim();

            if (PATTERN_IP.matcher(ip).matches() && PATTERN_DOMAIN.matcher(domain).matches()) {
                return Map.entry(ip, domain);
            }
        }
        return null;
    }

    /**
     * 休眠线程，忽略中断异常
     *
     * @param millis 休眠时间，毫秒
     */
    public static void sleep(long millis) {
        if (millis > 0L) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * 转换相对路径为绝对路径
     *
     * @param path 路径
     * @return 规范化后的路径
     */
    public static String normalizePath(@Nonnull String path) {

        boolean isAbsPath = '/' == path.charAt(0) || PATTERN_PATH_ABSOLUTE.matcher(path).matches();

        if (!isAbsPath) {
            if (path.startsWith(DOT)) {
                path = path.substring(1);
            }
            if (path.startsWith(FILE_SEPARATOR)) {
                path = path.substring(FILE_SEPARATOR.length());
            }
            path = ROOT_PATH + FILE_SEPARATOR + path;
        }
        return path;
    }


    public static void isBaseRule(String content, BiConsumer<String, Rule.Type> ifPresent, Consumer<String> orElse) {
        String temp = content;
        if (temp.contains(ASTERISK)) {
            temp = content.replace(ASTERISK, A);
        }

        if (temp.startsWith(DOT)) {
            temp = temp.substring(1);
        }

        if (temp.endsWith(DOT)) {
            temp = temp.substring(0, temp.length() - 1);
        }

        if (PATTERN_DOMAIN.matcher(temp).matches()) {
            ifPresent.accept(content, content.equals(temp) ? Rule.Type.BASIC : Rule.Type.WILDCARD);
        } else if (DOMAIN_PART.matcher(temp).matches()) {
            ifPresent.accept(content, Rule.Type.WILDCARD);
        } else {
            orElse.accept(content);
        }
    }
}
