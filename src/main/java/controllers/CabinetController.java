package controllers;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class CabinetController implements Initializable {
    @FXML
    private Button backBtn, logoutBtn, saveBtn, cancelBtn, changePasswordBtn, deleteAccountBtn;
    @FXML
    private TextField nameField, surnameField, emailField, phoneField, cityField, addressField;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupButtons();
        loadUserData();
    }

    private void setupButtons() {
        backBtn.setOnAction(e -> closeWindow());
        logoutBtn.setOnAction(e -> logout());
        saveBtn.setOnAction(e -> saveProfile());
        cancelBtn.setOnAction(e -> loadUserData());
        changePasswordBtn.setOnAction(e -> changePassword());
        deleteAccountBtn.setOnAction(e -> deleteAccount());
    }

    private void loadUserData() {
        // Загрузи данные пользователя из БД или SharedPreferences
        nameField.setText("Иван");
        surnameField.setText("Петров");
        emailField.setText("ivan.petrov@email.com");
        phoneField.setText("+7 (999) 123-45-67");
        cityField.setText("Москва");
        addressField.setText("ул. Арбат, д. 15, кв. 42");
    }

    private void saveProfile() {
        String name = nameField.getText().trim();
        String surname = surnameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String city = cityField.getText().trim();
        String address = addressField.getText().trim();

        // Проверка на пустые поля
        if (name.isEmpty() || surname.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Внимание", "Заполните все обязательные поля!");
            return;
        }

        // Проверка email
        if (!email.contains("@")) {
            showAlert(Alert.AlertType.WARNING, "Ошибка", "Введите корректный email!");
            return;
        }

        // Сохранение в БД или локальное хранилище
        System.out.println("Сохранение профиля:");
        System.out.println("Имя: " + name + " " + surname);
        System.out.println("Email: " + email);
        System.out.println("Телефон: " + phone);
        System.out.println("Город: " + city);
        System.out.println("Адрес: " + address);

        showAlert(Alert.AlertType.INFORMATION, "Успешно", "Профиль сохранён!");
    }

    private void changePassword() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Изменить пароль");
        alert.setHeaderText(null);
        alert.setContentText("Функция изменения пароля будет реализована позже.\n\nНа вашу почту отправлена ссылка для сброса пароля.");
        alert.showAndWait();
        System.out.println("Отправлена ссылка на смену пароля на " + emailField.getText());
    }

    private void deleteAccount() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Удалить аккаунт");
        confirmAlert.setHeaderText("⚠️ Внимание!");
        confirmAlert.setContentText("Вы уверены? Это действие необратимо!\n\nВсе ваши данные будут удалены.");
        confirmAlert.showAndWait();

        if (confirmAlert.getResult().getText().equals("OK")) {
            System.out.println("Аккаунт удалён!");
            showAlert(Alert.AlertType.INFORMATION, "Успешно", "Ваш аккаунт удалён. До свидания!");
            logout();
        }
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
            System.out.println("Пользователь вышел из аккаунта");
            showAlert(Alert.AlertType.INFORMATION, "До встречи!", null, "Вы успешно вышли из аккаунта");
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

    private void showAlert(Alert.AlertType type, String title, String header, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
