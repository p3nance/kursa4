package controllers;

import config.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;

public class AuthController {
    private static MainController mainController;
    public static void setMainController(MainController mc) { mainController = mc; }

    @FXML private TabPane authTabPane;
    @FXML private TextField loginEmail, registerEmail;
    @FXML private PasswordField loginPassword, registerPassword, registerConfirmPassword;
    @FXML private Button loginButton, registerButton, switchToLoginButton, switchToRegisterButton;
    @FXML private ProgressIndicator loginProgress, registerProgress;

    @FXML
    public void initialize() {
        loginButton.setOnAction(e -> handleLogin());
        registerButton.setOnAction(e -> handleRegister());

        switchToRegisterButton.setOnAction(e -> authTabPane.getSelectionModel().select(1));
        switchToLoginButton.setOnAction(e -> authTabPane.getSelectionModel().select(0));

        loginProgress.setVisible(false);
        registerProgress.setVisible(false);
    }

    private void handleLogin() {
        String email = loginEmail.getText().trim();
        String password = loginPassword.getText();

        loginButton.setDisable(true);
        loginProgress.setVisible(true);

        new Thread(() -> {
            boolean success = SessionManager.login(email, password);
            Platform.runLater(() -> {
                loginButton.setDisable(false);
                loginProgress.setVisible(false);
                if (success) {
                    if (mainController != null) mainController.openCabinetInMain();
                } else {
                    new Alert(Alert.AlertType.ERROR, "Ошибка авторизации!").showAndWait();
                }
            });
        }).start();
    }

    private void handleRegister() {
        String email = registerEmail.getText().trim();
        String password = registerPassword.getText();
        String confirm = registerConfirmPassword.getText();

        if (!password.equals(confirm)) {
            new Alert(Alert.AlertType.ERROR, "Пароли не совпадают!").showAndWait();
            return;
        }

        registerButton.setDisable(true);
        registerProgress.setVisible(true);

        new Thread(() -> {
            boolean success = SessionManager.register(email, password);
            Platform.runLater(() -> {
                registerButton.setDisable(false);
                registerProgress.setVisible(false);
                if (success) {
                    if (mainController != null) mainController.openCabinetInMain();
                } else {
                    new Alert(Alert.AlertType.ERROR, "Ошибка регистрации!").showAndWait();
                }
            });
        }).start();
    }
}
