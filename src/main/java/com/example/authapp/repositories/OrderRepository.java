package com.example.authapp.repositories;

import com.example.authapp.dto.OrderDTO;
import com.example.authapp.dto.OrderItemDTO;
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

public class OrderRepository {
    private static final String SUPABASE_URL = "https://qsthuhzkciimucarscco.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFzdGh1aHprY2lpbXVjYXJzY2NvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTc0MjM4MTUsImV4cCI6MjA3Mjk5OTgxNX0.VnHrSq-S8NlSmzQ7_soRvrc7t3s3fEp_wu9tTwm9ZUI";
    private static final String ORDERS_TABLE = "orders";
    private static final String ORDER_ITEMS_TABLE = "order_items";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    /**
     * Создает заказ в таблице orders БЕЗ ID (он генерируется автоматически)
     */
    public static int createOrder(OrderDTO orderDTO) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s", SUPABASE_URL, ORDERS_TABLE);

            // Создаем JSON БЕЗ поля ID
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("user_id", orderDTO.userId);
            jsonObject.addProperty("total_amount", orderDTO.totalAmount);
            jsonObject.addProperty("discount_amount", orderDTO.discountAmount);
            jsonObject.addProperty("final_amount", orderDTO.finalAmount);
            jsonObject.addProperty("promo_code", orderDTO.promoCode);
            jsonObject.addProperty("status", "pending");

            String jsonBody = jsonObject.toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                System.err.println("❌ Ошибка создания заказа: " + response.statusCode());
                System.err.println("   Ответ: " + response.body());
                throw new Exception("Ошибка создания заказа: " + response.statusCode() + " " + response.body());
            }

            // Парсим ответ и получаем ID заказа
            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
            JsonObject createdOrder = jsonArray.get(0).getAsJsonObject();
            int orderId = createdOrder.get("id").getAsInt();
            return orderId;

        } catch (Exception e) {
            System.err.println("❌ Ошибка при создании заказа: " + e.getMessage());
            throw new Exception("Ошибка при создании заказа: " + e.getMessage());
        }
    }

    /**
     * Добавляет товары в таблицу order_items БЕЗ ID (он генерируется автоматически)
     */
    public static void addOrderItems(int orderId, List<OrderItemDTO> items) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s", SUPABASE_URL, ORDER_ITEMS_TABLE);

            // Создаем массив JSON объектов БЕЗ ID
            JsonArray jsonArray = new JsonArray();

            for (OrderItemDTO item : items) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("order_id", orderId);
                jsonObject.addProperty("product_id", item.productId);
                jsonObject.addProperty("product_name", item.productName);
                jsonObject.addProperty("product_image", item.productImage);
                jsonObject.addProperty("price", item.price);
                jsonObject.addProperty("quantity", item.quantity);
                jsonObject.addProperty("subtotal", item.subtotal);
                jsonArray.add(jsonObject);
            }

            String jsonBody = jsonArray.toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                System.err.println("❌ Ошибка добавления товаров: " + response.statusCode());
                System.err.println("   Ответ: " + response.body());
                throw new Exception("Ошибка добавления товаров: " + response.statusCode() + " " + response.body());
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка при добавлении товаров: " + e.getMessage());
            throw new Exception("Ошибка при добавлении товаров: " + e.getMessage());
        }
    }

    /**
     * Получает все заказы пользователя
     */
    public static List<OrderDTO> getUserOrders(String userId) throws Exception {
        try {
            String encodedUserId = java.net.URLEncoder.encode(userId, "UTF-8");
            String url = String.format("%s/rest/v1/%s?user_id=eq.%s&order=order_date.desc",
                    SUPABASE_URL, ORDERS_TABLE, encodedUserId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("❌ Ошибка получения заказов: " + response.statusCode());
                return new ArrayList<>();
            }

            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
            List<OrderDTO> orders = new ArrayList<>();

            for (int i = 0; i < jsonArray.size(); i++) {
                OrderDTO order = gson.fromJson(jsonArray.get(i), OrderDTO.class);
                order.items = getOrderItems(order.orderId);
                orders.add(order);
            }

            return orders;

        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки заказов: " + e.getMessage());
            throw new Exception("Ошибка загрузки заказов: " + e.getMessage());
        }
    }

    public static List<OrderItemDTO> getOrderItems(int orderId) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?order_id=eq.%d",
                    SUPABASE_URL, ORDER_ITEMS_TABLE, orderId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("❌ Ошибка получения товаров заказа: " + response.statusCode());
                return new ArrayList<>();
            }

            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
            List<OrderItemDTO> items = new ArrayList<>();

            for (int i = 0; i < jsonArray.size(); i++) {
                OrderItemDTO item = gson.fromJson(jsonArray.get(i), OrderItemDTO.class);
                items.add(item);
            }

            return items;

        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки товаров заказа: " + e.getMessage());
            throw new Exception("Ошибка загрузки товаров заказа: " + e.getMessage());
        }
    }

    public static void updateOrderStatus(int orderId, String newStatus) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?id=eq.%d", SUPABASE_URL, ORDERS_TABLE, orderId);
            String jsonBody = "{\"status\":\"" + newStatus + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                throw new Exception("Ошибка обновления статуса: " + response.statusCode());
            }

        } catch (Exception e) {
            throw new Exception("Ошибка при обновлении статуса заказа: " + e.getMessage());
        }
    }
}
