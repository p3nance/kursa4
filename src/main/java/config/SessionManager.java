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
    private static boolean isAdmin = false;

    public static String getAccessToken() { return accessToken; }
    public static String getUserEmail() { return userEmail; }
    public static String getUserId() { return userId; }
    public static boolean isAdmin() { return isAdmin; }

    public static void clearSession() {
        accessToken = null;
        userEmail = null;
        userId = null;
        isAdmin = false;
    }

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
            HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());
            if (json.has("access_token")) {
                accessToken = json.getString("access_token");
                userEmail = email;
                if (json.has("user")) {
                    userId = json.getJSONObject("user").optString("id", null);
                } else {
                    userId = json.optString("user_id", null);
                }
                checkAdminStatus(email);
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            clearSession();
            return false;
        }
    }

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
            HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
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
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            clearSession();
            return false;
        }
    }

    private static void checkAdminStatus(String email) {
        try {
            String encodedEmail = java.net.URLEncoder.encode(email, "UTF-8");
            String url = Config.SUPABASE_URL + "/rest/v1/profiles?email=eq." + encodedEmail + "&select=is_admin";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("apikey", Config.SUPABASE_ANON_KEY)
                .GET()
                .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();
                if (!body.equals("[]")) {
                    org.json.JSONArray jsonArray = new org.json.JSONArray(body);
                    if (jsonArray.length() > 0) {
                        JSONObject user = jsonArray.getJSONObject(0);
                        isAdmin = user.getBoolean("is_admin");
                    } else {
                        isAdmin = false;
                    }
                } else {
                    isAdmin = false;
                }
            } else {
                isAdmin = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            isAdmin = false;
        }
    }
}
