package com.example.authapp.network;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Decimal currency arithmetic. Checkout prices and discounts are recalculated by the server. */
public final class Money {
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    private Money() {}
    public static BigDecimal amount(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Некорректная сумма");
        return value.setScale(2, RoundingMode.HALF_UP);
    }
    /** Compatibility boundary for old callers; calculations use the decimal overloads. */
    public static BigDecimal amount(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Некорректная сумма");
        return amount(BigDecimal.valueOf(value));
    }
    /** User-entered prices accept a decimal point or comma and at most two fraction digits. */
    public static BigDecimal parsePrice(String text) {
        String input = text == null ? "" : text.trim();
        if (!input.matches("[0-9]+(?:[.,][0-9]{1,2})?"))
            throw new IllegalArgumentException("Укажите неотрицательную цену с точностью до копеек");
        return amount(new BigDecimal(input.replace(',', '.')));
    }
    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Некорректное количество");
        return amount(unitPrice).multiply(BigDecimal.valueOf(quantity));
    }
    public static BigDecimal percent(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Скидка должна быть от 0 до 100%");
        return value;
    }
    public static BigDecimal percent(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Некорректная скидка");
        return percent(BigDecimal.valueOf(value));
    }
    public static BigDecimal discount(BigDecimal total, BigDecimal percent) {
        percent(percent);
        return amount(total).multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
    public static BigDecimal discount(BigDecimal total, double percent) {
        if (!Double.isFinite(percent)) throw new IllegalArgumentException("Некорректная скидка");
        return discount(total, BigDecimal.valueOf(percent));
    }
    public static BigDecimal discount(double total, double percent) { return discount(amount(total), percent); }
    public static BigDecimal payable(BigDecimal total, BigDecimal discount) {
        BigDecimal result = amount(total).subtract(amount(discount));
        if (result.signum() < 0) throw new IllegalArgumentException("Скидка превышает сумму заказа");
        return result;
    }
}
