package com.example.authapp.dto;

/**
 * Data Transfer Object для PromoCode
 */
public class PromoCodeDTO {
    public int id;
    public String code;
    public double discountPercent;
    public int maxUses;
    public int usedCount;
    public String expiryDate;
    public boolean isActive;

    public PromoCodeDTO() {}

    public PromoCodeDTO(int id, String code, double discountPercent, int maxUses,
                        int usedCount, String expiryDate, boolean isActive) {
        this.id = id;
        this.code = code;
        this.discountPercent = discountPercent;
        this.maxUses = maxUses;
        this.usedCount = usedCount;
        this.expiryDate = expiryDate;
        this.isActive = isActive;
    }
}
