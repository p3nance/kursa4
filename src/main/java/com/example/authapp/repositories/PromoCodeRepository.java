package com.example.authapp.repositories;

import com.example.authapp.models.PromoCode;
import java.util.HashMap;
import java.util.Map;

public class PromoCodeRepository {
    private static final Map<String, PromoCode> promoCodes = new HashMap<>();

    static {
        // Инициализация тестовых промокодов
        promoCodes.put("WELCOME10", new PromoCode(1, "WELCOME10", 10.0, 1000, 50, "2025-12-31", true));
        promoCodes.put("SAVE20", new PromoCode(2, "SAVE20", 20.0, 500, 100, "2025-12-31", true));
        promoCodes.put("SUMMER15", new PromoCode(3, "SUMMER15", 15.0, 300, 200, "2025-08-31", false));
    }

    public PromoCode validatePromoCode(String code) throws Exception {
        try {
            String upperCode = code.toUpperCase().trim();
            PromoCode promo = promoCodes.get(upperCode);

            if (promo == null) {
                throw new Exception("Промокод не найден");
            }

            if (!promo.getIsActive()) {
                throw new Exception("Промокод неактивен");
            }

            if (!isDateValid(promo.getExpiryDate())) {
                throw new Exception("Промокод истек");
            }

            if (promo.getUsedCount() >= promo.getMaxUses()) {
                throw new Exception("Промокод больше не доступен (исчерпан лимит использований)");
            }

            return promo;
        } catch (Exception e) {
            throw new Exception("Ошибка валидации промокода: " + e.getMessage());
        }
    }

    public void createPromoCode(PromoCode promoCode) throws Exception {
        try {
            promoCodes.put(promoCode.getCode(), promoCode);
        } catch (Exception e) {
            throw new Exception("Ошибка создания промокода: " + e.getMessage());
        }
    }

    public void usePromoCode(int promoId) throws Exception {
        try {
            for (PromoCode promo : promoCodes.values()) {
                if (promo.getPromoId() == promoId) {
                    promo.setUsedCount(promo.getUsedCount() + 1);
                    return;
                }
            }
        } catch (Exception e) {
            throw new Exception("Ошибка использования промокода: " + e.getMessage());
        }
    }

    public void deletePromoCode(String code) throws Exception {
        try {
            promoCodes.remove(code.toUpperCase());
        } catch (Exception e) {
            throw new Exception("Ошибка удаления промокода: " + e.getMessage());
        }
    }

    public PromoCode getPromoCodeByCode(String code) {
        return promoCodes.get(code.toUpperCase());
    }

    private boolean isDateValid(String expiryDate) {
        try {
            // Простая проверка: если дата меньше текущей даты, то промокод истек
            // Формат: YYYY-MM-DD
            return expiryDate.compareTo(java.time.LocalDate.now().toString()) >= 0;
        } catch (Exception e) {
            return false;
        }
    }
}