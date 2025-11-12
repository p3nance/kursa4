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

    public void createPromoCode(String code, double discountPercent, int maxUses, LocalDate expiryDate) throws Exception {
        if (code == null || code.isEmpty() || code.length() > 50) {
            throw new IllegalArgumentException("Некорректный формат кода");
        }

        if (discountPercent < 0 || discountPercent > 100) {
            throw new IllegalArgumentException("Скидка должна быть от 0 до 100%");
        }

        if (expiryDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Дата истечения не может быть в прошлом");
        }

        PromoCode promo = new PromoCode(0, code, discountPercent, maxUses, 0, expiryDate.toString(), true);
        promoCodeRepository.createPromoCode(promo);
    }

    public void deletePromoCode(String code) throws Exception {
        promoCodeRepository.deletePromoCode(code);
    }

    public PromoCode getPromoCode(String code) throws Exception {
        return promoCodeRepository.getPromoCodeByCode(code);
    }
}