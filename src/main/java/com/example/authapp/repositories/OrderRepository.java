package com.example.authapp.repositories;

import config.Config;
import com.example.authapp.dto.OrderDTO;
import com.example.authapp.dto.OrderItemDTO;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class OrderRepository {

    /**
     * ✅ Создание заказа с промокодом и final_amount
     */
    public static int createOrder(String userId, double totalAmount, Integer promoCodeId, double discountAmount) throws Exception {
        try {
            String urlString = Config.SUPABASE_URL + "/rest/v1/orders";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Prefer", "return=representation");
            conn.setDoOutput(true);

            JSONObject json = new JSONObject();
            json.put("user_id", userId);
            json.put("total_amount", totalAmount);
            json.put("status", "pending");

            // ✅ ВЫЧИСЛЯЕМ И ДОБАВЛЯЕМ ИТОГОВУЮ СУММУ
            double finalAmount = totalAmount - discountAmount;
            json.put("final_amount", finalAmount);

            // ✅ Добавляем промокод и скидку
            if (promoCodeId != null && promoCodeId > 0) {
                json.put("promo_code_id", promoCodeId);
                json.put("discount_amount", discountAmount);
            }

            System.out.println("📤 Отправка заказа: " + json.toString());

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 201) {
                Scanner errorScanner = new Scanner(conn.getErrorStream(), StandardCharsets.UTF_8);
                String errorResponse = errorScanner.useDelimiter("\\A").next();
                errorScanner.close();
                throw new Exception("Ошибка создания заказа: " + errorResponse);
            }

            Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8);
            String response = scanner.useDelimiter("\\A").next();
            scanner.close();

            JSONArray jsonArray = new JSONArray(response);
            JSONObject orderJson = jsonArray.getJSONObject(0);
            int orderId = orderJson.getInt("id");

            System.out.println("✅ Заказ создан с ID: " + orderId);
            return orderId;

        } catch (Exception e) {
            System.err.println("❌ Ошибка создания заказа: " + e.getMessage());
            throw e;
        }
    }

    /**
     * ✅ Добавление товара в заказ с product_name, product_image и subtotal
     */
    public static void addOrderItem(int orderId, int productId, String productName,
                                    String productImage, int quantity, double price) throws Exception {
        try {
            String urlString = Config.SUPABASE_URL + "/rest/v1/order_items";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // ✅ РАССЧИТЫВАЕМ ПРОМЕЖУТОЧНЫЙ ИТОГ
            double subtotal = price * quantity;

            JSONObject json = new JSONObject();
            json.put("order_id", orderId);
            json.put("product_id", productId);
            json.put("product_name", productName);
            json.put("product_image", productImage);
            json.put("quantity", quantity);
            json.put("price", price);
            json.put("subtotal", subtotal);

            System.out.println("📤 Отправка товара в заказ: " + json.toString());

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 201) {
                Scanner errorScanner = new Scanner(conn.getErrorStream(), StandardCharsets.UTF_8);
                String errorResponse = errorScanner.useDelimiter("\\A").next();
                errorScanner.close();
                throw new Exception("Не удалось добавить товар в заказ: " + errorResponse);
            }

            System.out.println("✅ Товар добавлен в заказ (subtotal: " + subtotal + ")");

        } catch (Exception e) {
            System.err.println("❌ Ошибка добавления товара: " + e.getMessage());
            throw new Exception("Ошибка добавления товара: " + e.getMessage());
        }
    }

    /**
     * ✅ Получение заказов пользователя
     */
    public static List<OrderDTO> getUserOrders(String userId) throws Exception {
        try {
            String urlString = Config.SUPABASE_URL + "/rest/v1/orders?user_id=eq." + userId + "&order=created_at.desc&select=*";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("Ошибка загрузки заказов");
            }

            Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8);
            String response = scanner.useDelimiter("\\A").next();
            scanner.close();

            JSONArray jsonArray = new JSONArray(response);
            List<OrderDTO> orders = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);

                OrderDTO order = new OrderDTO();
                order.orderId = obj.getInt("id");
                order.userId = obj.getString("user_id");
                order.totalAmount = obj.getDouble("total_amount");
                order.status = obj.getString("status");
                order.orderDate = obj.getString("created_at");

                // Промокод данные
                order.promoCodeId = obj.has("promo_code_id") && !obj.isNull("promo_code_id")
                        ? obj.getInt("promo_code_id") : null;
                order.discountAmount = obj.has("discount_amount") && !obj.isNull("discount_amount")
                        ? obj.getDouble("discount_amount") : 0.0;
                order.finalAmount = obj.has("final_amount")
                        ? obj.getDouble("final_amount") : order.totalAmount;

                // Загружаем товары заказа
                order.items = getOrderItems(order.orderId);

                orders.add(order);
            }

            return orders;

        } catch (Exception e) {
            throw new Exception("Ошибка при загрузке заказов: " + e.getMessage());
        }
    }

    /**
     * ✅ Получение товаров заказа
     */
    public static List<OrderItemDTO> getOrderItems(int orderId) throws Exception {
        try {
            String urlString = Config.SUPABASE_URL + "/rest/v1/order_items?order_id=eq." + orderId + "&select=*";
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", Config.SUPABASE_ANON_KEY);
            conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_ANON_KEY);

            Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8);
            String response = scanner.useDelimiter("\\A").next();
            scanner.close();

            JSONArray jsonArray = new JSONArray(response);
            List<OrderItemDTO> items = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);

                OrderItemDTO item = new OrderItemDTO();
                item.id = obj.getInt("id");
                item.orderId = obj.getInt("order_id");
                item.productId = obj.getInt("product_id");
                item.productName = obj.getString("product_name");
                item.productImage = obj.getString("product_image");
                item.price = obj.getDouble("price");
                item.quantity = obj.getInt("quantity");
                item.subtotal = obj.getDouble("subtotal");

                items.add(item);
            }

            return items;

        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
