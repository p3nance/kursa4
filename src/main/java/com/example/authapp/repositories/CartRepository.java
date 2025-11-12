package com.example.authapp.repositories;

import com.example.authapp.models.Cart.CartItem;
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

/**
 * Репозиторий для работы с корзиной в Supabase
   * Загружает и сохраняет товары корзины на сервер
   */
public class CartRepository {
      private static final String SUPABASE_URL = "https://qsthuhzkciimucarscco.supabase.co";
      private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFzdGh1aHprY2lpbXVjYXJzY2NvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTc0MjM4MTUsImV4cCI6MjA3Mjk5OTgxNX0.VnHrSq-S8NlSmzQ7_soRvrc7t3s3fEp_wu9tTwm9ZUI";
      private static final String TABLE_NAME = "cart_items";
      private static final HttpClient httpClient = HttpClient.newHttpClient();
      private static final Gson gson = new Gson();

    /**
     * Загружает корзину пользователя из Supabase
       */
    public static List<CartItem> loadCartFromSupabase(int userId) throws Exception {
              try {
                            String url = String.format("%s/rest/v1/%s?user_id=eq.%d&select=*", SUPABASE_URL, TABLE_NAME, userId);
                            HttpRequest request = HttpRequest.newBuilder()
                                                  .uri(URI.create(url))
                                                  .header("Authorization", "Bearer " + SUPABASE_KEY)
                                                  .header("apikey", SUPABASE_KEY)
                                                  .GET()
                                                  .build();
                            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                            if (response.statusCode() != 200) {
                                              throw new Exception("Ошибка получения корзины: " + response.statusCode());
                                          }
                            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
                            List<CartItem> items = new ArrayList<>();
                            for (int i = 0; i < jsonArray.size(); i++) {
                                              CartItemDTO dto = gson.fromJson(jsonArray.get(i), CartItemDTO.class);
                                              System.out.println("📦 Загруженный товар: " + dto.productName + " x" + dto.quantity);
                                          }
                            return items;
                        } catch (Exception e) {
                            throw new Exception("Ошибка загрузки корзины из Supabase: " + e.getMessage());
                        }
          }

    /**
     * Сохраняет товар в корзину на сервер
     */
    public static void addCartItemToSupabase(int userId, CartItemDTO cartItemDTO) throws Exception {
              try {
                            String url = String.format("%s/rest/v1/%s", SUPABASE_URL, TABLE_NAME);
                            String jsonBody = gson.toJson(cartItemDTO);
                            HttpRequest request = HttpRequest.newBuilder()
                                                  .uri(URI.create(url))
                                                  .header("Authorization", "Bearer " + SUPABASE_KEY)
                                                  .header("apikey", SUPABASE_KEY)
                                                  .header("Content-Type", "application/json")
                                                  .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                                                  .build();
                            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                            if (response.statusCode() != 201) {
                                              throw new Exception("Ошибка добавления товара в корзину: " + response.statusCode());
                                          }
                            System.out.println("✅ Товар добавлен в корзину на сервер");
                        } catch (Exception e) {
                            throw new Exception("Ошибка при добавлении товара в корзину Supabase: " + e.getMessage());
                        }
          }
      }

    /**
     * Обновляет количество товара в корзине
           */
    public static void updateCartItemInSupabase(int cartItemId, int newQuantity) throws Exception {
              try {
                            String url = String.format("%s/rest/v1/%s?id=eq.%d", SUPABASE_URL, TABLE_NAME, cartItemId);
                            CartItemDTO updateDto = new CartItemDTO();
                            updateDto.quantity = newQuantity;
                            String jsonBody = gson.toJson(updateDto);
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
                            System.out.println("🔄 Товар обновлен в корзине на сервер");
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
     * Очищает всю корзину пользователя
           */
    public static void clearUserCart(int userId) throws Exception {
              try {
                            String url = String.format("%s/rest/v1/%s?user_id=eq.%d", SUPABASE_URL, TABLE_NAME, userId);
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
                            System.out.println("🗑 Корзина очищена на сервер");
                        } catch (Exception e) {
                            throw new Exception("Ошибка при очистке корзины на Supabase: " + e.getMessage());
                        }
          }
}
