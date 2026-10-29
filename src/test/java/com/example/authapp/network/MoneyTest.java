package com.example.authapp.network;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test void roundsDiscountOnceAtCurrencyPrecision() {
        assertEquals(new BigDecimal("1.01"), Money.discount(10.05, 10));
        assertEquals(new BigDecimal("0.30"), Money.amount(0.1 + 0.2));
    }
    @Test void rejectsInvalidMonetaryInputs() {
        assertThrows(IllegalArgumentException.class, () -> Money.discount(100, 101));
        assertThrows(IllegalArgumentException.class, () -> Money.amount(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Money.amount(-1));
    }
}
