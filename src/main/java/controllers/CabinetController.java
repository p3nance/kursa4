package controllers;

import config.SessionManager;
import com.example.authapp.repositories.UserRepository;
import com.example.authapp.dto.UserDTO;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.scene.layout.VBox;

public class CabinetController implements Initializable {
    @FXML private Label userEmailLabel;
    @FXML private TextField emailField;
    @FXML private TextField nameField;
    @FXML private TextField surnameField;
    @FXML private TextField phoneField;
    @FXML private TextField cityField;
    @FXML private TextField addressField;
    @FXML private Button saveButton;
    @FXML private Button changePasswordButton;
    @FXML private Button backButton;
    @FXML private Button logoutButton;
    @FXML private TableView<String> ordersTable;

    private static MainController hostMainController;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        System.out.println("✅ CabinetController инициализируется...");

        String email = SessionManager.getUserEmail();
        if (email != null) {
            userEmailLabel.setText("Пользователь: " + email);
            emailField.setText(email);
            loadUserData(email);
        }

        setupButtons();
    }

    public static void setHostMainController(MainController controller) {
        hostMainController = controller;
    }

    private void setupButtons() {
        if (saveButton != null) {
            saveButton.setOnAction(e -> saveUserData());
        }
        if (changePasswordButton != null) {
            changePasswordButton.setOnAction(e -> showChangePasswordDialog());
        }
        if (backButton != null) {
            backButton.setOnAction(e -> goBack());
        }
        if (logoutButton != null) {
            logoutButton.setOnAction(e -> logout());
        }
    }

    private void loadUserData(String email) {
        Thread loadThread = new Thread(() -> {
            try {
                System.out.println("🔄 Загрузка профиля пользователя: " + email);

                UserDTO user = UserRepository.getUserProfileByEmail(email);

                if (user != null) {
                    System.out.println("✅ Профиль загружен: " + user.name + " " + user.surname);
                    javafx.application.Platform.runLater(() -> {
                        nameField.setText(user.name != null ? user.name : "");
                        surnameField.setText(user.surname != null ? user.surname : "");
                        phoneField.setText(user.phone != null ? user.phone : "");
                        cityField.setText(user.city != null ? user.city : "");
                        addressField.setText(user.address != null ? user.address : "");
                    });
                } else {
                    System.out.println("⚠️ Профиль не найден в БД");
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка загрузки профиля: " + e.getMessage());
                e.printStackTrace();
            }
        });
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void saveUserData() {
        String name = nameField.getText().trim();
        String surname = surnameField.getText().trim();
        String phone = phoneField.getText().trim();
        String city = cityField.getText().trim();
        String address = addressField.getText().trim();
        String email = emailField.getText().trim();

        if (name.isEmpty() || surname.isEmpty()) {
            showAlert("Ошибка", "Введите имя и фамилию");
            return;
        }

        try {
            System.out.println("💾 Сохранение профиля: " + name + " " + surname);
            UserRepository.updateUserProfile(email, name, surname, phone, city, address);
            showAlert("Успех", "Профиль обновлен");
        } catch (Exception e) {
            showAlert("Ошибка", "Не удалось сохранить профиль: " + e.getMessage());
            System.err.println("❌ Ошибка сохранения: " + e.getMessage());
        }
    }

    private void showChangePasswordDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Изменение пароля");
        dialog.setHeaderText("Установите новый пароль");

        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 15;");

        PasswordField oldPassword = new PasswordField();
        oldPassword.setPromptText("Старый пароль");

        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("Новый пароль");

        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Подтверждение пароля");

        content.getChildren().addAll(
                new Label("Старый пароль:"), oldPassword,
                new Label("Новый пароль:"), newPassword,
                new Label("Подтвердить:"), confirmPassword
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                if (newPassword.getText().isEmpty()) {
                    showAlert("Ошибка", "Введите новый пароль");
                    return null;
                }
                if (!newPassword.getText().equals(confirmPassword.getText())) {
                    showAlert("Ошибка", "Пароли не совпадают");
                    return null;
                }
                showAlert("Успех", "Пароль изменен");
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void goBack() {
        if (hostMainController != null) {
            hostMainController.showMainContent();
        }
    }

    private void logout() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Выход");
        confirmAlert.setHeaderText("Вы уверены?");
        confirmAlert.setContentText("Вы выйдете из аккаунта");

        if (confirmAlert.showAndWait().get() == ButtonType.OK) {
            try {
                SessionManager.clearSession();
                goBack();
            } catch (Exception e) {
                showAlert("Ошибка", "Ошибка при выходе: " + e.getMessage());
            }
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