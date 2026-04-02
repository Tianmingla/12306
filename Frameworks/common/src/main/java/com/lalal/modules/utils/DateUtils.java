package com.lalal.modules.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期工具类（基于 Java 8+ java.time，线程安全）
 */
public final class DateUtils {

    // 默认格式：yyyy-MM-dd HH:mm:ss
    public static final String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_PATTERN);

    private DateUtils() {
        // 工具类，禁止实例化
    }

    // ========== 格式化 ==========

    /**
     * 将 Date 格式化为字符串（使用默认格式 yyyy-MM-dd HH:mm:ss）
     */
    public static String format(Date date) {
        if (date == null) return "";
        return toLocalDateTime(date).format(DEFAULT_FORMATTER);
    }

    /**
     * 将 Date 按指定格式格式化为字符串
     */
    public static String format(Date date, String pattern) {
        if (date == null || pattern == null || pattern.isEmpty()) return "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return toLocalDateTime(date).format(formatter);
    }
    /**
     * 将 Date 按指定格式格式化为字符串
     */
    public static String format(LocalDateTime date, String pattern) {
        if (date == null || pattern == null || pattern.isEmpty()) return "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return date.format(formatter);
    }

    // ========== 时间差计算 ==========

    /**
     * 计算两个 Date 之间相差的总秒数（向下取整）
     */
    public static long diffSeconds(Date start, Date end) {
        return Duration.between(toLocalDateTime(start), toLocalDateTime(end)).getSeconds();
    }

    /**
     * 计算两个时间点的分钟差，假设时间差在 [0, 24小时) 范围内。
     * 如果 end < start，则认为 end 是次日时间（自动 +24h）。
     *
     * 注意：此方法仅适用于时间差不超过 24 小时的场景！
     */
    public static long diffMinutes(Date start, Date end) {
        LocalDateTime startLdt = toLocalDateTime(start);
        LocalDateTime endLdt = toLocalDateTime(end);

        long minutes = Duration.between(startLdt, endLdt).toMinutes();

        while (minutes < 0) {
            // 跨天处理：加 24 小时（1440 分钟）
            minutes += 24 * 60;
        }

        return minutes;
    }
    public static long diffMinutes(LocalDateTime start, LocalDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();

        while (minutes < 0) {
            // 跨天处理：加 24 小时（1440 分钟）
            minutes += 24 * 60;
        }

        return minutes;
    }

    /**
     * 计算两个 Date 之间相差的总小时数（向下取整）
     */
    public static long diffHours(Date start, Date end) {
        return Duration.between(toLocalDateTime(start), toLocalDateTime(end)).toHours();
    }

    /**
     * 计算两个 Date 之间相差的总天数（24小时为1天，向下取整）
     */
    public static long diffDays(Date start, Date end) {
        return Duration.between(toLocalDateTime(start), toLocalDateTime(end)).toDays();
    }

    // ========== 辅助方法 ==========

    /**
     * 将 java.util.Date 转换为 LocalDateTime（使用系统默认时区）
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }
}