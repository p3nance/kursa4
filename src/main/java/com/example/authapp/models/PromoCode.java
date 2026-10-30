package com.example.authapp.models;

import javafx.beans.property.*;
import java.math.BigDecimal;
import com.example.authapp.network.Money;

public class PromoCode {
    private final IntegerProperty promoId;
    private final StringProperty code;
    private final ReadOnlyObjectWrapper<BigDecimal> discountPercent;
    private final IntegerProperty maxUses;
    private final IntegerProperty usedCount;
    private final StringProperty expiryDate;
    private final BooleanProperty isActive;

    public PromoCode(int promoId, String code, double discountPercent, int maxUses,
                     int usedCount, String expiryDate, boolean isActive) {
        this(promoId, code, Money.percent(discountPercent), maxUses, usedCount, expiryDate, isActive);
    }

    public PromoCode(int promoId, String code, BigDecimal discountPercent, int maxUses,
                     int usedCount, String expiryDate, boolean isActive) {
        this.promoId = new SimpleIntegerProperty(promoId);
        this.code = new SimpleStringProperty(code);
        this.discountPercent = new ReadOnlyObjectWrapper<>(Money.percent(discountPercent));
        this.maxUses = new SimpleIntegerProperty(maxUses);
        this.usedCount = new SimpleIntegerProperty(usedCount);
        this.expiryDate = new SimpleStringProperty(expiryDate);
        this.isActive = new SimpleBooleanProperty(isActive);
    }

    /**
     * ✅ Проверка доступности промокода
     */
    public boolean canUse() {
        return isActive.get() && usedCount.get() < maxUses.get();
    }

    // Getters
    public int getPromoId() { return promoId.get(); }
    public String getCode() { return code.get(); }
    public BigDecimal getDiscountPercent() { return discountPercent.get(); }
    public int getMaxUses() { return maxUses.get(); }
    public int getUsedCount() { return usedCount.get(); }
    public String getExpiryDate() { return expiryDate.get(); }
    public boolean getIsActive() { return isActive.get(); }

    // Properties
    public IntegerProperty promoIdProperty() { return promoId; }
    public StringProperty codeProperty() { return code; }
    public ReadOnlyObjectProperty<BigDecimal> discountPercentProperty() { return discountPercent.getReadOnlyProperty(); }
    public IntegerProperty maxUsesProperty() { return maxUses; }
    public IntegerProperty usedCountProperty() { return usedCount; }
    public StringProperty expiryDateProperty() { return expiryDate; }
    public BooleanProperty isActiveProperty() { return isActive; }

    // Setters
    public void setPromoId(int promoId) { this.promoId.set(promoId); }
    public void setCode(String code) { this.code.set(code); }
    public void setDiscountPercent(double value) { setDiscountPercent(Money.percent(value)); }
    public void setDiscountPercent(BigDecimal value) { this.discountPercent.set(Money.percent(value)); }
    public void setMaxUses(int maxUses) { this.maxUses.set(maxUses); }
    public void setUsedCount(int usedCount) { this.usedCount.set(usedCount); }
    public void setExpiryDate(String expiryDate) { this.expiryDate.set(expiryDate); }
    public void setIsActive(boolean isActive) { this.isActive.set(isActive); }
}
