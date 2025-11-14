package com.example.authapp.repositories;

import com.example.authapp.dto.CartItemDTO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
     * Загружает корзину пользователя из Supabase (работает с UUID строки)
     */
    public static List<CartItemDTO> loadCartFromSupabase(String userId) throws Exception {
        try {
            // Экранируем UUID для использования в URL
            String encodedUserId = java.net.URLEncoder.encode(userId, "UTF-8");
            String url = String.format("%s/rest/v1/%s?user_id=eq.%s&select=*", SUPABASE_URL, TABLE_NAME, encodedUserId);

            System.out.println("📡 Загрузка корзины для user_id: " + userId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new Exception("Ошибка получения корзины: " + response.statusCode() + " " + response.body());
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
            throw new Exception("Ошибка загрузки корзины из Supabase: " + e.getMessage());
        }
    }

    /**
     * Сохраняет товар в корзину на сервер (работает с UUID строки)
     */
    public static void addCartItemToSupabase(String userId, CartItemDTO cartItemDTO) throws Exception {
        try {
            cartItemDTO.userId = userId; // Устанавливаем UUID строку

            String url = String.format("%s/rest/v1/%s", SUPABASE_URL, TABLE_NAME);

            String jsonBody = gson.toJson(cartItemDTO);
            System.out.println("📤 Отправка: " + jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                throw new Exception("Ошибка добавления товара в корзину: " + response.statusCode() + " " + response.body());
            }

            System.out.println("✅ Товар добавлен в корзину на сервер");

        } catch (Exception e) {
            throw new Exception("Ошибка при добавлении товара в корзину Supabase: " + e.getMessage());
        }
    }

    /**
     * Обновляет количество товара в корзине
     */
    public static void updateCartItemInSupabase(int cartItemId, int newQuantity) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?id=eq.%d", SUPABASE_URL, TABLE_NAME, cartItemId);

            String jsonBody = "{\"quantity\":" + newQuantity + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new Exception("Ошибка обновления товара: " + response.statusCode());
            }

            System.out.println("🔄 Товар обновлен в корзине на сервер (количество: " + newQuantity + ")");

        } catch (Exception e) {
            throw new Exception("Ошибка при обновлении товара в корзине Supabase: " + e.getMessage());
        }
    }

    /**
     * Удаляет товар из корзины
     */
    public static void removeCartItemFromSupabase(int cartItemId) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?id=eq.%d", SUPABASE_URL, TABLE_NAME, cartItemId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204) {
                throw new Exception("Ошибка удаления товара: " + response.statusCode());
            }

            System.out.println("➖ Товар удален из корзины на сервер");

        } catch (Exception e) {
            throw new Exception("Ошибка при удалении товара из корзины Supabase: " + e.getMessage());
        }
    }

    /**
     * Очищает всю корзину пользователя (работает с UUID строки)
     */
    public static void clearUserCart(String userId) throws Exception {
        try {
            String encodedUserId = java.net.URLEncoder.encode(userId, "UTF-8");
            String url = String.format("%s/rest/v1/%s?user_id=eq.%s", SUPABASE_URL, TABLE_NAME, encodedUserId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204) {
                throw new Exception("Ошибка очистки корзины: " + response.statusCode());
            }

            System.out.println("🗑️ Корзина очищена на сервер");

        } catch (Exception e) {
            throw new Exception("Ошибка при очистке корзины на Supabase: " + e.getMessage());
        }
    }
}
