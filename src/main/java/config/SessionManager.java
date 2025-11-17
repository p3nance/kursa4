package config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SessionManager {
    private static String accessToken;
    private static String userEmail;
    private static String userId;
    private static boolean isAdmin = false;
    private static boolean isBlocked = false;

    // --- GETTERS ---
    public static String getAccessToken() { return accessToken; }
    public static String getUserEmail() { return userEmail; }
    public static String getUserId() { return userId; }
    public static boolean isAdmin() { return isAdmin; }
    public static boolean isBlocked() { return isBlocked; }

    public static void clearSession() {
        accessToken = null;
        userEmail = null;
        userId = null;
        isAdmin = false;
        isBlocked = false;
        System.out.println("🚪 Сессия очищена");
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

            if (json.has("access_token")) {
                accessToken = json.getString("access_token");
                userEmail = email;

                if (json.has("user")) {
                    userId = json.getJSONObject("user").optString("id", null);
                } else {
                    userId = json.optString("user_id", null);
                }

                System.out.println("✅ Пользователь авторизован: " + email);
                System.out.println("📝 User ID: " + userId);

                // ✅ ПРОВЕРЯЕМ БЛОКИРОВКУ И АДМИН СТАТУС
                if (isUserBlocked(email)) {
                    clearSession();
                    System.err.println("❌ Пользователь заблокирован администратором!");
                    return false;
                }

                checkAdminStatus(email);
                return true;
            }

            return false;

        } catch (Exception e) {
            System.err.println("❌ Ошибка при входе: " + e.getMessage());
            e.printStackTrace();
            clearSession();
            return false;
        }
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

            if (json.has("access_token")) {
                accessToken = json.getString("access_token");
                userEmail = email;

                if (json.has("user")) {
                    userId = json.getJSONObject("user").optString("id", null);
                } else {
                    userId = json.optString("user_id", null);
                }

                isAdmin = false;
                System.out.println("✅ Пользователь зарегистрирован: " + email);
                return true;
            }

            return false;

        } catch (Exception e) {
            System.err.println("❌ Ошибка при регистрации: " + e.getMessage());
            e.printStackTrace();
            clearSession();
            return false;
        }
    }

    // --- ПРОВЕРКА БЛОКИРОВКИ ---
    private static boolean isUserBlocked(String email) {
        try {
            String encodedEmail = URLEncoder.encode(email, "UTF-8");
            String url = Config.SUPABASE_URL + "/rest/v1/profiles?email=eq." + encodedEmail + "&select=is_blocked";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("apikey", Config.SUPABASE_ANON_KEY)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && !response.body().equals("[]")) {
                JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
                if (jsonArray.size() > 0) {
                    JsonObject user = jsonArray.get(0).getAsJsonObject();
                    boolean blocked = user.get("is_blocked").getAsBoolean();
                    if (blocked) {
                        System.out.println("🔒 Пользователь заблокирован в системе");
                        isBlocked = true;
                    }
                    return blocked;
                }
            }

            return false;

        } catch (Exception e) {
            System.err.println("⚠️ Ошибка проверки блокировки: " + e.getMessage());
            return false;
        }
    }

    // --- ПРОВЕРКА АДМИН СТАТУСА ---
    private static void checkAdminStatus(String email) {
        try {
            String encodedEmail = URLEncoder.encode(email, "UTF-8");
            String url = Config.SUPABASE_URL + "/rest/v1/profiles?email=eq." + encodedEmail + "&select=is_admin";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("apikey", Config.SUPABASE_ANON_KEY)
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                if (!body.equals("[]")) {
                    JsonArray jsonArray = JsonParser.parseString(body).getAsJsonArray();
                    if (jsonArray.size() > 0) {
                        JsonObject user = jsonArray.get(0).getAsJsonObject();
                        isAdmin = user.get("is_admin").getAsBoolean();
                        if (isAdmin) {
                            System.out.println("👑 Пользователь имеет права администратора");
                        }
                    }
                } else {
                    isAdmin = false;
                }
            } else {
                isAdmin = false;
            }

        } catch (Exception e) {
            System.err.println("⚠️ Ошибка проверки админ статуса: " + e.getMessage());
            isAdmin = false;
        }
    }
}