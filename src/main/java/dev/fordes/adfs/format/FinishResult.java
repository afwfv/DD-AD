package dev.fordes.adfs.format;

/** 收尾阶段实际追加及去重的白名单规则数。 */
public record FinishResult(long whitelistAdded, long duplicates) {

    public static final FinishResult EMPTY = new FinishResult(0, 0);
}
