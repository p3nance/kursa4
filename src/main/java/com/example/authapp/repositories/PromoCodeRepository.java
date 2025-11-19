package com.example.authapp.repositories;

import config.Config;
import com.example.authapp.models.PromoCode;
import com.example.authapp.dto.PromoCodeDTO;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PromoCodeRepository {

    /**
     * ✅ Валидация промокода из БД
     */
    public PromoCode validatePromoCode(String code) throws Exception {
        try {
            String upperCode = code.toUpperCase().trim();

            // Ищем промокод в БД
            String urlString = Config.SUPABASE_URL + "/rest/v1/promo_codes?code=eq." + upperCode + "&select=*";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("Промокод не найден");
            }

            Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8);
            String response = scanner.useDelimiter("\\A").next();
            scanner.close();

            JSONArray jsonArray = new JSONArray(response);
            if (jsonArray.isEmpty()) {
                throw new Exception("Промокод не найден");
            }

            JSONObject json = jsonArray.getJSONObject(0);

            // Создаем объект PromoCode
            PromoCode promo = new PromoCode(
                    json.getInt("id"),
                    json.getString("code"),
                    json.getDouble("discount_percent"),
                    json.getInt("max_uses"),
                    json.getInt("used_count"),
                    json.getString("expiry_date"),
                    json.getBoolean("is_active")
            );

            // Проверки валидации
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

    /**
     * ✅ Создание промокода в БД
     */
    public void createPromoCode(PromoCode promoCode) throws Exception {
        try {
            String urlString = Config.SUPABASE_URL + "/rest/v1/promo_codes";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            JSONObject json = new JSONObject();
            json.put("code", promoCode.getCode().toUpperCase());
            json.put("discount_percent", promoCode.getDiscountPercent());
            json.put("max_uses", promoCode.getMaxUses());
            json.put("used_count", 0);
            json.put("expiry_date", promoCode.getExpiryDate());
            json.put("is_active", true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 201) {
                Scanner scanner = new Scanner(conn.getErrorStream(), StandardCharsets.UTF_8);
                String errorResponse = scanner.useDelimiter("\\A").next();
                scanner.close();
                throw new Exception("Ошибка создания промокода: " + errorResponse);
            }

            System.out.println("✅ Промокод создан: " + promoCode.getCode());

        } catch (Exception e) {
            throw new Exception("Ошибка создания промокода: " + e.getMessage());
        }
    }

    public void usePromoCode(int promoId) throws Exception {
        try {
            String urlString = Config.SUPABASE_URL + "/rest/v1/rpc/increment_promo_code_usage";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            JSONObject json = new JSONObject();
            json.put("promo_id_param", promoId);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200 && responseCode != 204) {
                Scanner errorScanner = new Scanner(conn.getErrorStream(), StandardCharsets.UTF_8);
                String errorResponse = errorScanner.useDelimiter("\\A").next();
                errorScanner.close();
                throw new Exception("Не удалось обновить счетчик: " + errorResponse);
            }

            System.out.println("✅ Промокод использован (RPC)");

        } catch (Exception e) {
            throw new Exception("Ошибка использования промокода: " + e.getMessage());
        }
    }

    /**
     * ✅ Удаление промокода (деактивация)
     */
    public void deletePromoCode(int promoId) throws Exception {
        try {
            String urlString = Config.SUPABASE_URL + "/rest/v1/promo_codes?id=eq." + promoId;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");  // ✅ Используем POST вместо PATCH
            conn.setRequestProperty("apikey", Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");  // ✅ Указываем реальный метод
            conn.setRequestProperty("Prefer", "return=minimal");
            conn.setDoOutput(true);

            JSONObject json = new JSONObject();
            json.put("is_active", false);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200 && responseCode != 204) {
                Scanner errorScanner = new Scanner(conn.getErrorStream(), StandardCharsets.UTF_8);
                String errorResponse = errorScanner.useDelimiter("\\A").next();
                errorScanner.close();
                throw new Exception("Не удалось деактивировать промокод: " + errorResponse);
            }

            System.out.println("✅ Промокод деактивирован");

        } catch (Exception e) {
            throw new Exception("Ошибка удаления промокода: " + e.getMessage());
        }
    }

    /**
     * ✅ Получение всех промокодов для админ-панели
     */
    public List<PromoCodeDTO> getAllPromoCodes() throws Exception {
        try {
            String urlString = Config.SUPABASE_URL + "/rest/v1/promo_codes?select=*&order=id.desc";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("Ошибка загрузки промокодов");
            }

            Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8);
            String response = scanner.useDelimiter("\\A").next();
            scanner.close();

            JSONArray jsonArray = new JSONArray(response);
            List<PromoCodeDTO> promoCodes = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                PromoCodeDTO dto = new PromoCodeDTO(
                        json.getInt("id"),
                        json.getString("code"),
                        json.getDouble("discount_percent"),
                        json.getInt("max_uses"),
                        json.getInt("used_count"),
                        json.getString("expiry_date"),
                        json.getBoolean("is_active")
                );
                promoCodes.add(dto);
            }

            return promoCodes;

        } catch (Exception e) {
            throw new Exception("Ошибка загрузки промокодов: " + e.getMessage());
        }
    }

    /**
     * ✅ Получение промокода по коду
     */
    public PromoCode getPromoCodeByCode(String code) throws Exception {
        try {
            String upperCode = code.toUpperCase().trim();
            String urlString = Config.SUPABASE_URL + "/rest/v1/promo_codes?code=eq." + upperCode + "&select=*";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return null;
            }

            Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8);
            String response = scanner.useDelimiter("\\A").next();
            scanner.close();

            JSONArray jsonArray = new JSONArray(response);
            if (jsonArray.isEmpty()) {
                return null;
            }

            JSONObject json = jsonArray.getJSONObject(0);
            return new PromoCode(
                    json.getInt("id"),
                    json.getString("code"),
                    json.getDouble("discount_percent"),
                    json.getInt("max_uses"),
                    json.getInt("used_count"),
                    json.getString("expiry_date"),
                    json.getBoolean("is_active")
            );

        } catch (Exception e) {
            throw new Exception("Ошибка получения промокода: " + e.getMessage());
        }
    }

    /**
     * ✅ Проверка срока действия
     */
    private boolean isDateValid(String expiryDate) {
        try {
            return expiryDate.compareTo(java.time.LocalDate.now().toString()) >= 0;
        } catch (Exception e) {
            return false;
        }
    }
}
