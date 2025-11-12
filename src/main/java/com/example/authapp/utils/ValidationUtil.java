package com.example.authapp.utils;

import java.util.regex.Pattern;

public class ValidationUtil {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^(\\+7|8)?[\\s-]?\\(?[0-9]{3}\\)?[\\s-]?[0-9]{3}[\\s-]?[0-9]{2}[\\s-]?[0-9]{2}$"
    );

    private static final Pattern PROMO_CODE_PATTERN = Pattern.compile("^[A-Z0-9]+$");

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }

    public static boolean isValidPassword(String password) {
        return password != null &&
                password.length() >= 8 &&
                password.matches(".*[A-Z].*") &&
                password.matches(".*[a-z].*") &&
                password.matches(".*[0-9].*");
    }

    public static boolean isValidPromoCode(String code) {
        return code != null && code.length() > 0 && code.length() <= 50 &&
                PROMO_CODE_PATTERN.matcher(code).matches();
    }

    public static boolean isValidPrice(String priceStr) {
        try {
            double price = Double.parseDouble(priceStr);
            return price > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidStock(String stockStr) {
        try {
            int stock = Integer.parseInt(stockStr);
            return stock >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static boolean hasMinLength(String value, int minLength) {
        return value != null && value.length() >= minLength;
    }

    public static boolean hasMaxLength(String value, int maxLength) {
        return value != null && value.length() <= maxLength;
    }

    public static String sanitizeInput(String input) {
        if (input == null) return "";
        return input.trim()
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll("\"", "&quot;")
                .replaceAll("'", "&#39;");
    }
}