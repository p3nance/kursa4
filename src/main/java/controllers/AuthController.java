package controllers;

import config.Config;
import config.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class AuthController {
    @FXML private TextField loginEmail;
    @FXML private PasswordField loginPassword;
    @FXML private Button loginButton;
    @FXML private Button switchToRegisterButton;
    @FXML private TextField registerEmail;
    @FXML private PasswordField registerPassword;
    @FXML private PasswordField registerConfirmPassword;
    @FXML private Button registerButton;
    @FXML private Button switchToLoginButton;
    @FXML private TabPane authTabPane;
    @FXML private ProgressIndicator loginProgress;
    @FXML private ProgressIndicator registerProgress;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @FXML
    public void initialize() {
        Config.validateConfig();
        setupEventHandlers();
        hideProgressIndicators();
        showConfigWarningIfNeeded();
    }

    private void setupEventHandlers() {
        loginButton.setOnAction(e -> handleLogin());
        registerButton.setOnAction(e -> handleRegister());
        switchToRegisterButton.setOnAction(e -> switchToRegister());
        switchToLoginButton.setOnAction(e -> switchToLogin());
    }

    private void hideProgressIndicators() {
        loginProgress.setVisible(false);
        registerProgress.setVisible(false);
    }

    private void showConfigWarningIfNeeded() {
        if (!isSupabaseConfigured()) {
            showAlert(
                    "Настройка приложения",
                    "⚠️ Supabase не настроен!\n\n" +
                            "Чтобы приложение работало с реальной базой данных:\n\n" +
                            "1. Откройте файл: src/main/java/config/Config.java\n" +
                            "2. Замените SUPABASE_URL и SUPABASE_ANON_KEY\n" +
                            "3. Перезапустите приложение\n\n" +
                            "Сейчас работает демо-режим с заглушками."
            );
        }
    }

    private void handleLogin() {
        String email = loginEmail.getText().trim();
        String password = loginPassword.getText();

        if (!validateAuthFields(email, password)) return;

        loginProgress.setVisible(true);
        loginButton.setDisable(true);

        new Thread(() -> {
            try {
                if (!isSupabaseConfigured()) {
                    // Демо-режим
                    Thread.sleep(1500);
                    javafx.application.Platform.runLater(() -> {
                        showAlert("Демо-режим", "Supabase не настроен. Используйте демо-режим.");
                        // Для демо сохраняем фиксированные значения
                        SessionManager.saveAccessToken("demo_token_" + System.currentTimeMillis());
                        SessionManager.saveUserId("demo_user_123");
                        showMainForm();
                        loginProgress.setVisible(false);
                        loginButton.setDisable(false);
                    });
                    return;
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(Config.SUPABASE_URL + "/auth/v1/token?grant_type=password"))
                        .header("apikey", Config.SUPABASE_ANON_KEY)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}",
                                StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                javafx.application.Platform.runLater(() -> {
                    loginProgress.setVisible(false);
                    loginButton.setDisable(false);

                    if (response.statusCode() == 200) {
                        try {
                            JSONObject authResponse = new JSONObject(response.body());
                            String accessToken = authResponse.optString("access_token", "");
                            JSONObject user = authResponse.optJSONObject("user");

                            if (user != null && !accessToken.isEmpty()) {
                                String userId = user.getString("id");

                                // Сохраняем токен и ID в сессию
                                SessionManager.saveAccessToken(accessToken);
                                SessionManager.saveUserId(userId);

                                System.out.println("✅ Сессия создана для пользователя: " + userId);
                            }
                        } catch (Exception parseException) {
                            System.err.println("⚠️ Ошибка парсинга ответа: " + parseException.getMessage());
                        }

                        showAlert("Успешный вход", "Вы успешно вошли в систему!");
                        showMainForm();
                    } else {
                        showAlert("Ошибка входа",
                                "Не удалось войти. Проверьте Email и пароль.\nКод ошибки: " + response.statusCode());
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    showAlert("Ошибка", "Ошибка при входе: " + e.getMessage());
                    loginProgress.setVisible(false);
                    loginButton.setDisable(false);
                });
            }
        }).start();
    }

    private void handleRegister() {
        String email = registerEmail.getText().trim();
        String password = registerPassword.getText();
        String confirmPassword = registerConfirmPassword.getText();

        if (!validateRegisterFields(email, password, confirmPassword)) return;

        registerProgress.setVisible(true);
        registerButton.setDisable(true);

        new Thread(() -> {
            try {
                if (!isSupabaseConfigured()) {
                    Thread.sleep(1500);
                    javafx.application.Platform.runLater(() -> {
                        showAlert("Демо-регистрация", "Supabase не настроен. Используйте демо-режим.");
                        clearRegisterForm();
                        switchToLogin();
                        registerProgress.setVisible(false);
                        registerButton.setDisable(false);
                    });
                    return;
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(Config.SUPABASE_URL + "/auth/v1/signup"))
                        .header("apikey", Config.SUPABASE_ANON_KEY)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}",
                                StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                javafx.application.Platform.runLater(() -> {
                    registerProgress.setVisible(false);
                    registerButton.setDisable(false);

                    if (response.statusCode() == 200) {
                        try {
                            JSONObject authResponse = new JSONObject(response.body());
                            JSONObject user = authResponse.optJSONObject("user");

                            if (user != null) {
                                String userId = user.getString("id");
                                SessionManager.saveUserId(userId);
                                System.out.println("✅ Пользователь зарегистрирован: " + userId);
                            }
                        } catch (Exception parseException) {
                            System.err.println("⚠️ Ошибка парсинга ответа: " + parseException.getMessage());
                        }

                        showAlert("Регистрация успешна",
                                "Пользователь зарегистрирован. Проверьте email для подтверждения.");
                        clearRegisterForm();
                        switchToLogin();
                    } else {
                        showAlert("Ошибка регистрации",
                                "Не удалось зарегистрировать пользователя.\nКод ошибки: " + response.statusCode());
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    showAlert("Ошибка", "Ошибка при регистрации: " + e.getMessage());
                    registerProgress.setVisible(false);
                    registerButton.setDisable(false);
                });
            }
        }).start();
    }

    private boolean isSupabaseConfigured() {
        return !Config.SUPABASE_URL.contains("your-project")
                && !Config.SUPABASE_ANON_KEY.contains("your-key")
                && Config.SUPABASE_URL.startsWith("https://");
    }

    private boolean validateAuthFields(String email, String password) {
        if (email.isEmpty() || password.isEmpty()) {
            showAlert("Ошибка", "Заполните все поля");
            return false;
        }
        if (!isValidEmail(email)) {
            showAlert("Ошибка", "Введите корректный email");
            return false;
        }
        return true;
    }

    private boolean validateRegisterFields(String email, String password, String confirmPassword) {
        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showAlert("Ошибка", "Заполните все поля");
            return false;
        }
        if (!isValidEmail(email)) {
            showAlert("Ошибка", "Введите корректный email");
            return false;
        }
        if (!password.equals(confirmPassword)) {
            showAlert("Ошибка", "Пароли не совпадают");
            return false;
        }
        if (password.length() < 6) {
            showAlert("Ошибка", "Пароль должен содержать минимум 6 символов");
            return false;
        }
        return true;
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    private void clearRegisterForm() {
        registerEmail.clear();
        registerPassword.clear();
        registerConfirmPassword.clear();
    }

    private void switchToRegister() {
        authTabPane.getSelectionModel().select(1);
    }

    private void switchToLogin() {
        authTabPane.getSelectionModel().select(0);
    }

    private void showMainForm() {
        try {
            Stage currentStage = (Stage) loginButton.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/main.fxml"));
            Parent root = loader.load();
            Stage mainStage = new Stage();
            mainStage.setTitle("Главное окно");
            mainStage.setScene(new Scene(root, 1280, 720));
            mainStage.show();
            currentStage.close();
        } catch (Exception e) {
            showAlert("Ошибка", "Не удалось загрузить главное окно: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

