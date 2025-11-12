package com.example.authapp.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DateUtil {
    private static final DateTimeFormatter RUSSIAN_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("ru"));

    private static final DateTimeFormatter RUSSIAN_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", new Locale("ru"));

    private static final DateTimeFormatter ISO_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE;

    private static final DateTimeFormatter ISO_DATE_TIME_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static String formatLocalDate(LocalDate date) {
        if (date == null) return "";
        return date.format(RUSSIAN_DATE_FORMAT);
    }

    public static String formatLocalDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(RUSSIAN_DATE_TIME_FORMAT);
    }

    public static String formatDateShort(LocalDate date) {
        if (date == null) return "";
        return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    public static LocalDate parseDate(String dateString) throws Exception {
        try {
            return LocalDate.parse(dateString, ISO_FORMAT);
        } catch (Exception e) {
            throw new Exception("Ошибка парсинга даты: " + e.getMessage());
        }
    }

    public static LocalDateTime parseDateTime(String dateTimeString) throws Exception {
        try {
            return LocalDateTime.parse(dateTimeString, ISO_DATE_TIME_FORMAT);
        } catch (Exception e) {
            throw new Exception("Ошибка парсинга даты и времени: " + e.getMessage());
        }
    }

    public static boolean isDateExpired(LocalDate date) {
        return date.isBefore(LocalDate.now());
    }

    public static boolean isDateInFuture(LocalDate date) {
        return date.isAfter(LocalDate.now());
    }

    public static boolean isDateToday(LocalDate date) {
        return date.equals(LocalDate.now());
    }

    public static String daysUntil(LocalDate date) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date);
        if (days < 0) {
            return "Истекло " + Math.abs(days) + " дн. назад";
        } else if (days == 0) {
            return "Сегодня";
        } else if (days == 1) {
            return "Завтра";
        } else {
            return "Через " + days + " дн.";
        }
    }

    public static String getMonthName(int month) {
        return YearMonth.of(LocalDate.now().getYear(), month)
                .format(DateTimeFormatter.ofPattern("MMMM", new Locale("ru")));
    }

    public static String getCurrentDateFormatted() {
        return formatLocalDate(LocalDate.now());
    }

    public static String getCurrentDateTimeFormatted() {
        return formatLocalDateTime(LocalDateTime.now());
    }
}