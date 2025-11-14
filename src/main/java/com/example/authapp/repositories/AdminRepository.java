package com.example.authapp.repositories;

import com.example.authapp.dto.ProductDTO;
import com.example.authapp.dto.UserDTO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class AdminRepository {
    private static final String SUPABASE_URL = "https://qsthuhzkciimucarscco.supabase.co";
    private static final String SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFzdGh1aHprY2lpbXVjYXJzY2NvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTc0MjM4MTUsImV4cCI6MjA3Mjk5OTgxNX0.VnHrSq-S8NlSmzQ7_soRvrc7t3s3fEp_wu9tTwm9ZUI";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

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
                throw new Exception("Ошибка получения товаров: " + response.statusCode() + " " + response.body());
            }
            List<ProductDTO> products = new ArrayList<>();
            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
            for (int i = 0; i < jsonArray.size(); i++) {
                ProductDTO dto = gson.fromJson(jsonArray.get(i), ProductDTO.class);
                products.add(dto);
            }
            return products;
        } catch (Exception e) {
            e.printStackTrace();
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
                throw new Exception("Ошибка получения пользователей: " + response.statusCode() + " " + response.body());
            }
            List<UserDTO> users = new ArrayList<>();
            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
            for (int i = 0; i < jsonArray.size(); i++) {
                UserDTO dto = gson.fromJson(jsonArray.get(i), UserDTO.class);
                users.add(dto);
            }
            return users;
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Ошибка загрузки пользователей: " + e.getMessage());
        }
    }

    public static void addProduct(String name, String description, double price, int stock, String category, String manufacturer) throws Exception {
        try {
            String url = SUPABASE_URL + "/rest/v1/products";
            String jsonBody = "{" +
                "\"name\":\"" + name.replace("\"", "\\\"") + "\"," +
                "\"description\":\"" + description.replace("\"", "\\\"") + "\"," +
                "\"price\":" + price + "," +
                "\"stock\":" + stock + "," +
                "\"category\":\"" + category.replace("\"", "\\\"") + "\"," +
                "\"manufacturer\":\"" + manufacturer.replace("\"", "\\\"") + "\"," +
                "\"image_url\":\"\"" +
                "}";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + SUPABASE_KEY)
                .header("apikey", SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                throw new Exception("Ошибка добавления товара: " + response.statusCode() + " " + response.body());
            }
        } catch (Exception e) {
            throw new Exception("Ошибка добавления товара: " + e.getMessage());
        }
    }
}
