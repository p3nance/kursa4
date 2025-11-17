package controllers;

import config.SessionManager;
import com.example.authapp.repositories.UserRepository;
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

        if (email.isEmpty() || password.isEmpty()) {
            showError("❌ Пожалуйста, заполните все поля");
            return;
        }

        if (registerMode) {
            // ✅ РЕГИСТРАЦИЯ
            String confirm = authConfirmPassword.getText();
            if (!password.equals(confirm)) {
                showError("❌ Пароли не совпадают!");
                return;
            }

            if (password.length() < 6) {
                showError("❌ Пароль должен быть не менее 6 символов");
                return;
            }

            authSubmitBtn.setDisable(true);
            authSubmitBtn.setText("Регистрация...");

            new Thread(() -> {
                try {
                    System.out.println("🔑 Регистрация пользователя: " + email);

                    // 1️⃣ Регистрируем в Supabase Auth
                    boolean success = SessionManager.register(email, password);

                    if (success) {
                        System.out.println("✅ Пользователь успешно зарегистрирован в Auth");

                        // 2️⃣ Получаем userId и email из сессии
                        String userId = SessionManager.getUserId();
                        String userEmail = SessionManager.getUserEmail();

                        System.out.println("📝 User ID: " + userId);
                        System.out.println("📧 Email: " + userEmail);

                        if (userId != null && !userId.isEmpty()) {
                            try {
                                // 3️⃣ СОЗДАЕМ ПРОФИЛЬ В ТАБЛИЦЕ profiles
                                UserRepository.createUserProfile(userId, userEmail, "", "");
                                System.out.println("✅ Профиль успешно создан в базе данных");

                                Platform.runLater(() -> {
                                    authSubmitBtn.setDisable(false);
                                    authSubmitBtn.setText("Зарегистрироваться");
                                    showSuccess("✅ Регистрация успешна!");

                                    new Thread(() -> {
                                        try {
                                            Thread.sleep(1500);
                                            Platform.runLater(() -> {
                                                if (mainController != null) {
                                                    mainController.showMainContent();
                                                }
                                            });
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }).start();
                                });

                            } catch (Exception e) {
                                System.err.println("❌ Ошибка создания профиля: " + e.getMessage());
                                e.printStackTrace();
                                Platform.runLater(() -> {
                                    authSubmitBtn.setDisable(false);
                                    authSubmitBtn.setText("Зарегистрироваться");
                                    showError("❌ Ошибка создания профиля: " + e.getMessage());
                                });
                            }
                        } else {
                            Platform.runLater(() -> {
                                authSubmitBtn.setDisable(false);
                                authSubmitBtn.setText("Зарегистрироваться");
                                showError("❌ Не удалось получить ID пользователя");
                            });
                        }

                    } else {
                        Platform.runLater(() -> {
                            authSubmitBtn.setDisable(false);
                            authSubmitBtn.setText("Зарегистрироваться");
                            showError("❌ Ошибка регистрации! Возможно, пользователь уже существует");
                        });
                    }

                } catch (Exception e) {
                    System.err.println("❌ Общая ошибка регистрации: " + e.getMessage());
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        authSubmitBtn.setDisable(false);
                        authSubmitBtn.setText("Зарегистрироваться");
                        showError("❌ Ошибка: " + e.getMessage());
                    });
                }

            }).start();

        } else {
            // ✅ ВХОД
            authSubmitBtn.setDisable(true);
            authSubmitBtn.setText("Вход...");

            new Thread(() -> {
                try {
                    System.out.println("🔐 Попытка входа: " + email);
                    boolean success = SessionManager.login(email, password);

                    Platform.runLater(() -> {
                        authSubmitBtn.setDisable(false);
                        authSubmitBtn.setText("Вход");

                        if (success) {
                            System.out.println("✅ Пользователь успешно авторизован");
                            if (mainController != null) {
                                mainController.showMainContent();
                            }
                        } else {
                            showError("❌ Ошибка авторизации! Проверьте email и пароль или пользователь заблокирован");
                        }
                    });

                } catch (Exception e) {
                    System.err.println("❌ Ошибка при входе: " + e.getMessage());
                    Platform.runLater(() -> {
                        authSubmitBtn.setDisable(false);
                        authSubmitBtn.setText("Вход");
                        showError("❌ Ошибка: " + e.getMessage());
                    });
                }

            }).start();
        }
    }

    private void showError(String msg) {
        authErrorLabel.setText(msg);
        authErrorLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12;");
        authErrorLabel.setVisible(true);
    }

    private void showSuccess(String msg) {
        authErrorLabel.setText(msg);
        authErrorLabel.setStyle("-fx-text-fill: #10b981; -fx-font-size: 12;");
        authErrorLabel.setVisible(true);
    }
}