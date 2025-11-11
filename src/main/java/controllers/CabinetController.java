package controllers;
import config.Config;
import config.SessionManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class CabinetController {
    private HttpClient httpClient = HttpClient.newHttpClient();

    public void loadUserProfile() {
        String userId = SessionManager.getUserId();
        if (userId == null) return; // Не делаем запрос без логина!
        String url = Config.SUPABASE_URL + "/rest/v1/profiles?id=eq." + userId;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("apikey", Config.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer " + SessionManager.getAccessToken())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONArray data = new JSONArray(response.body());
            if (data.length() == 0) {
                createNewProfile(userId, SessionManager.getUserEmail());
            } else {
                JSONObject profile = data.getJSONObject(0);
                // Заполни поля профиля в UI
            }
        } catch (Exception e) {
            System.out.println("Ошибка загрузки профиля: " + e.getMessage());
        }
    }

    private void createNewProfile(String userId, String email) {
        new Thread(() -> {
            try {
                JSONObject profileData = new JSONObject();
                profileData.put("id", userId);
                profileData.put("email", email);
                String url = Config.SUPABASE_URL + "/rest/v1/profiles";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .header("apikey", Config.SUPABASE_ANON_KEY)
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal")
                        .POST(HttpRequest.BodyPublishers.ofString(profileData.toString()))
                        .build();
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {}
        }).start();
    }
    public void saveProfile(JSONObject profileData) {
        String userId = SessionManager.getUserId();
        String url = Config.SUPABASE_URL + "/rest/v1/profiles?id=eq." + userId;
        new Thread(() -> {
            try {
                HttpRequest patchRequest = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .header("apikey", Config.SUPABASE_ANON_KEY)
                        .header("Authorization", "Bearer " + SessionManager.getAccessToken())
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(profileData.toString()))
                        .build();
                httpClient.send(patchRequest, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {}
        }).start();
    }
}
