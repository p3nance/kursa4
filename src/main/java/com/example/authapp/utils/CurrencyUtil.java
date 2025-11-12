package com.example.authapp.utils;

import java.text.DecimalFormat;

public class CurrencyUtil {
    private static final DecimalFormat formatter = new DecimalFormat("#,##0.00");
    private static final String DEFAULT_CURRENCY = "₽";

    public static String formatPrice(double price) {
        return formatPrice(price, "RUB");
    }

    public static String formatPrice(double price, String currency) {
        String formattedPrice = formatter.format(price);
        if ("RUB".equals(currency)) {
            return formattedPrice + " ₽";
        } else if ("USD".equals(currency)) {
            return "$" + formattedPrice;
        } else if ("EUR".equals(currency)) {
            return "€" + formattedPrice;
        } else if ("GBP".equals(currency)) {
            return "£" + formattedPrice;
        }
        return formattedPrice + " " + currency;
    }

    public static String formatPriceShort(double price) {
        return String.format("%.0f ₽", price);
    }

    public static String formatDiscount(double originalPrice, double discountPercent) {
        double discountAmount = originalPrice * (discountPercent / 100.0);
        double finalPrice = originalPrice - discountAmount;
        return String.format("Было: %.2f ₽ → Теперь: %.2f ₽ (сэкономлено: %.2f ₽)",
                originalPrice, finalPrice, discountAmount);
    }

    public static String formatPriceWithDiscount(double price, double discountPercent) {
        double finalPrice = price - (price * (discountPercent / 100.0));
        return String.format("%.2f ₽", finalPrice);
    }

    public static double calculateDiscount(double originalPrice, double discountPercent) {
        return originalPrice * (discountPercent / 100.0);
    }

    public static double applyDiscount(double originalPrice, double discountPercent) {
        return originalPrice - calculateDiscount(originalPrice, discountPercent);
    }

    public static double parsePrice(String price) throws NumberFormatException {
        if (price == null || price.isEmpty()) {
            throw new NumberFormatException("Цена не может быть пустой");
        }
        String cleaned = price.replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty()) {
            throw new NumberFormatException("Неверный формат цены");
        }
        return Double.parseDouble(cleaned);
    }
}