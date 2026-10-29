package com.example.authapp.network;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Decimal arithmetic for displayed previews; the server recalculates checkout totals. */
public final class Money {
    private Money() {}
    public static BigDecimal amount(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Некорректная сумма");
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
    public static BigDecimal discount(double total, double percent) {
        if (!Double.isFinite(percent) || percent < 0 || percent > 100) throw new IllegalArgumentException("Некорректная скидка");
        return amount(total).multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
