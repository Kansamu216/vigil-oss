package io.github.kansamu216.vigil.model;

/**
 * 观测码前缀的统一判定与转换。
 *
 * <p>前缀字面量只在本类的方法体内出现，不声明为可内联的静态字符串常量，
 * 避免编译期被复制到其他模块字节码中而破坏重定位。</p>
 */
public final class ObservationCodes {

    private static final int MAX_CODE_LENGTH = 64;

    private ObservationCodes() {
    }

    /** code 非 null 且以 {@code STATS_} 开头时返回 true。 */
    public static boolean isStats(String code) {
        return code != null && code.startsWith("STATS_");
    }

    /** code 非 null 且以 {@code COLLECT_} 开头时返回 true。 */
    public static boolean isCollect(String code) {
        return code != null && code.startsWith("COLLECT_");
    }

    /**
     * 转换为统计码：已带 {@code STATS_} 前缀则原样返回；否则补前缀，并把基础码
     * 截断到 58 字符，使总长不超过 64。
     *
     * @throws IllegalArgumentException code 为 null 或空
     */
    public static String toStats(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("code must not be null or empty");
        }
        if (code.startsWith("STATS_")) {
            return code;
        }
        int room = MAX_CODE_LENGTH - "STATS_".length();
        String base = code.length() > room ? code.substring(0, room) : code;
        return "STATS_" + base;
    }
}
