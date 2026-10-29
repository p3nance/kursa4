package com.example.authapp.repositories;
import config.Config;
import config.SessionManager;
import com.example.authapp.network.SupabaseHttp;
import java.util.Objects;
import com.example.authapp.dto.UserDTO;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class UserRepository {
    private static final String SUPABASE_URL = config.Config.SUPABASE_URL;
    private static final String SUPABASE_KEY = config.Config.SUPABASE_ANON_KEY;
    private static final String TABLE_NAME = "profiles";
        private static final Gson gson = new Gson();

    /**
     * ✅ СОЗДАЕТ НОВЫЙ ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ (с пустыми полями)
     * Вызывается СРАЗУ ПОСЛЕ УСПЕШНОЙ РЕГИСТРАЦИИ в AuthController
     * Все остальные поля пользователь заполнит в личном кабинете
     */
    public static void createUserProfile(String userId, String email, String name, String surname) throws Exception {
        var lease = SessionManager.requireSession();
        try (var scope = SupabaseHttp.forSession(lease)) {
            if (!Objects.equals(userId, lease.userId())) throw new IllegalStateException("Можно получить или изменить только свой профиль");
            if (!Objects.equals(email, SessionManager.getUserEmail())) throw new IllegalStateException("Можно получить или изменить только свой профиль");
            String url = String.format("%s/rest/v1/%s", SUPABASE_URL, TABLE_NAME);

            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("id", userId);
            jsonObject.addProperty("email", email);
            jsonObject.addProperty("name", name != null ? name : "");
            jsonObject.addProperty("surname", surname != null ? surname : "");
            jsonObject.addProperty("phone", "");
            jsonObject.addProperty("city", "");
            jsonObject.addProperty("address", "");


            String jsonBody = jsonObject.toString();


            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=representation")
                    .method("POST", HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            // ✅ Статус 201 (Created) или 200 (OK)
            if (response.statusCode() == 201 || response.statusCode() == 200) {
            } else {
                System.err.println("❌ Ошибка создания профиля!");
                System.err.println("   Статус: " + response.statusCode());

                throw new Exception("Ошибка создания профиля: " + response.statusCode());
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
        var lease = SessionManager.requireSession();
        try (var scope = SupabaseHttp.forSession(lease)) {
            if (!Objects.equals(email, SessionManager.getUserEmail())) throw new IllegalStateException("Можно получить или изменить только свой профиль");
            String url = String.format("%s/rest/v1/%s?id=eq.%s&select=*", SUPABASE_URL, TABLE_NAME, SupabaseHttp.queryValue(lease.userId()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() == 200) {
                String body = response.body();

                if (body.equals("[]")) {
                    return null;
                }

                JsonArray jsonArray = JsonParser.parseString(body).getAsJsonArray();
                if (jsonArray.size() > 0) {
                    UserDTO user = gson.fromJson(jsonArray.get(0), UserDTO.class);
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
        var lease = SessionManager.requireSession();
        try (var scope = SupabaseHttp.forSession(lease)) {
            if (!Objects.equals(userId, lease.userId())) throw new IllegalStateException("Можно получить или изменить только свой профиль");
            String url = String.format("%s/rest/v1/%s?id=eq.%s&select=*", SUPABASE_URL, TABLE_NAME, SupabaseHttp.queryValue(lease.userId()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() == 200 && !response.body().equals("[]")) {
                JsonArray jsonArray = JsonParser.parseString(com.example.authapp.network.SupabaseHttp.jsonBody(response)).getAsJsonArray();
                if (jsonArray.size() > 0) {
                    UserDTO user = gson.fromJson(jsonArray.get(0), UserDTO.class);
                    return user;
                }
            }

            return null;
        } catch (Exception e) {
            System.err.println("❌ Ошибка получения профиля: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Ошибка получения профиля: " + e.getMessage());
        }
    }

    public static void updateUserProfile(String email, String name, String surname,
                                         String phone, String city, String address) throws Exception {
        var lease = SessionManager.requireSession();
        try (var scope = SupabaseHttp.forSession(lease)) {
            if (!Objects.equals(email, SessionManager.getUserEmail())) throw new IllegalStateException("Можно получить или изменить только свой профиль");
            String url = String.format("%s/rest/v1/%s?id=eq.%s", SUPABASE_URL, TABLE_NAME, SupabaseHttp.queryValue(lease.userId()));

            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("name", name != null ? name : "");
            jsonObject.addProperty("surname", surname != null ? surname : "");
            jsonObject.addProperty("phone", phone != null ? phone : "");
            jsonObject.addProperty("city", city != null ? city : "");
            jsonObject.addProperty("address", address != null ? address : "");

            String jsonBody = jsonObject.toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + com.example.authapp.network.SupabaseHttp.authorization())
                    .header("apikey", SUPABASE_KEY)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = com.example.authapp.network.SupabaseHttp.send(request);

            if (response.statusCode() == 200 || response.statusCode() == 204) {
            } else {
                System.err.println("❌ Ошибка обновления профиля!");
                System.err.println("   Статус: " + response.statusCode());

                throw new Exception("Ошибка обновления: " + response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка обновления профиля: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Ошибка обновления профиля: " + e.getMessage());
        }
    }
    public static void verifyPassword(String email, String password) throws Exception {
        var lease = config.SessionManager.requireSession();
        try (var scope = com.example.authapp.network.SupabaseHttp.forSession(lease)) {
        config.SessionManager.ensureValidSession(lease);
        if (!java.util.Objects.equals(email, config.SessionManager.getUserEmail())) throw new IllegalStateException("Можно изменить только свой пароль");
        String body = new org.json.JSONObject().put("email", email).put("password", password).toString();
        com.example.authapp.network.SupabaseHttp.send(com.example.authapp.network.SupabaseHttp.request(
                "/auth/v1/token?grant_type=password").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        }
    }
    /** Self-service password update using the signed-in user's token. */
    public static void updatePassword(String userId, String newPassword) throws Exception {
        var lease = config.SessionManager.requireSession();
        try (var scope = com.example.authapp.network.SupabaseHttp.forSession(lease)) {
        config.SessionManager.ensureValidSession(lease);
        if (!java.util.Objects.equals(userId, lease.userId())) throw new IllegalStateException("Можно изменить только свой пароль");
        String body = new org.json.JSONObject().put("password", newPassword).toString();
        com.example.authapp.network.SupabaseHttp.send(com.example.authapp.network.SupabaseHttp.request(
                "/auth/v1/user").header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build());
        }
    }
}
