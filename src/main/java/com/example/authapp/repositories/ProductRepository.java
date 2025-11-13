package com.example.authapp.repositories;

import com.example.authapp.models.Product;
import com.example.authapp.dto.ProductDTO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class ProductRepository {
    private static final String SUPABASE_URL = "https://qsthuhzkciimucarscco.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFzdGh1aHprY2lpbXVjYXJzY2NvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTc0MjM4MTUsImV4cCI6MjA3Mjk5OTgxNX0.VnHrSq-S8NlSmzQ7_soRvrc7t3s3fEp_wu9tTwm9ZUI";
    static final String TABLE_NAME = "products";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    static final Gson gson = new Gson();

    /**
     * Загружает товары из Supabase
     * ВАЖНО: Используем ProductDTO для Gson, потом конвертируем в Product для UI
     */
    public static List<Product> loadProductsFromSupabase() throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?select=*", SUPABASE_URL, TABLE_NAME);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new Exception("Ошибка получения товаров: " + response.statusCode());
            }

            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
            List<Product> products = new ArrayList<>();

            // Конвертируем каждый DTO в Product для UI
            for (int i = 0; i < jsonArray.size(); i++) {
                ProductDTO dto = gson.fromJson(jsonArray.get(i), ProductDTO.class);

                // Создаем Product с Property для UI из простого DTO
                Product product = new Product(
                        dto.id,
                        dto.name,
                        dto.description,
                        dto.price,
                        dto.stock,
                        dto.imageUrl != null ? dto.imageUrl : "",
                        dto.category != null ? dto.category : "",
                        dto.manufacturer != null ? dto.manufacturer : ""
                );

                products.add(product);
            }

            return products;
        } catch (Exception e) {
            throw new Exception("Ошибка загрузки товаров из Supabase: " + e.getMessage());
        }
    }

    public static void addProductToSupabase(Product product) throws Exception {
        try {
            // Конвертируем Product в ProductDTO для JSON
            ProductDTO dto = new ProductDTO(
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getPrice(),
                    product.getStock(),
                    product.getImageUrl(),
                    product.getCategory(),
                    product.getManufacturer()
            );

            String url = String.format("%s/rest/v1/%s", SUPABASE_URL, TABLE_NAME);
            String jsonBody = gson.toJson(dto);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                throw new Exception("Ошибка добавления товара: " + response.statusCode());
            }
        } catch (Exception e) {
            throw new Exception("Ошибка при добавлении товара в Supabase: " + e.getMessage());
        }
    }

    public static void updateProductInSupabase(Product product) throws Exception {
        try {
            ProductDTO dto = new ProductDTO(
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getPrice(),
                    product.getStock(),
                    product.getImageUrl(),
                    product.getCategory(),
                    product.getManufacturer()
            );

            String url = String.format("%s/rest/v1/%s?id=eq.%d", SUPABASE_URL, TABLE_NAME, product.getId());
            String jsonBody = gson.toJson(dto);

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
        } catch (Exception e) {
            throw new Exception("Ошибка при обновлении товара в Supabase: " + e.getMessage());
        }
    }

    public static void deleteProductFromSupabase(int productId) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?id=eq.%d", SUPABASE_URL, TABLE_NAME, productId);

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
        } catch (Exception e) {
            throw new Exception("Ошибка при удалении товара из Supabase: " + e.getMessage());
        }
    }

    public List<Product> getAllProducts() throws Exception {
        return loadProductsFromSupabase();
    }

    public void addProduct(Product product) throws Exception {
        addProductToSupabase(product);
    }

    public void updateProduct(Product product) throws Exception {
        updateProductInSupabase(product);
    }

    public void deleteProduct(int productId) throws Exception {
        deleteProductFromSupabase(productId);
    }
}