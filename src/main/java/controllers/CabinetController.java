package controllers;
import config.Config;
import config.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class CabinetController {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @FXML private TextField nameField, surnameField, emailField, phoneField, cityField, addressField;
    @FXML private Button saveBtn, logoutBtn, backBtn;

    @FXML
    public void initialize() {
        // email нельзя редактировать!
        emailField.setEditable(false);

        loadUserProfile();
        saveBtn.setOnAction(e -> handleSave());
        logoutBtn.setOnAction(e -> handleLogout());
        // backBtn: переход назад по твоей логике
    }

    private void loadUserProfile() {
        String userId = SessionManager.getUserId();
        if (userId == null) return;
        String url = Config.SUPABASE_URL + "/rest/v1/profiles?id=eq." + userId;
        new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .header("apikey", Config.SUPABASE_ANON_KEY)
                        .header("Authorization", "Bearer " + SessionManager.getAccessToken())
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JSONArray data = new JSONArray(response.body());
                Platform.runLater(() -> {
                    if (data.length() == 0) {
                        createNewProfile(userId, SessionManager.getUserEmail());
                        clearFields();
                    } else {
                        JSONObject profile = data.getJSONObject(0);
                        nameField.setText(profile.optString("name", ""));
                        surnameField.setText(profile.optString("surname", ""));
                        emailField.setText(profile.optString("email", ""));
                        phoneField.setText(profile.optString("phone", ""));
                        cityField.setText(profile.optString("city", ""));
                        addressField.setText(profile.optString("address", ""));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(this::clearFields);
            }
        }).start();
    }

    private void clearFields() {
        nameField.setText("");
        surnameField.setText("");
        emailField.setText(SessionManager.getUserEmail());
        phoneField.setText("");
        cityField.setText("");
        addressField.setText("");
    }

    private void createNewProfile(String userId, String email) {
        new Thread(() -> {
            try {
                JSONObject profileData = new JSONObject();
                profileData.put("id", userId);
                profileData.put("email", email);
                profileData.put("name", "");
                profileData.put("surname", "");
                profileData.put("phone", "");
                profileData.put("city", "");
                profileData.put("address", "");
                String url = Config.SUPABASE_URL + "/rest/v1/profiles";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .header("apikey", Config.SUPABASE_ANON_KEY)
                        .header("Authorization", "Bearer " + SessionManager.getAccessToken())
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal")
                        .POST(HttpRequest.BodyPublishers.ofString(profileData.toString()))
                        .build();
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {}
        }).start();
    }

    private void handleSave() {
        String userId = SessionManager.getUserId();
        String url = Config.SUPABASE_URL + "/rest/v1/profiles?id=eq." + userId;
        JSONObject profileData = new JSONObject();
        profileData.put("name", nameField.getText());
        profileData.put("surname", surnameField.getText());
        // email НЕ меняется (берётся из SessionManager)!
        profileData.put("phone", phoneField.getText());
        profileData.put("city", cityField.getText());
        profileData.put("address", addressField.getText());
        saveBtn.setDisable(true); // Блокируем кнопку на время запроса
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
                Platform.runLater(() -> {
                    saveBtn.setDisable(false);
                    new Alert(Alert.AlertType.INFORMATION, "Профиль сохранён!").showAndWait();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    saveBtn.setDisable(false);
                    new Alert(Alert.AlertType.ERROR, "Ошибка сохранения!").showAndWait();
                });
            }
        }).start();
    }

    private void handleLogout() {
        SessionManager.clearSession();
        Platform.runLater(() -> {
            // Здесь закрытие окна личного кабинета + возврат к окну логина
            saveBtn.getScene().getWindow().hide();
            // (допиши вызов открытия окна логина если делалось через отдельный Stage)
        });
    }
}
