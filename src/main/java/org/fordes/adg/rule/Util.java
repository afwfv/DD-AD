package org.fordes.adg.rule;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.fordes.adg.rule.enums.RuleType;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static cn.hutool.core.thread.ThreadUtil.sleep;

/**
 * @author Chengfs on 2022/9/19
 */
@Slf4j
public class Util {

    /**
     * 加锁将集合按行写入文件
     *
     * @param file    目标文件
     * @param content 内容集合
     */
    public static void writeToFile(File file, Collection<String> content, String ruleUrl) {
        if (CollUtil.isNotEmpty(content)) {
            try (RandomAccessFile accessFile = new RandomAccessFile(file, "rw");
                 FileChannel channel = accessFile.getChannel()) {
                //加锁写入文件，如获取不到锁则休眠
                FileLock fileLock = null;
                while (true) {
                    try {
                        channel.tryLock();
                        break;
                    } catch (Exception e) {
                        sleep(1000);
                    }
                }
                accessFile.seek(accessFile.length());
                accessFile.write(StrUtil.format(Constant.COMMENT_TEMPLATE, ruleUrl).getBytes(StandardCharsets.UTF_8));
                accessFile.write((CollUtil.join(content, StrUtil.CRLF)).getBytes(StandardCharsets.UTF_8));
                accessFile.write(StrUtil.CRLF.getBytes(StandardCharsets.UTF_8));
                accessFile.write(StrUtil.CRLF.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ioException) {
                log.error("写入文件出错，{} => {}", file.getPath(), ioException.getMessage());
            }
        }
    }

    /**
     * 按路径创建文件，如存在则删除重新创建
     *
     * @param path 路径
     * @return {@link File}
     */
    public static File createFile(String path) {
        path = FileUtil.normalize(path);
        if (!FileUtil.isAbsolutePath(path)) {
            path = Constant.ROOT_PATH + File.separator + path;
        }
        File file = FileUtil.file(FileUtil.normalize(path));
        return createFile(file);
    }

    public static File createFile(File file) {
        if (FileUtil.exist(file)) {
            FileUtil.del(file);
        }
        FileUtil.touch(file);
        FileUtil.appendUtf8String(StrUtil.format(Constant.UPDATE,
                DateTime.now().toString(DatePattern.NORM_DATETIME_PATTERN)), file);
        FileUtil.appendUtf8String(Constant.REPO, file);
        return file;
    }

    /**
     * 校验内容是指定类型规则
     *
     * @param rule 内容
     * @param type 规则
     * @return 结果
     */
    public static boolean validRule(String rule, RuleType type) {

        //匹配标识，有标识时必须匹配
        if (ArrayUtil.isNotEmpty(type.getIdentify())) {
            if (!StrUtil.containsAny(rule, type.getIdentify())) {
                return false;
            }
        }

        if (ArrayUtil.isNotEmpty(type.getMatch()) || ArrayUtil.isNotEmpty(type.getExclude())) {
            //匹配正规则，需要至少满足一个
            if (ArrayUtil.isNotEmpty(type.getMatch())) {
                boolean math = false;
                for (String pattern : type.getMatch()) {
                    if (ReUtil.contains(pattern, rule)) {
                        return true;
                    }
                }
                if (!math) {
                    return false;
                }
            }

            //匹配负规则，需要全部不满足
            if (ArrayUtil.isNotEmpty(type.getExclude())) {
                for (String pattern : type.getExclude()) {
                    if (ReUtil.contains(pattern, rule)) {
                        return false;
                    }
                }
                return true;
            }

            return false;
        } else {
            return true;
        }

    }

    /**
     * 清理rule字符串，去除空格和某些特定符号
     *
     * @param content 内容
     * @return 结果
     */
    public static String clearRule(String content) {
        content = StrUtil.isNotBlank(content) ? StrUtil.trim(content) : StrUtil.EMPTY;

        //有效性检测
        if (ReUtil.contains(Constant.EFFICIENT_REGEX, content)) {
            return StrUtil.EMPTY;
        }

        //去除首尾 基础修饰符号
        if (ReUtil.contains(Constant.BASIC_MODIFY_REGEX, content)) {
            content = ReUtil.replaceAll(content, Constant.BASIC_MODIFY_REGEX, StrUtil.EMPTY);
        }

        return StrUtil.trim(content);
    }

    public static <K, T> void safePut(Map<K, Set<T>> map, K key, T val) {
        if (map.containsKey(key)) {
            map.get(key).add(val);
        } else {
            map.put(key, CollUtil.newHashSet(val));
        }
    }

}
