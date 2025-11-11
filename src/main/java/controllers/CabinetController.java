package controllers;

import config.Config;
import config.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.ResourceBundle;
import org.json.JSONObject;
import org.json.JSONArray;
import java.time.LocalDateTime;


public class CabinetController implements Initializable {

    private static final String SUPABASE_URL = Config.SUPABASE_URL;
    private static final String SUPABASE_KEY = Config.SUPABASE_ANON_KEY;
    private static final String PROFILES_TABLE = "profiles";

    private HttpClient httpClient;
    private String currentUserId;
    private String accessToken;

    @FXML
    private Button backBtn, logoutBtn, saveBtn, cancelBtn, changePasswordBtn, deleteAccountBtn;

    @FXML
    private TextField nameField, surnameField, emailField, phoneField, cityField, addressField;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.httpClient = HttpClient.newHttpClient();
        loadCurrentUser();
        setupButtons();
    }

    // ==================== ПОЛУЧЕНИЕ ТЕКУЩЕГО ПОЛЬЗОВАТЕЛЯ ====================

    private void loadCurrentUser() {
        new Thread(() -> {
            try {
                String url = SUPABASE_URL + "/auth/v1/user";

                this.accessToken = SessionManager.getAccessToken();

                if (accessToken == null || accessToken.isEmpty()) {
                    javafx.application.Platform.runLater(() ->
                            showAlert(Alert.AlertType.ERROR, "Ошибка авторизации",
                                    "Вы не авторизованы. Пожалуйста, войдите в аккаунт.")
                    );
                    return;
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                System.out.println("DEBUG: Статус ответа от /auth/v1/user: " + response.statusCode());
                System.out.println("DEBUG: Тело ответа: " + response.body());

                if (response.statusCode() == 200) {
                    JSONObject user = new JSONObject(response.body());
                    this.currentUserId = user.getString("id");
                    String email = user.optString("email", "");

                    System.out.println("✅ Текущий пользователь ID: " + currentUserId);
                    System.out.println("✅ Email: " + email);

                    javafx.application.Platform.runLater(() -> {
                        emailField.setText(email);
                        emailField.setDisable(true);
                        loadUserProfile();
                    });
                } else {
                    System.err.println("❌ Ошибка получения пользователя. Код: " + response.statusCode());
                    javafx.application.Platform.runLater(() ->
                            showAlert(Alert.AlertType.ERROR, "Ошибка",
                                    "Не удалось получить данные пользователя. Код: " + response.statusCode())
                    );
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка при загрузке пользователя: " + e.getMessage());
                e.printStackTrace();
                javafx.application.Platform.runLater(() ->
                        showAlert(Alert.AlertType.ERROR, "Ошибка подключения",
                                "Ошибка: " + e.getMessage())
                );
            }
        }).start();
    }

    // ==================== ЗАГРУЗКА ПРОФИЛЯ ИЗ ТАБЛИЦЫ ====================

    private void loadUserProfile() {
        new Thread(() -> {
            try {
                String url = SUPABASE_URL + "/rest/v1/" + PROFILES_TABLE
                        + "?id=eq." + currentUserId + "&select=*";

                System.out.println("DEBUG: URL для загрузки профиля: " + url);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                System.out.println("DEBUG: Статус загрузки профиля: " + response.statusCode());
                System.out.println("DEBUG: Ответ профиля: " + response.body());

                if (response.statusCode() == 200) {
                    JSONArray data = new JSONArray(response.body());
                    if (data.length() > 0) {
                        JSONObject profile = data.getJSONObject(0);
                        System.out.println("✅ Профиль загружен: " + profile.toString());
                        javafx.application.Platform.runLater(() -> populateFields(profile));
                    } else {
                        System.out.println("⚠️ Профиль не найден, создаем новый");
                        javafx.application.Platform.runLater(() -> createEmptyProfile());
                    }
                } else {
                    System.err.println("❌ Ошибка при загрузке профиля. Код: " + response.statusCode());
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка при загрузке профиля: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void populateFields(JSONObject profile) {
        nameField.setText(profile.optString("name", ""));
        surnameField.setText(profile.optString("surname", ""));
        phoneField.setText(profile.optString("phone", ""));
        cityField.setText(profile.optString("city", ""));
        addressField.setText(profile.optString("address", ""));
    }

    private void createEmptyProfile() {
        nameField.setText("");
        surnameField.setText("");
        phoneField.setText("");
        cityField.setText("");
        addressField.setText("");
    }

    // ==================== СОХРАНЕНИЕ ПРОФИЛЯ ====================

    private void saveProfile() {
        String name = nameField.getText().trim();
        String surname = surnameField.getText().trim();
        String phone = phoneField.getText().trim();
        String city = cityField.getText().trim();
        String address = addressField.getText().trim();

        if (name.isEmpty() || surname.isEmpty() || phone.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Внимание",
                    "Заполните обязательные поля: имя, фамилия, телефон!");
            return;
        }

        if (!phone.matches(".*\\d.*")) {
            showAlert(Alert.AlertType.WARNING, "Ошибка",
                    "Введите корректный номер телефона!");
            return;
        }

        new Thread(() -> {
            try {
                JSONObject profileData = new JSONObject();
                profileData.put("id", currentUserId);
                profileData.put("name", name);
                profileData.put("surname", surname);
                profileData.put("email", emailField.getText());
                profileData.put("phone", phone);
                profileData.put("city", city);
                profileData.put("address", address);
                profileData.put("updated_at", java.time.LocalDateTime.now().toString());

                System.out.println("DEBUG: Данные для сохранения: " + profileData.toString());

                String url = SUPABASE_URL + "/rest/v1/" + PROFILES_TABLE
                        + "?id=eq." + currentUserId;

                System.out.println("DEBUG: URL для обновления: " + url);

                HttpRequest patchRequest = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(profileData.toString()))
                        .build();

                HttpResponse<String> patchResponse = httpClient.send(patchRequest,
                        HttpResponse.BodyHandlers.ofString());

                System.out.println("DEBUG: Статус PATCH: " + patchResponse.statusCode());
                System.out.println("DEBUG: Ответ PATCH: " + patchResponse.body());

                if (patchResponse.statusCode() == 204 || patchResponse.statusCode() == 200) {
                    System.out.println("✅ Профиль успешно обновлен");
                    javafx.application.Platform.runLater(() ->
                            showAlert(Alert.AlertType.INFORMATION, "Успешно",
                                    "Профиль обновлён!")
                    );
                } else if (patchResponse.statusCode() == 404) {
                    System.out.println("⚠️ Профиль не найден, создаем новый");
                    createNewProfile(profileData);
                } else {
                    System.err.println("❌ Ошибка: " + patchResponse.statusCode());
                    System.err.println("Тело ошибки: " + patchResponse.body());
                    javafx.application.Platform.runLater(() ->
                            showAlert(Alert.AlertType.ERROR, "Ошибка",
                                    "Ошибка при сохранении (код " + patchResponse.statusCode() + "): " + patchResponse.body())
                    );
                }
            } catch (Exception e) {
                System.err.println("❌ Исключение при сохранении: " + e.getMessage());
                e.printStackTrace();
                javafx.application.Platform.runLater(() ->
                        showAlert(Alert.AlertType.ERROR, "Ошибка",
                                "Ошибка подключения: " + e.getMessage())
                );
            }
        }).start();
    }

    private void createNewProfile(JSONObject profileData) {
        new Thread(() -> {
            try {
                String url = SUPABASE_URL + "/rest/v1/" + PROFILES_TABLE;

                System.out.println("DEBUG: Создаем новый профиль. URL: " + url);
                System.out.println("DEBUG: Данные: " + profileData.toString());

                HttpRequest postRequest = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal")
                        .POST(HttpRequest.BodyPublishers.ofString(profileData.toString()))
                        .build();

                HttpResponse<String> postResponse = httpClient.send(postRequest,
                        HttpResponse.BodyHandlers.ofString());

                System.out.println("DEBUG: Статус POST: " + postResponse.statusCode());
                System.out.println("DEBUG: Ответ POST: " + postResponse.body());

                if (postResponse.statusCode() == 201 || postResponse.statusCode() == 204 || postResponse.statusCode() == 200) {
                    System.out.println("✅ Новый профиль создан");
                    javafx.application.Platform.runLater(() ->
                            showAlert(Alert.AlertType.INFORMATION, "Успешно",
                                    "Профиль создан и сохранён!")
                    );
                } else {
                    System.err.println("❌ Ошибка создания: " + postResponse.statusCode());
                    javafx.application.Platform.runLater(() ->
                            showAlert(Alert.AlertType.ERROR, "Ошибка",
                                    "Ошибка при создании профиля (код " + postResponse.statusCode() + "): " + postResponse.body())
                    );
                }
            } catch (Exception e) {
                System.err.println("❌ Исключение при создании профиля: " + e.getMessage());
                e.printStackTrace();
                javafx.application.Platform.runLater(() ->
                        showAlert(Alert.AlertType.ERROR, "Ошибка",
                                "Ошибка подключения: " + e.getMessage())
                );
            }
        }).start();
    }


    // ==================== УПРАВЛЕНИЕ АККАУНТОМ ====================

    private void changePassword() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Изменить пароль");
        alert.setHeaderText(null);
        alert.setContentText("На вашу почту (" + emailField.getText()
                + ") отправлена ссылка для сброса пароля.\n\nПроверьте почту.");
        alert.showAndWait();

        new Thread(() -> {
            try {
                JSONObject resetData = new JSONObject();
                resetData.put("email", emailField.getText());

                String url = SUPABASE_URL + "/auth/v1/recover";

                System.out.println("DEBUG: Отправляем запрос на восстановление пароля");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .header("apikey", SUPABASE_KEY)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(resetData.toString()))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                System.out.println("DEBUG: Статус восстановления пароля: " + response.statusCode());

                if (response.statusCode() == 200) {
                    System.out.println("✅ Ссылка на сброс пароля отправлена на " + emailField.getText());
                } else {
                    System.err.println("❌ Ошибка восстановления пароля: " + response.statusCode());
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка при смене пароля: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void deleteAccount() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Удалить аккаунт");
        confirmAlert.setHeaderText("⚠️ Внимание!");
        confirmAlert.setContentText("Вы уверены? Это действие необратимо!\n\nВсе ваши данные будут удалены.");
        confirmAlert.showAndWait();

        if (confirmAlert.getResult().getText().equals("OK")) {
            new Thread(() -> {
                try {
                    String url = SUPABASE_URL + "/rest/v1/" + PROFILES_TABLE
                            + "?id=eq." + currentUserId;

                    System.out.println("DEBUG: Удаляем профиль. URL: " + url);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(new URI(url))
                            .header("apikey", SUPABASE_KEY)
                            .header("Authorization", "Bearer " + accessToken)
                            .DELETE()
                            .build();

                    HttpResponse<String> response = httpClient.send(request,
                            HttpResponse.BodyHandlers.ofString());

                    System.out.println("DEBUG: Статус удаления профиля: " + response.statusCode());
                    System.out.println("DEBUG: Ответ: " + response.body());

                    if (response.statusCode() == 204 || response.statusCode() == 200) {
                        System.out.println("✅ Профиль удален");
                        javafx.application.Platform.runLater(() -> {
                            showAlert(Alert.AlertType.INFORMATION, "Успешно",
                                    "Ваш аккаунт удалён. До свидания!");
                            logout();
                        });
                    } else {
                        System.err.println("❌ Ошибка удаления профиля: " + response.statusCode());
                        javafx.application.Platform.runLater(() ->
                                showAlert(Alert.AlertType.ERROR, "Ошибка",
                                        "Ошибка при удалении профиля (код " + response.statusCode() + ")")
                        );
                    }
                } catch (Exception e) {
                    System.err.println("❌ Ошибка при удалении профиля: " + e.getMessage());
                    e.printStackTrace();
                    javafx.application.Platform.runLater(() ->
                            showAlert(Alert.AlertType.ERROR, "Ошибка",
                                    "Ошибка подключения: " + e.getMessage())
                    );
                }
            }).start();
        }
    }

    // ==================== СЛУЖЕБНЫЕ МЕТОДЫ ====================

    private void setupButtons() {
        backBtn.setOnAction(e -> closeWindow());
        logoutBtn.setOnAction(e -> logout());
        saveBtn.setOnAction(e -> saveProfile());
        cancelBtn.setOnAction(e -> loadUserProfile());
        changePasswordBtn.setOnAction(e -> changePassword());
        deleteAccountBtn.setOnAction(e -> deleteAccount());
    }

    private void closeWindow() {
        Stage stage = (Stage) backBtn.getScene().getWindow();
        stage.close();
    }

    private void logout() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Выход");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Вы хотите выйти из аккаунта?");
        confirmAlert.showAndWait();

        if (confirmAlert.getResult().getText().equals("OK")) {
            System.out.println("❌ Пользователь вышел из аккаунта");
            SessionManager.clearAccessToken();
            showAlert(Alert.AlertType.INFORMATION, "До встречи!",
                    "Вы успешно вышли из аккаунта");
            closeWindow();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
