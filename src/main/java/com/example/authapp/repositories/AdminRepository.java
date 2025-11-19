package com.example.authapp.repositories;

import com.example.authapp.dto.*;
import com.example.authapp.models.PromoCode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import config.Config;

/**
 * ✅ AdminRepository - управление товарами, пользователями и заказами
 * Использует Supabase REST API для товаров/заказов и Admin API для блокировки
 */
public class AdminRepository {
    private static final String SUPABASE_URL = Config.SUPABASE_URL;
    private static final String SUPABASE_KEY = Config.SUPABASE_ANON_KEY;

    // ✅ SERVICE ROLE KEY - для Admin API (блокировка/разблокировка в Authentication)
    private static final String SUPABASE_SERVICE_ROLE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFzdGh1aHprY2lpbXVjYXJzY2NvIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc1NzQyMzgxNSwiZXhwIjoyMDcyOTk5ODE1fQ.WQaeTsuXb3rdOvKuadz7Hnq3daQ3uBC0nlBnxRXhRZo";

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    // ============ ТОВАРЫ ============

    public static List<ProductDTO> getAllProducts() throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/products?select=*";


            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new Exception("Ошибка получения товаров: " + response.statusCode());
            }

            List<ProductDTO> products = new ArrayList<>();
            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();

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

    public static void deleteProduct(int productId) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/products?id=eq." + productId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204) {
                throw new Exception("Ошибка удаления: " + response.statusCode());
            }

        } catch (Exception e) {
            throw new Exception("Ошибка удаления товара: " + e.getMessage());
        }
    }

    public static void addProduct(String name, String description, double price,
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
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                throw new Exception("Ошибка добавления товара: " + response.statusCode() + " " + response.body());
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка добавления товара: " + e.getMessage());
            throw new Exception("Ошибка добавления товара: " + e.getMessage());
        }
    }

    // Обновление товара
    public static void updateProduct(int productId, String name, String description, double price,
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
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                throw new Exception("Ошибка обновления товара: " + response.statusCode() + " " + response.body());
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка обновления товара: " + e.getMessage());
            throw new Exception("Ошибка обновления товара: " + e.getMessage());
        }
    }

    public static void updateProductImage(int productId, String imageUrl) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/products?id=eq." + productId;

            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("image_url", imageUrl != null ? imageUrl : "");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204 && response.statusCode() != 200) {
                throw new Exception("Ошибка обновления изображения: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка обновления изображения: " + e.getMessage());
            throw new Exception("Ошибка обновления изображения: " + e.getMessage());
        }
    }

    // ПОЛЬЗОВАТЕЛИ

    public static List<UserDTO> getAllUsers() throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/profiles?select=*";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new Exception("Ошибка получения пользователей: " + response.statusCode());
            }

            List<UserDTO> users = new ArrayList<>();
            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();

            for (int i = 0; i < jsonArray.size(); i++) {
                UserDTO dto = gson.fromJson(jsonArray.get(i), UserDTO.class);
                users.add(dto);
            }
            return users;

        } catch (Exception e) {
            throw new Exception("Ошибка загрузки пользователей: " + e.getMessage());
        }
    }

    // ЗАКАЗЫ

    public static List<OrderDTO> getAllOrders() throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/orders?select=*&order=created_at.desc";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new Exception("Ошибка загрузки заказов: " + response.statusCode());
            }

            Gson gson = new Gson();
            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();

            List<OrderDTO> orders = new ArrayList<>();
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject orderJson = jsonArray.get(i).getAsJsonObject();

                OrderDTO dto = new OrderDTO();

                // ✅ КРИТИЧЕСКИ ВАЖНО: правильно парсим orderId из поля "id"
                dto.orderId = orderJson.get("id").getAsInt();
                dto.userId = orderJson.has("user_id") && !orderJson.get("user_id").isJsonNull()
                        ? orderJson.get("user_id").getAsString() : null;
                dto.totalAmount = orderJson.has("total_amount")
                        ? orderJson.get("total_amount").getAsDouble() : 0.0;
                dto.status = orderJson.has("status") && !orderJson.get("status").isJsonNull()
                        ? orderJson.get("status").getAsString() : "unknown";
                dto.orderDate = orderJson.has("created_at") && !orderJson.get("created_at").isJsonNull()
                        ? orderJson.get("created_at").getAsString() : null;

                // Промокод данные (если есть)
                dto.promoCodeId = orderJson.has("promo_code_id") && !orderJson.get("promo_code_id").isJsonNull()
                        ? orderJson.get("promo_code_id").getAsInt() : null;
                dto.discountAmount = orderJson.has("discount_amount") && !orderJson.get("discount_amount").isJsonNull()
                        ? orderJson.get("discount_amount").getAsDouble() : 0.0;
                dto.finalAmount = orderJson.has("final_amount")
                        ? orderJson.get("final_amount").getAsDouble() : dto.totalAmount;

                // Загружаем товары заказа
                dto.items = getOrderItemsAdmin(dto.orderId);

                orders.add(dto);

                System.out.println("✅ Заказ загружен: ID=" + dto.orderId + ", статус=" + dto.status);
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
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return new ArrayList<>();
            }

            List<OrderItemDTO> items = new ArrayList<>();
            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();

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

            System.out.println("📤 URL: " + url);
            System.out.println("📤 Body: " + jsonBody.toString());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("📥 Статус ответа: " + response.statusCode());
            System.out.println("📥 Тело ответа: " + response.body());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                throw new Exception("Ошибка обновления статуса: HTTP " + response.statusCode() + " - " + response.body());
            }

            System.out.println("✅ Статус заказа успешно обновлен!");

        } catch (Exception e) {
            System.err.println("❌ Ошибка при обновлении статуса заказа: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Ошибка при обновлении статуса заказа: " + e.getMessage());
        }
    }

    public static void blockUser(String userId) throws Exception {
        try {
            String url = SUPABASE_URL + "/auth/v1/admin/users/" + userId;
            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("ban_duration", "1000000h");

            String jsonString = jsonBody.toString();
            System.out.println("📝 JSON Body: " + jsonString);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_SERVICE_ROLE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .method("PUT", HttpRequest.BodyPublishers.ofString(jsonString))
                    .build();

            System.out.println("📤 Отправка запроса...");

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("📊 HTTP Статус: " + response.statusCode());
            System.out.println("📄 Ответ: " + response.body());

            if (response.statusCode() != 200) {
                System.err.println("❌ Ошибка при блокировке!");
                System.err.println(" Статус: " + response.statusCode());
                System.err.println(" Тело ошибки: " + response.body());
                throw new Exception("Ошибка блокировки пользователя: HTTP " + response.statusCode());
            }
            updateUserBlockStatus(userId, true);

        } catch (Exception e) {
            System.err.println("════════════════════════════════════════");
            System.err.println("❌ ОШИБКА ПРИ БЛОКИРОВКЕ");
            System.err.println("════════════════════════════════════════");
            System.err.println("📌 Сообщение: " + e.getMessage());
            e.printStackTrace();
            System.err.println("════════════════════════════════════════");
            throw new Exception("Ошибка при блокировке пользователя: " + e.getMessage());
        }
    }

    public static void unblockUser(String userId) throws Exception {
        try {
            String url = SUPABASE_URL + "/auth/v1/admin/users/" + userId;
            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("ban_duration", "0ms");

            String jsonString = jsonBody.toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_SERVICE_ROLE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .method("PUT", HttpRequest.BodyPublishers.ofString(jsonString))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            updateUserBlockStatus(userId, false);
            if (response.statusCode() != 200) {

                throw new Exception("Ошибка разблокировки пользователя: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("════════════════════════════════════════");
            throw new Exception("Ошибка при разблокировке пользователя: " + e.getMessage());
        }
    }
    private static void updateUserBlockStatus(String userId, boolean isBlocked) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/profiles?id=eq." + userId;

            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("is_blocked", isBlocked);  // ✅ Обновляем флаг!

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * ✅ Получение всех промокодов
     */
    public static List<PromoCodeDTO> getAllPromoCodes() throws Exception {
        PromoCodeRepository repo = new PromoCodeRepository();
        return repo.getAllPromoCodes();
    }

    /**
     * ✅ Создание промокода
     */
    public static void createPromoCode(PromoCodeDTO dto) throws Exception {
        PromoCode promo = new PromoCode(
                dto.id,
                dto.code,
                dto.discountPercent,
                dto.maxUses,
                dto.usedCount,
                dto.expiryDate,
                dto.isActive
        );
        PromoCodeRepository repo = new PromoCodeRepository();
        repo.createPromoCode(promo);
    }

    /**
     * ✅ Удаление промокода
     */
    public static void deletePromoCode(int promoId) throws Exception {
        PromoCodeRepository repo = new PromoCodeRepository();
        repo.deletePromoCode(promoId);
    }
}