package com.example.authapp.services;

import com.example.authapp.models.PromoCode;
import com.example.authapp.repositories.PromoCodeRepository;
import java.time.LocalDate;

public class PromoCodeService {
    private final PromoCodeRepository promoCodeRepository;

    public PromoCodeService() {
        this.promoCodeRepository = new PromoCodeRepository();
    }

    public PromoCode validateAndGetPromoCode(String code) throws Exception {
        PromoCode promo = promoCodeRepository.validatePromoCode(code);
        if (!promo.canUse()) {
            throw new Exception("Промокод больше не действителен");
        }
        return promo;
    }

    public double calculateDiscount(double totalPrice, PromoCode promo) {
        return totalPrice * (promo.getDiscountPercent() / 100.0);
    }

    public double calculateFinalPrice(double totalPrice, PromoCode promo) {
        return totalPrice - calculateDiscount(totalPrice, promo);
    }

    public void usePromoCode(int promoId) throws Exception {
        promoCodeRepository.usePromoCode(promoId);
    }

    public void createPromoCode(String code, double discountPercent, int maxUses, String expiryDate) throws Exception {
        if (code == null || code.isEmpty()) {
            throw new Exception("Код промокода не может быть ране");
        }
        if (discountPercent < 0 || discountPercent > 100) {
            throw new Exception("Некорректный процент скидки");
        }
        promoCodeRepository.createPromoCode(code, discountPercent, maxUses, expiryDate);
    }
}
