package com.example.authapp.repositories;

import com.example.authapp.models.Product;
import com.example.authapp.dto.ProductDTO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.example.authapp.network.SupabaseHttp;
import config.SessionManager;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class ProductRepository {
    private static final String SUPABASE_URL = config.Config.SUPABASE_URL;
    private static final String SUPABASE_KEY = config.Config.SUPABASE_ANON_KEY;
    static final String TABLE_NAME = "products";
        static final Gson gson = new Gson();

    public static List<Product> loadProductsFromSupabase() throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?select=*", SUPABASE_URL, TABLE_NAME);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() != 200) {
                throw new Exception("Ошибка получения товаров: " + response.statusCode());
            }

            JsonArray jsonArray = JsonParser.parseString(com.example.authapp.network.SupabaseHttp.jsonBody(response)).getAsJsonArray();
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
        var lease = SessionManager.requireSession();
        try (var scope = SupabaseHttp.forSession(lease)) {
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
            JsonObject payload = gson.toJsonTree(dto).getAsJsonObject();
            payload.remove("id"); // IDs are assigned by the database and cannot be changed by REST clients.
            String jsonBody = payload.toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() != 201 && response.statusCode() != 200) {
                throw new Exception("Ошибка добавления товара: " + response.statusCode());
            }
        } catch (Exception e) {
            throw new Exception("Ошибка при добавлении товара в Supabase: " + e.getMessage());
        }
    }

    public static void updateProductInSupabase(Product product) throws Exception {
        var lease = SessionManager.requireSession();
        try (var scope = SupabaseHttp.forSession(lease)) {
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
            JsonObject payload = gson.toJsonTree(dto).getAsJsonObject();
            payload.remove("id"); // IDs are assigned by the database and cannot be changed by REST clients.
            String jsonBody = payload.toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                throw new Exception("Ошибка обновления товара: " + response.statusCode());
            }
        } catch (Exception e) {
            throw new Exception("Ошибка при обновлении товара в Supabase: " + e.getMessage());
        }
    }

    public static void deleteProductFromSupabase(int productId) throws Exception {
        AdminRepository.deleteProduct(productId);
    }

    public static void decreaseProductStock(int productId, int quantity) {
        throw new UnsupportedOperationException("Остатки изменяются атомарно при оформлении заказа");
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