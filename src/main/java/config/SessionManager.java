package config;

import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SessionManager {

    private static String accessToken;
    private static String userEmail;
    private static String userId;
    private static boolean isAdmin = false;  // ✅ Флаг админа

    // --- GETTERS ---

    public static String getAccessToken() { return accessToken; }

    public static String getUserEmail() { return userEmail; }

    public static String getUserId() { return userId; }

    public static boolean isAdmin() { return isAdmin; }  // ✅ Метод проверки админа

    public static void clearSession() {
        accessToken = null;
        userEmail = null;
        userId = null;
        isAdmin = false;  // ✅ Сбрасываем админ статус
    }

    // --- Supabase EMAIL+PASSWORD LOGIN ---

    public static boolean login(String email, String password) {
        try {
            String url = Config.SUPABASE_URL + "/auth/v1/token?grant_type=password";

            JSONObject payload = new JSONObject();
            payload.put("email", email);
            payload.put("password", password);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("apikey", Config.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject json = new JSONObject(response.body());
            System.out.println("📡 Ответ login: " + json.toString(2));

            if (json.has("access_token")) {
                accessToken = json.getString("access_token");
                userEmail = email;

                // Корректно выдергиваем userId из ответа Supabase
                if (json.has("user")) {
                    userId = json.getJSONObject("user").optString("id", null);
                } else {
                    userId = json.optString("user_id", null);
                }

                System.out.println("✅ Вход выполнен!");
                System.out.println("📧 Email: " + userEmail);
                System.out.println("🆔 UserID: " + userId);

                // ✅ ПРОВЕРЯЕМ АДМИН СТАТУС ПОСЛЕ ЛОГИНА
                checkAdminStatus(email);

                return true;
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка при входе: " + e.getMessage());
            e.printStackTrace();
        }

        clearSession();
        return false;
    }

    // --- Supabase EMAIL+PASSWORD REGISTER ---

    public static boolean register(String email, String password) {
        try {
            String url = Config.SUPABASE_URL + "/auth/v1/signup";

            JSONObject payload = new JSONObject();
            payload.put("email", email);
            payload.put("password", password);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("apikey", Config.SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject json = new JSONObject(response.body());
            System.out.println("📡 Ответ register: " + json.toString(2));

            if (json.has("access_token")) {
                accessToken = json.getString("access_token");
                userEmail = email;

                if (json.has("user")) {
                    userId = json.getJSONObject("user").optString("id", null);
                } else {
                    userId = json.optString("user_id", null);
                }

                System.out.println("✅ Регистрация выполнена!");
                System.out.println("📧 Email: " + userEmail);
                System.out.println("🆔 UserID: " + userId);

                // ✅ НОВЫЙ ПОЛЬЗОВАТЕЛЬ НИКОГДА НЕ АДМИН
                isAdmin = false;
                System.out.println("👤 Новый пользователь не является админом");

                return true;
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка при регистрации: " + e.getMessage());
            e.printStackTrace();
        }

        clearSession();
        return false;
    }

    // ✅ НОВЫЙ МЕТОД: ПРОВЕРКА АДМИН СТАТУСА
    private static void checkAdminStatus(String email) {
        try {
            String encodedEmail = java.net.URLEncoder.encode(email, "UTF-8");
            String url = Config.SUPABASE_URL + "/rest/v1/profiles?email=eq." + encodedEmail + "&select=is_admin";

            System.out.println("🔍 Проверяем админ статус для: " + email);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("apikey", Config.SUPABASE_ANON_KEY)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("📡 Статус ответа: " + response.statusCode());
            System.out.println("📄 Тело ответа: " + response.body());

            if (response.statusCode() == 200) {
                String body = response.body();
                if (!body.equals("[]")) {
                    org.json.JSONArray jsonArray = new org.json.JSONArray(body);
                    if (jsonArray.length() > 0) {
                        JSONObject user = jsonArray.getJSONObject(0);
                        isAdmin = user.getBoolean("is_admin");

                        if (isAdmin) {
                            System.out.println("👑 АДМИН ДОСТУП РАЗРЕШЕН!");
                        } else {
                            System.out.println("👤 Обычный пользователь");
                        }
                    } else {
                        System.out.println("⚠️ Профиль не найден в БД");
                        isAdmin = false;
                    }
                } else {
                    System.out.println("⚠️ Массив пуст");
                    isAdmin = false;
                }
            } else {
                System.err.println("⚠️ Ошибка получения статуса: " + response.statusCode());
                isAdmin = false;
            }

        } catch (Exception e) {
            System.err.println("⚠️ Ошибка проверки админ статуса: " + e.getMessage());
            e.printStackTrace();
            isAdmin = false;
        }
    }
}
