package com.example.authapp.repositories;

import com.example.authapp.dto.UserDTO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class UserRepository {
    private static final String SUPABASE_URL = System.getenv("SUPABASE_URL") != null
            ? System.getenv("SUPABASE_URL")
            : "https://qsthuhzkciimucarscco.supabase.co";
    private static final String SUPABASE_KEY = System.getenv("SUPABASE_KEY") != null
            ? System.getenv("SUPABASE_KEY")
            : "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFzdGh1aHprY2lpbXVjYXJzY2NvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTc0MjM4MTUsImV4cCI6MjA3Mjk5OTgxNX0.VnHrSq-S8NlSmzQ7_soRvrc7t3s3fEp_wu9tTwm9ZUI";
    private static final String TABLE_NAME = "profiles";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    /**
     * Получает профиль пользователя по email
     * Поддерживает поля: id, name, surname, email, phone, city, address
     */
    public static UserDTO getUserProfileByEmail(String email) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?email=eq.%s&select=*", SUPABASE_URL, TABLE_NAME, email);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("📡 Запрос профиля (" + email + "): " + response.statusCode());

            if (response.statusCode() == 200) {
                String body = response.body();
                System.out.println("📦 Ответ: " + body);

                if (!body.equals("[]")) {
                    JsonArray jsonArray = JsonParser.parseString(body).getAsJsonArray();
                    if (jsonArray.size() > 0) {
                        UserDTO user = gson.fromJson(jsonArray.get(0), UserDTO.class);
                        System.out.println("✅ Профиль загружен: " + user.name);
                        return user;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            System.err.println("❌ Ошибка получения профиля: " + e.getMessage());
            throw new Exception("Ошибка получения профиля: " + e.getMessage());
        }
    }

    /**
     * Получает профиль пользователя по ID
     */
    public static UserDTO getUserProfile(String userId) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?id=eq.%s&select=*", SUPABASE_URL, TABLE_NAME, userId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && !response.body().equals("[]")) {
                JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
                if (jsonArray.size() > 0) {
                    return gson.fromJson(jsonArray.get(0), UserDTO.class);
                }
            }
            return null;
        } catch (Exception e) {
            throw new Exception("Ошибка получения профиля: " + e.getMessage());
        }
    }

    /**
     * Обновляет профиль пользователя
     * Поддерживает поля: name, surname, phone, city, address
     */
    public static void updateUserProfile(String email, String name, String surname,
                                         String phone, String city, String address) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?email=eq.%s", SUPABASE_URL, TABLE_NAME, email);

            // Экранируем кавычки в строках
            name = name != null ? name.replace("\"", "\\\"") : "";
            surname = surname != null ? surname.replace("\"", "\\\"") : "";
            phone = phone != null ? phone.replace("\"", "\\\"") : "";
            city = city != null ? city.replace("\"", "\\\"") : "";
            address = address != null ? address.replace("\"", "\\\"") : "";

            String jsonBody = String.format(
                    "{\"name\":\"%s\",\"surname\":\"%s\",\"phone\":\"%s\",\"city\":\"%s\",\"address\":\"%s\"}",
                    name, surname, phone, city, address);

            System.out.println("📤 Обновление профиля: " + jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new Exception("Ошибка обновления: " + response.statusCode());
            }

            System.out.println("✅ Профиль обновлен");
        } catch (Exception e) {
            System.err.println("❌ Ошибка обновления профиля: " + e.getMessage());
            throw new Exception("Ошибка обновления профиля: " + e.getMessage());
        }
    }
}