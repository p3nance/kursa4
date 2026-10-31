package com.example.authapp.services;

import com.example.authapp.network.Money;
import java.math.BigDecimal;
import com.example.authapp.models.PromoCode;
import com.example.authapp.repositories.PromoCodeRepository;

public class PromoCodeService {
    private final PromoCodeRepository promoCodeRepository;

    public PromoCodeService() {
        this.promoCodeRepository = new PromoCodeRepository();
    }

    /**
     * ✅ Валидация и получение промокода
     */
    public PromoCode validateAndGetPromoCode(String code) throws Exception {
        PromoCode promo = promoCodeRepository.validatePromoCode(code);
        if (!promo.canUse()) {
            throw new Exception("Промокод больше не действителен");
        }
        return promo;
    }

    /**
     * ✅ Расчет скидки
     */
    public BigDecimal calculateDiscount(BigDecimal totalPrice, PromoCode promo) {
        return Money.discount(totalPrice, promo.getDiscountPercent());
    }

    /**
     * ✅ Расчет финальной цены
     */
    public BigDecimal calculateFinalPrice(BigDecimal totalPrice, PromoCode promo) {
        return Money.amount(totalPrice).subtract(Money.discount(totalPrice, promo.getDiscountPercent()));
    }

    /**
     * ✅ Использование промокода (увеличение счетчика)
     */
    public void usePromoCode(int promoId) throws Exception {
        promoCodeRepository.usePromoCode(promoId);
    }

    /**
     * ✅ Создание промокода
     */
    public void createPromoCode(String code, BigDecimal discountPercent, int maxUses, String expiryDate) throws Exception {
        PromoCode promo = new PromoCode(0, code, discountPercent, maxUses, 0, expiryDate, true);
        promoCodeRepository.createPromoCode(promo);
    }
}
