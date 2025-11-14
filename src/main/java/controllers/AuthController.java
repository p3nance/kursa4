package controllers;

import config.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class AuthController {
    private static MainController mainController;

    public static void setMainController(MainController mc) {
        mainController = mc;
    }

    @FXML private Button authSubmitBtn;
    @FXML private Button authSwitchBtn;
    @FXML private Button authBackBtn;
    @FXML private TextField authEmail;
    @FXML private PasswordField authPassword;
    @FXML private PasswordField authConfirmPassword;
    @FXML private Label authSubTitle;
    @FXML private Label authErrorLabel;

    private boolean registerMode = false;

    @FXML
    public void initialize() {
        setLoginMode();
        authSubmitBtn.setOnAction(e -> handleAuthSubmit());
        authSwitchBtn.setOnAction(e -> handleAuthSwitch());
        authBackBtn.setOnAction(e -> {
            if (mainController != null)
                mainController.showMainContent();
        });
    }

    private void setLoginMode() {
        registerMode = false;
        authSubTitle.setText("Пожалуйста, войдите в ваш аккаунт");
        authSubmitBtn.setText("Войти");
        authSwitchBtn.setText(Нет аккаунта? Регистрироваться");
        authConfirmPassword.setVisible(false);
        authConfirmPassword.setManaged(false);
    }

    private void setRegisterMode() {
        registerMode = true;
        authSubTitle.setText("Создайте новый аккаунт");
        authSubmitBtn.setText(Регистрироваться");
        authSwitchBtn.setText(Уже есть аккаунт? Войти");
        authConfirmPassword.setVisible(true);
        authConfirmPassword.setManaged(true);
    }

    private void handleAuthSubmit() {
        String email = authEmail.getText().trim();
        String password = authPassword.getText();

        if (email.isEmpty() || password.isEmpty()) {
            authErrorLabel.setText("Пополните все поля");
            return;
        }

        new Thread(() -> {
            boolean success;
            if (registerMode) {
                String confirmPassword = authConfirmPassword.getText();
                if (!password.equals(confirmPassword)) {
                    Platform.runLater(() -> authErrorLabel.setText("Пароли не совпадают"));
                    return;
                }
                success = SessionManager.register(email, password);
            } else {
                success = SessionManager.login(email, password);
            }

            Platform.runLater(() -> {
                if (success) {
                    if (SessionManager.isAdmin()) {
                        if (mainController != null)
                            mainController.openAdminPanel();
                    } else {
                        if (mainController != null)
                            mainController.showMainContent();
                    }
                } else {
                    authErrorLabel.setText("Ошибка аутентификации");
                }
            });
        }).start();
    }

    private void handleAuthSwitch() {
        if (registerMode) {
            setLoginMode();
        } else {
            setRegisterMode();
        }
        authErrorLabel.setText("");
        authEmail.clear();
        authPassword.clear();
        authConfirmPassword.clear();
    }
}
