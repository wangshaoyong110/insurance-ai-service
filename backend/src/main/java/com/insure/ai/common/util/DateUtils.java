package com.insure.ai.common.util;

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * 日期工具类
 *
 * @author insurance-ai
 */
public class DateUtils {

    private DateUtils() {
    }

    /**
     * 解析 yyyy-MM-dd 为当天 00:00:00,为空或格式错误返回 null
     */
    public static LocalDateTime parseStartOfDay(String date) {
        LocalDate localDate = parseDate(date);
        return localDate == null ? null : localDate.atStartOfDay();
    }

    /**
     * 解析 yyyy-MM-dd 为当天 23:59:59,为空或格式错误返回 null
     */
    public static LocalDateTime parseEndOfDay(String date) {
        LocalDate localDate = parseDate(date);
        return localDate == null ? null : localDate.atTime(LocalTime.MAX);
    }

    private static LocalDate parseDate(String date) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
