package com.example.authapp.models;

import javafx.beans.property.*;

public class PromoCode {
    private final IntegerProperty promoId;
    private final StringProperty code;
    private final DoubleProperty discountPercent;
    private final IntegerProperty maxUses;
    private final IntegerProperty usedCount;
    private final StringProperty expiryDate;
    private final BooleanProperty isActive;

    public PromoCode(int promoId, String code, double discountPercent, int maxUses,
                     int usedCount, String expiryDate, boolean isActive) {
        this.promoId = new SimpleIntegerProperty(promoId);
        this.code = new SimpleStringProperty(code);
        this.discountPercent = new SimpleDoubleProperty(discountPercent);
        this.maxUses = new SimpleIntegerProperty(maxUses);
        this.usedCount = new SimpleIntegerProperty(usedCount);
        this.expiryDate = new SimpleStringProperty(expiryDate);
        this.isActive = new SimpleBooleanProperty(isActive);
    }

    public boolean canUse() {
        return isActive.get() && usedCount.get() < maxUses.get();
    }

    // Getters
    public int getPromoId() { return promoId.get(); }
    public String getCode() { return code.get(); }
    public double getDiscountPercent() { return discountPercent.get(); }
    public int getMaxUses() { return maxUses.get(); }
    public int getUsedCount() { return usedCount.get(); }
    public String getExpiryDate() { return expiryDate.get(); }
    public boolean getIsActive() { return isActive.get(); }

    // Setters
    public void setUsedCount(int count) { usedCount.set(count); }
    public void setIsActive(boolean active) { isActive.set(active); }

    // Properties
    public IntegerProperty promoIdProperty() { return promoId; }
    public StringProperty codeProperty() { return code; }
    public DoubleProperty discountPercentProperty() { return discountPercent; }
    public IntegerProperty maxUsesProperty() { return maxUses; }
    public IntegerProperty usedCountProperty() { return usedCount; }
    public StringProperty expiryDateProperty() { return expiryDate; }
    public BooleanProperty isActiveProperty() { return isActive; }

    @Override
    public String toString() {
        return String.format("%s (-%d%%, осталось: %d)", code.get(), (int)discountPercent.get(),
                maxUses.get() - usedCount.get());
    }
}