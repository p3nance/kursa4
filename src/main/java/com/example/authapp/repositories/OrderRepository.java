package com.example.authapp.repositories;

import com.example.authapp.dto.OrderDTO;
import com.example.authapp.dto.OrderItemDTO;
import com.example.authapp.network.SupabaseHttp;
import config.SessionManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OrderRepository {
    private OrderRepository() {}
    /** Prices, discounts, stock and ownership are validated together by the server transaction. */
    public static int checkout(UUID requestId, JSONArray items, String promoCode) throws Exception {
        SessionManager.ensureValidSession();
        JSONObject body = new JSONObject().put("p_request_id", requestId.toString()).put("p_items", items)
                .put("p_promo_code", promoCode == null || promoCode.isBlank() ? JSONObject.NULL : promoCode.trim());
        var response = SupabaseHttp.send(SupabaseHttp.request("/rest/v1/rpc/checkout_cart")
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build());
        try { return Integer.parseInt(SupabaseHttp.jsonBody(response).trim()); }
        catch (NumberFormatException e) { throw new java.io.IOException("Некорректный ответ оформления заказа"); }
    }
    public static List<OrderDTO> getUserOrders(String userId) throws Exception {
        SessionManager.ensureValidSession();
        if (!userId.equals(SessionManager.getUserId())) throw new IllegalArgumentException("Можно получить только свои заказы");
        // One request instead of one request for every order's items.
        String path = "/rest/v1/orders?user_id=eq." + SupabaseHttp.queryValue(userId)
                + "&order=created_at.desc&select=*,order_items(*)";
        JSONArray data = new JSONArray(SupabaseHttp.jsonBody(SupabaseHttp.send(SupabaseHttp.request(path).GET().build())));
        List<OrderDTO> result = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject row = data.getJSONObject(i);
            OrderDTO order = new OrderDTO();
            order.orderId = row.getInt("id"); order.userId = row.getString("user_id");
            order.totalAmount = row.getBigDecimal("total_amount").doubleValue();
            order.finalAmount = row.optBigDecimal("final_amount", row.getBigDecimal("total_amount")).doubleValue();
            order.discountAmount = row.optBigDecimal("discount_amount", java.math.BigDecimal.ZERO).doubleValue();
            order.promoCodeId = row.isNull("promo_code_id") ? null : row.optInt("promo_code_id");
            order.promoCode = row.optString("promo_code", null);
            order.status = row.optString("status", "pending"); order.orderDate = row.optString("created_at", "");
            order.items = parseItems(row.optJSONArray("order_items")); result.add(order);
        }
        return result;
    }
    public static List<OrderItemDTO> getOrderItems(int orderId) throws Exception {
        SessionManager.ensureValidSession();
        return parseItems(new JSONArray(SupabaseHttp.jsonBody(SupabaseHttp.send(SupabaseHttp.request(
                "/rest/v1/order_items?order_id=eq." + orderId + "&select=*").GET().build()))));
    }
    private static List<OrderItemDTO> parseItems(JSONArray data) {
        List<OrderItemDTO> result = new ArrayList<>();
        if (data == null) return result;
        for (int i = 0; i < data.length(); i++) {
            JSONObject row = data.getJSONObject(i);
            OrderItemDTO item = new OrderItemDTO(); item.id = row.getInt("id"); item.orderId = row.getInt("order_id");
            item.productId = row.optInt("product_id"); item.productName = row.getString("product_name");
            item.productImage = row.optString("product_image", ""); item.quantity = row.getInt("quantity");
            item.price = row.getBigDecimal("price").doubleValue(); item.subtotal = row.getBigDecimal("subtotal").doubleValue();
            result.add(item);
        }
        return result;
    }
}
