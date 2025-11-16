package com.example.authapp.repositories;

import com.example.authapp.dto.CartItemDTO;
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

public class CartRepository {
    private static final String SUPABASE_URL = "https://qsthuhzkciimucarscco.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFzdGh1aHprY2lpbXVjYXJzY2NvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTc0MjM4MTUsImV4cCI6MjA3Mjk5OTgxNX0.VnHrSq-S8NlSmzQ7_soRvrc7t3s3fEp_wu9tTwm9ZUI";
    private static final String TABLE_NAME = "cart_items";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    /**
     * ✅ ИСПРАВЛЕНО: Загружает корзину пользователя из Supabase
     */
    public static List<CartItemDTO> loadCartFromSupabase(String userId) throws Exception {
        try {
            String encodedUserId = java.net.URLEncoder.encode(userId, "UTF-8");
            String url = String.format("%s/rest/v1/%s?user_id=eq.%s&select=*",
                    SUPABASE_URL, TABLE_NAME, encodedUserId);

            System.out.println("📡 Загрузка корзины для user_id: " + userId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("❌ Ошибка: " + response.statusCode());
                return new ArrayList<>();
            }

            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
            List<CartItemDTO> items = new ArrayList<>();

            for (int i = 0; i < jsonArray.size(); i++) {
                CartItemDTO dto = gson.fromJson(jsonArray.get(i), CartItemDTO.class);
                items.add(dto);
                System.out.println("📦 Загруженный товар: " + dto.productName + " x" + dto.quantity);
            }

            System.out.println("✅ Корзина загружена: " + items.size() + " товаров");
            return items;

        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки корзины: " + e.getMessage());
            throw new Exception("Ошибка загрузки корзины из Supabase: " + e.getMessage());
        }
    }

    /**
     * ✅ ИСПРАВЛЕНО: Сохраняет товар в корзину на сервер
     * ВАЖНО: Не отправляем поле 'id' - оно сгенерируется автоматически на сервере
     */
    public static void addCartItemToSupabase(String userId, CartItemDTO cartItemDTO) throws Exception {
        try {
            // Создаем новый объект JSON БЕЗ поля 'id'
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("user_id", userId);
            jsonObject.addProperty("product_id", cartItemDTO.productId);
            jsonObject.addProperty("product_name", cartItemDTO.productName);
            jsonObject.addProperty("price", cartItemDTO.price);
            jsonObject.addProperty("quantity", cartItemDTO.quantity);
            jsonObject.addProperty("product_image", cartItemDTO.productImage);

            String url = String.format("%s/rest/v1/%s", SUPABASE_URL, TABLE_NAME);
            String jsonBody = jsonObject.toString();

            System.out.println("📤 Отправка товара в корзину: " + cartItemDTO.productName + " x" + cartItemDTO.quantity);
            System.out.println("   JSON (без ID): " + jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                System.err.println("❌ Ошибка добавления: " + response.statusCode());
                System.err.println("   Ответ: " + response.body());
                throw new Exception("Ошибка добавления товара: " + response.statusCode() + " " + response.body());
            }

            System.out.println("✅ Товар добавлен в корзину на сервер");

        } catch (Exception e) {
            System.err.println("❌ Ошибка при добавлении товара: " + e.getMessage());
            throw new Exception("Ошибка при добавлении товара в корзину: " + e.getMessage());
        }
    }

    /**
     * ✅ ИСПРАВЛЕНО: Обновляет количество товара в корзине
     */
    public static void updateCartItemInSupabase(int cartItemId, int newQuantity) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?id=eq.%d", SUPABASE_URL, TABLE_NAME, cartItemId);
            String jsonBody = "{\"quantity\":" + newQuantity + "}";

            System.out.println("🔄 Обновление товара в корзине: ID=" + cartItemId + ", новое количество=" + newQuantity);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                System.err.println("❌ Ошибка обновления: " + response.statusCode());
                throw new Exception("Ошибка обновления товара: " + response.statusCode());
            }

            System.out.println("✅ Товар обновлен (новое количество: " + newQuantity + ")");

        } catch (Exception e) {
            System.err.println("❌ Ошибка при обновлении товара: " + e.getMessage());
            throw new Exception("Ошибка при обновлении товара: " + e.getMessage());
        }
    }

    /**
     * ✅ ИСПРАВЛЕНО: Удаляет товар из корзины
     */
    public static void removeCartItemFromSupabase(int cartItemId) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?id=eq.%d", SUPABASE_URL, TABLE_NAME, cartItemId);

            System.out.println("➖ Удаление товара из корзины: ID=" + cartItemId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204) {
                System.err.println("❌ Ошибка удаления: " + response.statusCode());
                throw new Exception("Ошибка удаления товара: " + response.statusCode());
            }

            System.out.println("✅ Товар удален из корзины");

        } catch (Exception e) {
            System.err.println("❌ Ошибка при удалении товара: " + e.getMessage());
            throw new Exception("Ошибка при удалении товара: " + e.getMessage());
        }
    }

    /**
     * ✅ ИСПРАВЛЕНО: Очищает всю корзину пользователя
     */
    public static void clearUserCart(String userId) throws Exception {
        try {
            String encodedUserId = java.net.URLEncoder.encode(userId, "UTF-8");
            String url = String.format("%s/rest/v1/%s?user_id=eq.%s", SUPABASE_URL, TABLE_NAME, encodedUserId);

            System.out.println("🗑️ Очистка корзины на сервере для user_id: " + userId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204) {
                System.err.println("❌ Ошибка очистки: " + response.statusCode());
                throw new Exception("Ошибка очистки корзины: " + response.statusCode());
            }

            System.out.println("✅ Корзина очищена на сервере");

        } catch (Exception e) {
            System.err.println("❌ Ошибка при очистке корзины: " + e.getMessage());
            throw new Exception("Ошибка при очистке корзины: " + e.getMessage());
        }
    }
}
