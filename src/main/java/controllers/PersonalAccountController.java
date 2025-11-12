package controllers;

import com.example.authapp.models.User;
import com.example.authapp.utils.ValidationUtil;
import config.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.net.URL;
import java.util.ResourceBundle;

public class PersonalAccountController implements Initializable {
    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField addressField;
    @FXML private TextArea bioArea;

    @FXML private Button saveButton;
    @FXML private Button changePasswordButton;
    @FXML private Button addAddressButton;
    @FXML private Button logoutButton;

    @FXML private TableView<String> ordersTable;
    @FXML private TableView<String> addressesTable;

    private MainController mainController;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        emailField.setEditable(false);
        emailField.setStyle("-fx-text-fill: #94a3b8; -fx-opacity: 0.6;");

        setupButtons();
        loadUserData();
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    private void setupButtons() {
        saveButton.setOnAction(e -> saveUserData());
        changePasswordButton.setOnAction(e -> showChangePasswordDialog());
        addAddressButton.setOnAction(e -> addAddress());
        logoutButton.setOnAction(e -> logout());
    }

    private void loadUserData() {
        try {
            String email = SessionManager.getUserEmail();
            if (email != null) {
                emailField.setText(email);
                // TODO: Загрузить остальные данные пользователя из БД
            }
        } catch (Exception e) {
            showAlert("Ошибка", "Не удалось загрузить профиль");
        }
    }

    @FXML
    private void saveUserData() {
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();

        if (!ValidationUtil.isNotEmpty(name)) {
            showAlert("Ошибка", "Введите имя");
            return;
        }

        if (!phone.isEmpty() && !ValidationUtil.isValidPhone(phone)) {
            showAlert("Ошибка", "Некорректный номер телефона");
            return;
        }

        try {
            // TODO: Сохранить данные в БД
            showAlert("Успех", "Профиль обновлен");
        } catch (Exception e) {
            showAlert("Ошибка", "Не удалось сохранить изменения: " + e.getMessage());
        }
    }

    @FXML
    private void showChangePasswordDialog() {
        Dialog<Void> dialog = new Dialog<>();
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
                if (!ValidationUtil.isValidPassword(newPassword.getText())) {
                    showAlert("Ошибка", "Пароль должен содержать минимум 8 символов, букву, цифру");
                    return null;
                }
                try {
                    // TODO: Изменить пароль в БД
                    showAlert("Успех", "Пароль изменен");
                } catch (Exception e) {
                    showAlert("Ошибка", "Не удалось изменить пароль");
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    @FXML
    private void addAddress() {
        String address = addressField.getText().trim();
        if (address.isEmpty()) {
            showAlert("Ошибка", "Введите адрес");
            return;
        }

        try {
            // TODO: Добавить адрес в БД
            showAlert("Успех", "Адрес добавлен");
            addressField.clear();
        } catch (Exception e) {
            showAlert("Ошибка", "Не удалось добавить адрес: " + e.getMessage());
        }
    }

    @FXML
    private void logout() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Выход");
        confirmAlert.setHeaderText("Вы уверены?");
        confirmAlert.setContentText("Вы выйдете из аккаунта");

        if (confirmAlert.showAndWait().get() == ButtonType.OK) {
            try {
                SessionManager.clearSession();
                if (mainController != null) {
                    mainController.showMainContent();
                }
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