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

    // --- GETTERS ---
    public static String getAccessToken() { return accessToken; }
    public static String getUserEmail() { return userEmail; }
    public static String getUserId() { return userId; }

    public static void clearSession() {
        accessToken = null;
        userEmail = null;
        userId = null;
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
            System.out.println(json.toString(2)); // debug!

            if (json.has("access_token")) {
                accessToken = json.getString("access_token");
                userEmail = email;
                // Корректно выдергиваем userId из ответа Supabase!
                if (json.has("user")) {
                    userId = json.getJSONObject("user").optString("id", null);
                } else {
                    userId = json.optString("user_id", null);
                }
                return true;
            }
        } catch (Exception e) { e.printStackTrace(); }
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
            System.out.println(json.toString(2)); // debug!

            if (json.has("access_token")) {
                accessToken = json.getString("access_token");
                userEmail = email;
                if (json.has("user")) {
                    userId = json.getJSONObject("user").optString("id", null);
                } else {
                    userId = json.optString("user_id", null);
                }
                return true;
            }
        } catch (Exception e) { e.printStackTrace(); }
        clearSession();
        return false;
    }
}
