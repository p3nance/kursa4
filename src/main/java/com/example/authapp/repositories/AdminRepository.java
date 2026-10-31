package com.example.authapp.repositories;

import java.math.BigDecimal;
import com.example.authapp.dto.*;
import com.example.authapp.models.PromoCode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import config.Config;

public class AdminRepository {
    private static final String SUPABASE_URL = config.Config.SUPABASE_URL;
    private static final String SUPABASE_KEY = config.Config.SUPABASE_ANON_KEY;



        private static final Gson gson = new Gson();

    // ============ ТОВАРЫ ============

    public static List<ProductDTO> getAllProducts() throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/products?select=*";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() != 200) {
                throw new Exception("Ошибка получения товаров: " + response.statusCode());
            }

            List<ProductDTO> products = new ArrayList<>();
            JsonArray jsonArray = JsonParser.parseString(com.example.authapp.network.SupabaseHttp.jsonBody(response)).getAsJsonArray();

            for (int i = 0; i < jsonArray.size(); i++) {
                ProductDTO dto = gson.fromJson(jsonArray.get(i), ProductDTO.class);
                products.add(dto);
            }

            return products;

        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки товаров: " + e.getMessage());
            throw new Exception("Ошибка загрузки товаров: " + e.getMessage());
        }
    }

    /** Archive stock availability; historical order lines and cart references stay intact. */
    public static void deleteProduct(int productId) throws Exception {
        config.SessionManager.ensureValidSession();
        com.example.authapp.network.SupabaseHttp.send(com.example.authapp.network.SupabaseHttp.request(
                "/rest/v1/products?id=eq." + productId).header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"stock\":0}")).build());
    }

    public static void addProduct(String name, String description, BigDecimal price,
                                  int stock, String category, String manufacturer, String imageUrl) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/products";

            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("name", name);
            jsonBody.addProperty("description", description);
            jsonBody.addProperty("price", price);
            jsonBody.addProperty("stock", stock);
            jsonBody.addProperty("category", category);
            jsonBody.addProperty("manufacturer", manufacturer);
            jsonBody.addProperty("image_url", imageUrl != null ? imageUrl : "");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() != 201) {
                throw new Exception("Ошибка добавления товара: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка добавления товара: " + e.getMessage());
            throw new Exception("Ошибка добавления товара: " + e.getMessage());
        }
    }

    public static void updateProduct(int productId, String name, String description, BigDecimal price,
                                     int stock, String category, String manufacturer) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/products?id=eq." + productId;

            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("name", name);
            jsonBody.addProperty("description", description);
            jsonBody.addProperty("price", price);
            jsonBody.addProperty("stock", stock);
            jsonBody.addProperty("category", category);
            jsonBody.addProperty("manufacturer", manufacturer);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                throw new Exception("Ошибка обновления товара: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка обновления товара: " + e.getMessage());
            throw new Exception("Ошибка обновления товара: " + e.getMessage());
        }
    }

    // ✅ Обновление только остатка на складе (для менеджера)
    public static void updateProductStock(int productId, int newStock) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/products?id=eq." + productId;

            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("stock", newStock);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                throw new Exception("Ошибка обновления остатка: " + response.statusCode());
            }

            System.out.println("✅ Остаток товара id=" + productId + " обновлён → " + newStock);

        } catch (Exception e) {
            System.err.println("❌ Ошибка обновления остатка: " + e.getMessage());
            throw new Exception("Ошибка обновления остатка: " + e.getMessage());
        }
    }

    public static void updateProductImage(int productId, String imageUrl) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/products?id=eq." + productId;

            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("image_url", imageUrl != null ? imageUrl : "");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() != 204 && response.statusCode() != 200) {
                throw new Exception("Ошибка обновления изображения: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка обновления изображения: " + e.getMessage());
            throw new Exception("Ошибка обновления изображения: " + e.getMessage());
        }
    }

    // ============ ПОЛЬЗОВАТЕЛИ ============

    public static List<UserDTO> getAllUsers() throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/profiles?select=*";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() != 200) {
                throw new Exception("Ошибка получения пользователей: " + response.statusCode());
            }

            List<UserDTO> users = new ArrayList<>();
            JsonArray jsonArray = JsonParser.parseString(com.example.authapp.network.SupabaseHttp.jsonBody(response)).getAsJsonArray();

            for (int i = 0; i < jsonArray.size(); i++) {
                UserDTO dto = gson.fromJson(jsonArray.get(i), UserDTO.class);
                users.add(dto);
            }
            return users;

        } catch (Exception e) {
            throw new Exception("Ошибка загрузки пользователей: " + e.getMessage());
        }
    }

    // ✅ Загружает MAP: UUID → email из таблицы profiles (одним запросом)
    private static Map<String, String> resolveUserEmails() {
        Map<String, String> map = new HashMap<>();
        try {
            String url = SUPABASE_URL + "/rest/v1/profiles?select=id,email";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() == 200 && !response.body().equals("[]")) {
                JsonArray arr = JsonParser.parseString(com.example.authapp.network.SupabaseHttp.jsonBody(response)).getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject obj = arr.get(i).getAsJsonObject();
                    String id    = obj.has("id")    && !obj.get("id").isJsonNull()    ? obj.get("id").getAsString()    : null;
                    String email = obj.has("email") && !obj.get("email").isJsonNull() ? obj.get("email").getAsString() : null;
                    if (id != null && email != null) {
                        map.put(id, email);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Ошибка загрузки emails: " + e.getMessage());
        }
        return map;
    }

    // ============ ЗАКАЗЫ ============

    public static List<OrderDTO> getAllOrders() throws Exception {
        try {
            // ✅ Сортировка по id от 1 до бесконечности
            String url = SUPABASE_URL + "/rest/v1/orders?select=*&order=id.asc";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() != 200) {
                throw new Exception("Ошибка загрузки заказов: " + response.statusCode());
            }

            // ✅ Загружаем MAP uuid→email одним запросом, чтобы не делать N+1
            Map<String, String> emailMap = resolveUserEmails();

            JsonArray jsonArray = JsonParser.parseString(com.example.authapp.network.SupabaseHttp.jsonBody(response)).getAsJsonArray();
            List<OrderDTO> orders = new ArrayList<>();

            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject orderJson = jsonArray.get(i).getAsJsonObject();

                OrderDTO dto = new OrderDTO();
                dto.orderId = orderJson.get("id").getAsInt();

                // ✅ Подставляем email вместо UUID
                String rawUserId = orderJson.has("user_id") && !orderJson.get("user_id").isJsonNull()
                        ? orderJson.get("user_id").getAsString() : null;
                if (rawUserId != null && emailMap.containsKey(rawUserId)) {
                    dto.userId = emailMap.get(rawUserId); // реальный email
                } else {
                    dto.userId = rawUserId; // UUID как fallback
                }

                dto.totalAmount   = orderJson.has("total_amount")
                        ? orderJson.get("total_amount").getAsBigDecimal() : BigDecimal.ZERO;
                dto.status        = orderJson.has("status") && !orderJson.get("status").isJsonNull()
                        ? orderJson.get("status").getAsString() : "unknown";
                dto.orderDate     = orderJson.has("created_at") && !orderJson.get("created_at").isJsonNull()
                        ? orderJson.get("created_at").getAsString() : null;
                dto.promoCodeId   = orderJson.has("promo_code_id") && !orderJson.get("promo_code_id").isJsonNull()
                        ? orderJson.get("promo_code_id").getAsInt() : null;
                dto.discountAmount = orderJson.has("discount_amount") && !orderJson.get("discount_amount").isJsonNull()
                        ? orderJson.get("discount_amount").getAsBigDecimal() : BigDecimal.ZERO;
                dto.finalAmount   = orderJson.has("final_amount")
                        ? orderJson.get("final_amount").getAsBigDecimal() : dto.totalAmount;

                dto.items = getOrderItemsAdmin(dto.orderId);
                orders.add(dto);

            }

            System.out.println("✅ Загружено заказов: " + orders.size());
            return orders;

        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки заказов: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private static List<OrderItemDTO> getOrderItemsAdmin(int orderId) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/order_items?order_id=eq." + orderId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() != 200) {
                return new ArrayList<>();
            }

            List<OrderItemDTO> items = new ArrayList<>();
            JsonArray jsonArray = JsonParser.parseString(com.example.authapp.network.SupabaseHttp.jsonBody(response)).getAsJsonArray();

            for (int i = 0; i < jsonArray.size(); i++) {
                OrderItemDTO item = gson.fromJson(jsonArray.get(i), OrderItemDTO.class);
                items.add(item);
            }

            return items;

        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки товаров заказа: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void updateOrderStatusAdmin(int orderId, String newStatus) throws Exception {
        try {
            System.out.println("📝 Обновление статуса заказа ID=" + orderId + " → " + newStatus);

            String url = SUPABASE_URL + "/rest/v1/orders?id=eq." + orderId;

            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("status", newStatus);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            System.out.println("📥 Статус ответа: " + response.statusCode());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                throw new Exception("Ошибка обновления статуса: HTTP " + response.statusCode());
            }

            System.out.println("✅ Статус заказа успешно обновлен!");

        } catch (Exception e) {
            System.err.println("❌ Ошибка при обновлении статуса заказа: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Ошибка при обновлении статуса заказа: " + e.getMessage());
        }
    }

    // ============ БЛОКИРОВКА ============

    public static void blockUser(String userId) throws Exception { setUserBlocked(userId, true); }
    public static void unblockUser(String userId) throws Exception { setUserBlocked(userId, false); }
    private static void setUserBlocked(String userId, boolean blocked) throws Exception {
        config.SessionManager.ensureValidSession();
        java.util.UUID.fromString(userId);
        String body = new org.json.JSONObject().put("p_user_id", userId).put("p_blocked", blocked).toString();
        com.example.authapp.network.SupabaseHttp.send(com.example.authapp.network.SupabaseHttp.request(
                "/rest/v1/rpc/set_user_blocked").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    // ============ ПРОМОКОДЫ ============

    public static List<PromoCodeDTO> getAllPromoCodes() throws Exception {
        PromoCodeRepository repo = new PromoCodeRepository();
        return repo.getAllPromoCodes();
    }

    public static void createPromoCode(PromoCodeDTO dto) throws Exception {
        PromoCode promo = new PromoCode(
                dto.id, dto.code, dto.discountPercent,
                dto.maxUses, dto.usedCount, dto.expiryDate, dto.isActive
        );
        PromoCodeRepository repo = new PromoCodeRepository();
        repo.createPromoCode(promo);
    }

    public static void deletePromoCode(int promoId) throws Exception {
        PromoCodeRepository repo = new PromoCodeRepository();
        repo.deletePromoCode(promoId);
    }
}