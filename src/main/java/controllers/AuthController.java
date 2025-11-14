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
        authSubTitle.setText("Пожалуйста, авторизуйтесь или создайте аккаунт");
        authSubmitBtn.setText("Вход");
        authSwitchBtn.setText("Регистрация");
        authConfirmPassword.setVisible(false);
        authErrorLabel.setVisible(false);
        authEmail.clear();
        authPassword.clear();
        authConfirmPassword.clear();
    }

    private void setRegisterMode() {
        registerMode = true;
        authSubTitle.setText("Регистрация нового пользователя");
        authSubmitBtn.setText("Зарегистрироваться");
        authSwitchBtn.setText("← Назад к входу");
        authConfirmPassword.setVisible(true);
        authErrorLabel.setVisible(false);
        authEmail.clear();
        authPassword.clear();
        authConfirmPassword.clear();
    }

    private void handleAuthSwitch() {
        if (!registerMode) {
            setRegisterMode();
        } else {
            setLoginMode();
        }
    }

    private void handleAuthSubmit() {
        String email = authEmail.getText().trim();
        String password = authPassword.getText();

        if (registerMode) {
            String confirm = authConfirmPassword.getText();
            if (!password.equals(confirm)) {
                showError("Пароли не совпадают!");
                return;
            }

            authSubmitBtn.setDisable(true);
            new Thread(() -> {
                boolean success = SessionManager.register(email, password);
                Platform.runLater(() -> {
                    authSubmitBtn.setDisable(false);
                    if (success) {
                        if (mainController != null) mainController.showMainContent();
                    } else {
                        showError("Ошибка регистрации!");
                    }
                });
            }).start();

        } else {
            authSubmitBtn.setDisable(true);
            new Thread(() -> {
                boolean success = SessionManager.login(email, password);
                Platform.runLater(() -> {
                    authSubmitBtn.setDisable(false);
                    if (success) {
                        // ✅ ПРОВЕРЯЕМ АДМИН СТАТУС ПОСЛЕ ВХОДА
                        if (SessionManager.isAdmin()) {
                            System.out.println("👑 Вход админа, открываем админ панель...");
                            if (mainController != null) {
                                mainController.openAdminPanel();
                            }
                        } else {
                            System.out.println("👤 Обычный пользователь, открываем главный контент...");
                            if (mainController != null) {
                                mainController.showMainContent();
                            }
                        }
                    } else {
                        showError("Ошибка авторизации!");
                    }
                });
            }).start();
        }
    }

    private void showError(String msg) {
        authErrorLabel.setText(msg);
        authErrorLabel.setVisible(true);
    }
}
