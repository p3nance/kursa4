package com.example.authapp.dto;

/**
 * Data Transfer Object для PromoCode
 */
import java.math.BigDecimal;
import com.example.authapp.network.Money;

public class PromoCodeDTO {
    public int id;
    public String code;
    public BigDecimal discountPercent = BigDecimal.ZERO;
    public int maxUses;
    public int usedCount;
    public String expiryDate;
    public boolean isActive;

    public PromoCodeDTO() {}

    public PromoCodeDTO(int id, String code, BigDecimal discountPercent, int maxUses,
                        int usedCount, String expiryDate, boolean isActive) {
        this.id = id;
        this.code = code;
        this.discountPercent = Money.percent(discountPercent);
        this.maxUses = maxUses;
        this.usedCount = usedCount;
        this.expiryDate = expiryDate;
        this.isActive = isActive;
    }
}
