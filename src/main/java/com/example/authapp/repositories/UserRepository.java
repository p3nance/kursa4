package com.example.authapp.repositories;

import com.example.authapp.dto.UserDTO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
     * ✅ СОЗДАЕТ НОВЫЙ ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ (с пустыми полями)
     * Вызывается СРАЗУ ПОСЛЕ УСПЕШНОЙ РЕГИСТРАЦИИ в AuthController
     * Все остальные поля пользователь заполнит в личном кабинете
     */
    public static void createUserProfile(String userId, String email, String name, String surname) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s", SUPABASE_URL, TABLE_NAME);

            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("id", userId);
            jsonObject.addProperty("email", email);
            jsonObject.addProperty("name", name != null ? name : "");
            jsonObject.addProperty("surname", surname != null ? surname : "");
            jsonObject.addProperty("phone", "");
            jsonObject.addProperty("city", "");
            jsonObject.addProperty("address", "");
            jsonObject.addProperty("is_admin", false);

            String jsonBody = jsonObject.toString();

            System.out.println("📤 Создание профиля: " + jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .method("POST", HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("📡 Статус создания профиля: " + response.statusCode());
            System.out.println("📦 Ответ сервера: " + response.body());

            // ✅ Статус 201 (Created) или 200 (OK)
            if (response.statusCode() == 201 || response.statusCode() == 200) {
                System.out.println("✅ Профиль успешно создан для пользователя: " + email);
            } else {
                System.err.println("❌ Ошибка создания профиля!");
                System.err.println("   Статус: " + response.statusCode());
                System.err.println("   Тело ответа: " + response.body());
                throw new Exception("Ошибка создания профиля: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка создания профиля: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Ошибка создания профиля: " + e.getMessage());
        }
    }

    /**
     * ✅ Получает профиль пользователя по email
     * Поддерживает поля: id, name, surname, email, phone, city, address
     */
    public static UserDTO getUserProfileByEmail(String email) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?email=eq.%s&select=*", SUPABASE_URL, TABLE_NAME, email);

            System.out.println("🔍 Запрос профиля по email: " + email);
            System.out.println("   URL: " + url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("📡 Статус запроса профиля: " + response.statusCode());
            System.out.println("📦 Ответ: " + response.body());

            if (response.statusCode() == 200) {
                String body = response.body();

                if (body.equals("[]")) {
                    System.out.println("⚠️ Профиль не найден для email: " + email);
                    return null;
                }

                JsonArray jsonArray = JsonParser.parseString(body).getAsJsonArray();
                if (jsonArray.size() > 0) {
                    UserDTO user = gson.fromJson(jsonArray.get(0), UserDTO.class);
                    System.out.println("✅ Профиль загружен: " + user.name + " " + user.surname);
                    return user;
                }
            } else {
                System.err.println("❌ Ошибка GET запроса. Статус: " + response.statusCode());
            }

            return null;
        } catch (Exception e) {
            System.err.println("❌ Ошибка получения профиля: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Ошибка получения профиля: " + e.getMessage());
        }
    }

    /**
     * ✅ Получает профиль пользователя по ID
     */
    public static UserDTO getUserProfile(String userId) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?id=eq.%s&select=*", SUPABASE_URL, TABLE_NAME, userId);

            System.out.println("🔍 Запрос профиля по id: " + userId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("📡 Статус: " + response.statusCode());
            System.out.println("📦 Ответ: " + response.body());

            if (response.statusCode() == 200 && !response.body().equals("[]")) {
                JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
                if (jsonArray.size() > 0) {
                    UserDTO user = gson.fromJson(jsonArray.get(0), UserDTO.class);
                    System.out.println("✅ Профиль загружен по ID: " + user.email);
                    return user;
                }
            }

            System.out.println("⚠️ Профиль не найден по ID: " + userId);
            return null;
        } catch (Exception e) {
            System.err.println("❌ Ошибка получения профиля: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Ошибка получения профиля: " + e.getMessage());
        }
    }

    /**
     * ✅ Обновляет профиль пользователя по email
     * Поддерживает поля: name, surname, phone, city, address
     */
    public static void updateUserProfile(String email, String name, String surname,
                                         String phone, String city, String address) throws Exception {
        try {
            String url = String.format("%s/rest/v1/%s?email=eq.%s", SUPABASE_URL, TABLE_NAME, email);

            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("name", name != null ? name : "");
            jsonObject.addProperty("surname", surname != null ? surname : "");
            jsonObject.addProperty("phone", phone != null ? phone : "");
            jsonObject.addProperty("city", city != null ? city : "");
            jsonObject.addProperty("address", address != null ? address : "");

            String jsonBody = jsonObject.toString();

            System.out.println("📤 Обновление профиля для: " + email);
            System.out.println("   Данные: " + jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("📡 Статус обновления: " + response.statusCode());
            System.out.println("📦 Ответ: " + response.body());

            // ✅ Статус 200 (OK) или 204 (No Content) - оба OK
            if (response.statusCode() == 200 || response.statusCode() == 204) {
                System.out.println("✅ Профиль успешно обновлен");
            } else {
                System.err.println("❌ Ошибка обновления профиля!");
                System.err.println("   Статус: " + response.statusCode());
                System.err.println("   Тело ответа: " + response.body());
                throw new Exception("Ошибка обновления: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка обновления профиля: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Ошибка обновления профиля: " + e.getMessage());
        }
    }
}