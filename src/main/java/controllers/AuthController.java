package controllers;

import config.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AuthController {

    @FXML private TextField loginEmail, registerEmail;
    @FXML private PasswordField loginPassword, registerPassword, registerConfirmPassword;
    @FXML private Button loginButton, registerButton;
    @FXML private ProgressIndicator loginProgress, registerProgress;
    @FXML private TabPane authTabPane;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @FXML // вызывется после загрузки FXML
    public void initialize() {
        loginProgress.setVisible(false);
        registerProgress.setVisible(false);

        loginButton.setOnAction(e -> handleLogin());
        registerButton.setOnAction(e -> handleRegister());
    }

    private void handleLogin() {
        loginButton.setDisable(true);
        loginProgress.setVisible(true);
        String email = loginEmail.getText();
        String password = loginPassword.getText();
        if (email.isEmpty() || password.isEmpty()) {
            showAlert("Ошибка", "Введите email и пароль");
            loginButton.setDisable(false);
            loginProgress.setVisible(false);
            return;
        }
        new Thread(() -> {
            try {
                String loginUrl = config.Config.SUPABASE_URL + "/auth/v1/token?grant_type=password";
                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("password", password);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(loginUrl))
                        .header("apikey", config.Config.SUPABASE_ANON_KEY)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject result = new JSONObject(response.body());

                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    loginProgress.setVisible(false);
                    if (result.has("access_token") && result.has("user")) {
                        SessionManager.saveAccessToken(result.getString("access_token"));
                        JSONObject user = result.getJSONObject("user");
                        SessionManager.saveUserId(user.getString("id"));
                        SessionManager.saveUserEmail(email);
                        // После успешного входа:
                        Platform.runLater(() -> {
                            try {
                                // Закрыть текущее окно (auth)
                                ((Stage) loginButton.getScene().getWindow()).close();

                                // Открыть main.fxml
                                FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/main.fxml"));
                                BorderPane root = loader.load();
                                Stage mainStage = new Stage();
                                mainStage.setTitle("TechStore");
                                mainStage.setScene(new Scene(root, 1200, 800));
                                mainStage.show();
                            } catch (Exception e) {
                                showAlert("Ошибка", "Не удалось открыть главное окно: " + e.getMessage());
                            }
                        });

                    } else {
                        showAlert("Ошибка входа", response.body());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert("Ошибка", "Сетевая ошибка: " + e.getMessage());
                    loginButton.setDisable(false);
                    loginProgress.setVisible(false);
                });
            }
        }).start();
    }

    private void handleRegister() {
        registerButton.setDisable(true);
        registerProgress.setVisible(true);
        String email = registerEmail.getText();
        String password = registerPassword.getText();
        String confirm = registerConfirmPassword.getText();
        if (email.isEmpty() || password.isEmpty() || !password.equals(confirm)) {
            showAlert("Ошибка", "Проверьте email и пароли.");
            registerButton.setDisable(false);
            registerProgress.setVisible(false);
            return;
        }
        new Thread(() -> {
            try {
                String registerUrl = config.Config.SUPABASE_URL + "/auth/v1/signup";
                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("password", password);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(registerUrl))
                        .header("apikey", config.Config.SUPABASE_ANON_KEY)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject result = new JSONObject(response.body());

                Platform.runLater(() -> {
                    registerButton.setDisable(false);
                    registerProgress.setVisible(false);
                    if (result.has("user")) {
                        JSONObject user = result.getJSONObject("user");
                        SessionManager.saveUserId(user.getString("id"));
                        SessionManager.saveUserEmail(email);
                        if (result.has("access_token"))
                            SessionManager.saveAccessToken(result.getString("access_token"));
                        showAlert("Успех", "Аккаунт зарегистрирован!\nТеперь выполните вход.");
                        authTabPane.getSelectionModel().selectFirst(); // переключить на вкладку Вход
                    } else {
                        showAlert("Ошибка регистрации", response.body());
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert("Ошибка", "Сетевая ошибка: " + e.getMessage());
                    registerButton.setDisable(false);
                    registerProgress.setVisible(false);
                });
            }
        }).start();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
