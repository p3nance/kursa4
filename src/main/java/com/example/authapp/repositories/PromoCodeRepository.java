package com.example.authapp.repositories;

import com.example.authapp.dto.PromoCodeDTO;
import com.example.authapp.models.PromoCode;
import com.example.authapp.network.SupabaseHttp;
import config.SessionManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.http.HttpRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PromoCodeRepository {
    public PromoCode validatePromoCode(String code) throws Exception {
        PromoCode promo = getPromoCodeByCode(code);
        if (promo == null) throw new IllegalArgumentException("Промокод не найден");
        if (!promo.getIsActive() || LocalDate.parse(promo.getExpiryDate()).isBefore(LocalDate.now()) || promo.getUsedCount() >= promo.getMaxUses()) {
            throw new IllegalArgumentException("Промокод недействителен");
        }
        return promo;
    }
    public PromoCode getPromoCodeByCode(String code) throws Exception {
        if (code == null || code.isBlank()) return null;
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        JSONArray rows = new JSONArray(SupabaseHttp.jsonBody(SupabaseHttp.send(SupabaseHttp.request(
                "/rest/v1/promo_codes?code=eq." + SupabaseHttp.queryValue(normalized) + "&select=*").GET().build())));
        return rows.isEmpty() ? null : parse(rows.getJSONObject(0));
    }
    public void createPromoCode(PromoCode promo) throws Exception {
        SessionManager.ensureValidSession();
        JSONObject body = new JSONObject().put("code", promo.getCode().trim().toUpperCase(Locale.ROOT))
                .put("discount_percent", promo.getDiscountPercent()).put("max_uses", promo.getMaxUses())
                .put("used_count", 0).put("expiry_date", promo.getExpiryDate()).put("is_active", true);
        SupabaseHttp.send(SupabaseHttp.request("/rest/v1/promo_codes").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build());
    }
    /** Usage is part of checkout_cart; never increment it independently from order creation. */
    public void usePromoCode(int promoId) {
        throw new UnsupportedOperationException("Промокод применяется автоматически при оформлении заказа");
    }
    /** Deactivate instead of deleting a referenced historical discount. */
    public void deletePromoCode(int promoId) throws Exception {
        SessionManager.ensureValidSession();
        SupabaseHttp.send(SupabaseHttp.request("/rest/v1/promo_codes?id=eq." + promoId).header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"is_active\":false}")).build());
    }
    public List<PromoCodeDTO> getAllPromoCodes() throws Exception {
        SessionManager.ensureValidSession();
        JSONArray rows = new JSONArray(SupabaseHttp.jsonBody(SupabaseHttp.send(SupabaseHttp.request(
                "/rest/v1/promo_codes?select=*&order=id.desc").GET().build())));
        List<PromoCodeDTO> result = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            PromoCode p = parse(rows.getJSONObject(i));
            result.add(new PromoCodeDTO(p.getPromoId(), p.getCode(), p.getDiscountPercent(), p.getMaxUses(), p.getUsedCount(), p.getExpiryDate(), p.getIsActive()));
        }
        return result;
    }
    private PromoCode parse(JSONObject row) {
        return new PromoCode(row.getInt("id"), row.getString("code"), row.getBigDecimal("discount_percent").doubleValue(),
                row.getInt("max_uses"), row.getInt("used_count"), row.getString("expiry_date"), row.getBoolean("is_active"));
    }
}
