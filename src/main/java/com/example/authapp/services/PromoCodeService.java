package com.example.authapp.services;

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
    public double calculateDiscount(double totalPrice, PromoCode promo) {
        return com.example.authapp.network.Money.discount(totalPrice, promo.getDiscountPercent()).doubleValue();
    }

    /**
     * ✅ Расчет финальной цены
     */
    public double calculateFinalPrice(double totalPrice, PromoCode promo) {
        return com.example.authapp.network.Money.amount(totalPrice).subtract(com.example.authapp.network.Money.discount(totalPrice, promo.getDiscountPercent())).doubleValue();
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
    public void createPromoCode(String code, double discountPercent, int maxUses, String expiryDate) throws Exception {
        PromoCode promo = new PromoCode(0, code, discountPercent, maxUses, 0, expiryDate, true);
        promoCodeRepository.createPromoCode(promo);
    }
}
