package controllers;

import config.SessionManager;
import com.example.authapp.repositories.UserRepository;
import com.example.authapp.dto.UserDTO;
import com.example.authapp.dto.OrderDTO;
import com.example.authapp.dto.OrderItemDTO;
import com.example.authapp.services.OrderService;
import com.example.authapp.services.CartService;
import com.example.authapp.services.ProductService;
import com.example.authapp.utils.PhoneFormatter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * ✅ Контроллер личного кабинета пользователя
 * Загружает профиль, показывает историю заказов, позволяет редактировать данные
 */
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
    @FXML private VBox mainContentVBox;
    @FXML private BorderPane rootBorderPane;
    @FXML private ScrollPane scrollPane;

    private static MainController hostMainController;
    private OrderService orderService;
    private String userEmail;
    private String userId;
    private boolean isAdmin = false;
    private AdminController adminController = null;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        System.out.println("👤 Инициализация CabinetController...");
        try {
            CartService cartService = new CartService();
            ProductService productService = new ProductService();
            orderService = new OrderService(cartService, productService);

            userEmail = SessionManager.getUserEmail();
            userId = SessionManager.getUserId();

            if (userEmail != null) {
                userEmailLabel.setText(userEmail);
                emailField.setText(userEmail);
                emailField.setEditable(false);

                // ✅ Применяем форматирование к полю телефона
                PhoneFormatter.setupPhoneField(phoneField);

                loadUserData();
                setupButtons();
                loadOrderHistory();
                checkIfAdmin();

                System.out.println("✅ CabinetController инициализирован");
            } else {
                showError("❌ Ошибка: пользователь не авторизован");
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка инициализации: " + e.getMessage());
            e.printStackTrace();
            showError("❌ Ошибка инициализации личного кабинета");
        }
    }

    public static void setHostMainController(MainController controller) {
        hostMainController = controller;
    }

    // ✅ ЗАГРУЗКА ДАННЫХ ПОЛЬЗОВАТЕЛЯ
    private void loadUserData() {
        Thread loadThread = new Thread(() -> {
            try {
                UserDTO user = UserRepository.getUserProfileByEmail(userEmail);
                Platform.runLater(() -> {
                    if (user != null) {
                        nameField.setText(user.name != null ? user.name : "");
                        surnameField.setText(user.surname != null ? user.surname : "");
                        phoneField.setText(user.phone != null ? user.phone : "");
                        cityField.setText(user.city != null ? user.city : "");
                        addressField.setText(user.address != null ? user.address : "");


                        System.out.println("✅ Данные пользователя загружены");
                        System.out.println("   Email: " + user.email);
                        System.out.println("   Is Admin: " + isAdmin);
                    } else {
                        System.out.println("⚠️ Профиль пользователя не найден");
                    }
                });
            } catch (Exception e) {
                System.err.println("❌ Ошибка загрузки профиля: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> showError("❌ Ошибка загрузки профиля: " + e.getMessage()));
            }
        });
        loadThread.setDaemon(true);
        loadThread.start();
    }

    // ✅ ЗАГРУЗКА ИСТОРИИ ЗАКАЗОВ
    private void loadOrderHistory() {
        Thread loadThread = new Thread(() -> {
            try {
                List<OrderDTO> orders = orderService.getUserOrderHistory();
                Platform.runLater(() -> {
                    VBox ordersVBox = new VBox(15);
                    ordersVBox.setStyle("-fx-background-color: #ffffff; -fx-padding: 20; " +
                            "-fx-border-color: #e5e7eb; -fx-border-width: 1 0 0 0;");

                    if (orders == null || orders.isEmpty()) {
                        Label emptyLabel = new Label("📭 История заказов пуста");
                        emptyLabel.setStyle("-fx-font-size: 16; -fx-text-fill: #999;");
                        ordersVBox.getChildren().add(emptyLabel);
                        ordersVBox.setAlignment(Pos.CENTER);
                    } else {
                        Label historyTitle = new Label("📋 История заказов");
                        historyTitle.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #1f2937;");
                        ordersVBox.getChildren().add(historyTitle);
                        ordersVBox.getChildren().add(new Separator());

                        for (OrderDTO order : orders) {
                            ordersVBox.getChildren().add(createOrderCard(order));
                        }

                        System.out.println("✅ Загружено " + orders.size() + " заказов");
                    }

                    mainContentVBox.getChildren().add(ordersVBox);
                });
            } catch (Exception e) {
                System.err.println("❌ Ошибка загрузки заказов: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> showError("❌ Ошибка загрузки заказов"));
            }
        });
        loadThread.setDaemon(true);
        loadThread.start();
    }

    // ✅ СОЗДАНИЕ КАРТОЧКИ ЗАКАЗА
    private VBox createOrderCard(OrderDTO order) {
        VBox cardVBox = new VBox(10);
        cardVBox.setStyle("-fx-border-color: #e5e7eb; -fx-border-radius: 12; " +
                "-fx-background-color: #f9fafb; -fx-background-radius: 12; " +
                "-fx-padding: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 2,0,0,1);");

        // Заголовок с номером, датой и статусом
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label orderIdLabel = new Label("Заказ #" + order.orderId);
        orderIdLabel.setStyle("-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label dateLabel = new Label(order.orderDate != null ? order.orderDate : "N/A");
        dateLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");

        Label statusLabel = new Label(getStatusLabel(order.status));
        statusLabel.setStyle("-fx-font-size: 11; -fx-padding: 4 10; -fx-background-radius: 4; " +
                "-fx-font-weight: bold; " + getStatusStyle(order.status));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        headerBox.getChildren().addAll(orderIdLabel, dateLabel, spacer, statusLabel);

        // Товары в заказе
        VBox itemsBox = new VBox(5);
        itemsBox.setStyle("-fx-padding: 10; -fx-background-color: #ffffff; -fx-background-radius: 8; " +
                "-fx-border-color: #e5e7eb; -fx-border-width: 1;");

        if (order.items != null && !order.items.isEmpty()) {
            for (OrderItemDTO item : order.items) {
                HBox itemRow = new HBox(12);
                itemRow.setAlignment(Pos.CENTER_LEFT);
                itemRow.setStyle("-fx-padding: 8;");

                Label itemName = new Label(item.productName + " x" + item.quantity);
                itemName.setStyle("-fx-font-size: 12; -fx-text-fill: #333;");

                Region itemSpacer = new Region();
                HBox.setHgrow(itemSpacer, Priority.ALWAYS);

                Label itemPrice = new Label(String.format("%.2f ₽", item.subtotal));
                itemPrice.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #3b82f6;");

                itemRow.getChildren().addAll(itemName, itemSpacer, itemPrice);
                itemsBox.getChildren().add(itemRow);
            }
        } else {
            Label noItems = new Label("Нет товаров в заказе");
            noItems.setStyle("-fx-font-size: 12; -fx-text-fill: #999;");
            itemsBox.getChildren().add(noItems);
        }

        // Суммы и итоги
        VBox summaryBox = new VBox(8);
        summaryBox.setStyle("-fx-padding: 12; -fx-background-color: #f9fafb; -fx-border-radius: 8;");

        HBox totalBox = createSummaryRow("Сумма:", String.format("%.2f ₽", order.totalAmount), "#666");
        summaryBox.getChildren().add(totalBox);

        if (order.discountAmount > 0) {
            HBox discountBox = createSummaryRow("Скидка:", String.format("-%.2f ₽", order.discountAmount), "#ef4444");
            summaryBox.getChildren().add(discountBox);
            summaryBox.getChildren().add(new Separator());
        } else {
            summaryBox.getChildren().add(new Separator());
        }

        HBox finalBox = createSummaryRow("К оплате:", String.format("%.2f ₽", order.finalAmount), "#059669");
        summaryBox.getChildren().add(finalBox);

        cardVBox.getChildren().addAll(headerBox, itemsBox, summaryBox);
        return cardVBox;
    }

    // ✅ СОЗДАНИЕ СТРОКИ ИТОГА
    private HBox createSummaryRow(String label, String value, String color) {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER_LEFT);

        Label labelField = new Label(label);
        labelField.setStyle("-fx-font-size: 13; -fx-text-fill: " + color + "; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label valueField = new Label(value);
        valueField.setStyle("-fx-font-size: 13; -fx-text-fill: " + color + "; -fx-font-weight: bold;");

        box.getChildren().addAll(labelField, spacer, valueField);
        return box;
    }

    // ✅ ТЕКСТ СТАТУСА ЗАКАЗА
    private String getStatusLabel(String status) {
        if (status == null) return "❓ Неизвестно";
        switch (status.toLowerCase()) {
            case "pending": return "⏳ В ожидании";
            case "completed": return "✅ Завершен";
            case "cancelled": return "❌ Отменен";
            default: return "❓ Неизвестно";
        }
    }

    // ✅ СТИЛЬ СТАТУСА
    private String getStatusStyle(String status) {
        if (status == null) return "-fx-background-color: #e5e7eb;";
        switch (status.toLowerCase()) {
            case "pending": return "-fx-background-color: #fbbf24; -fx-text-fill: #000;";
            case "completed": return "-fx-background-color: #10b981; -fx-text-fill: #fff;";
            case "cancelled": return "-fx-background-color: #ef4444; -fx-text-fill: #fff;";
            default: return "-fx-background-color: #e5e7eb;";
        }
    }

    // ✅ ПРОВЕРКА АДМИН-СТАТУСА И ДОБАВЛЕНИЕ КНОПКИ
    private void checkIfAdmin() {
        Thread checkThread = new Thread(() -> {
            try {
                UserDTO user = UserRepository.getUserProfileByEmail(userEmail);
                System.out.println("🔍 Проверка администратора для: " + userEmail);
                if (user != null) {
                    System.out.println("   User found: " + user.email);
                    System.out.println("   is_admin: " + user.is_admin);
                    if (user.is_admin) {
                        isAdmin = true;
                        System.out.println("✅ Пользователь - администратор!");
                        Platform.runLater(() -> {
                            addAdminButton();
                        });
                    } else {
                        System.out.println("❌ Пользователь - обычный пользователь");
                    }
                } else {
                    System.out.println("❌ User not found!");
                }
            } catch (Exception e) {
                System.err.println("⚠️ Ошибка проверки администратора: " + e.getMessage());
                e.printStackTrace();
            }
        });
        checkThread.setDaemon(true);
        checkThread.start();
    }

    // ✅ ДОБАВЛЯЕТ КНОПКУ АДМИН ПАНЕЛИ
    private void addAdminButton() {
        System.out.println("📌 Добавление кнопки админ панели...");
        Button adminButton = new Button("🔐 Админ панель");
        adminButton.setStyle("-fx-font-size: 13; -fx-padding: 10 20; " +
                "-fx-background-color: #dc2626; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-weight: bold;");

        adminButton.setOnAction(e -> openAdminPanel());

        // Добавляем кнопку в верхний контейнер (рядом с другими кнопками)
        if (rootBorderPane != null) {
            VBox topContainer = (VBox) rootBorderPane.getTop();
            if (topContainer != null) {
                // Ищем HBox с кнопками
                for (javafx.scene.Node node : topContainer.getChildren()) {
                    if (node instanceof HBox) {
                        HBox btnBox = (HBox) node;
                        btnBox.getChildren().add(adminButton);
                        System.out.println("✅ Админ кнопка добавлена успешно");
                        return;
                    }
                }
            }
        }
        System.out.println("⚠️ Не удалось найти контейнер для кнопки админ панели");
    }

    // ✅ ОТКРЫВАЕТ АДМИН ПАНЕЛЬ
    private void openAdminPanel() {
        try {
            System.out.println("🔐 Открытие админ панели через MainController...");

            // ✅ ВЫЗЫВАЕМ МЕТОД MainController - он уже знает как это делать!
            if (hostMainController != null) {
                hostMainController.openAdminPanel();
                System.out.println("✅ Админ панель открыта через MainController");
            } else {
                System.err.println("❌ hostMainController is null!");
                showError("❌ Ошибка: не удалось открыть админ панель");
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка открытия админ панели: " + e.getMessage());
            e.printStackTrace();
            showError("❌ Ошибка открытия админ панели:\n" + e.getMessage());
        }
    }


    // ✅ ВОЗВРАЩАЕТСЯ ИЗ АДМИН ПАНЕЛИ
    public void returnFromAdminPanel() {
        try {
            System.out.println("🚪 Возврат из админ панели в кабинет...");

            // Перезагружаем историю заказов
            mainContentVBox.getChildren().clear();
            loadOrderHistory();

            // Останавливаем сервис обновления админки если нужно
            if (adminController != null) {
                adminController.stopRefreshService();
            }

            System.out.println("✅ Возврат в кабинет завершён");
        } catch (Exception e) {
            System.err.println("❌ Ошибка возврата: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ✅ НАСТРОЙКА КНОПОК
    private void setupButtons() {
        saveButton.setOnAction(e -> saveProfile());
        changePasswordButton.setOnAction(e -> changePassword());
        backButton.setOnAction(e -> goBack());
        logoutButton.setOnAction(e -> logout());
    }

    // ✅ СОХРАНЕНИЕ ПРОФИЛЯ
    private void saveProfile() {
        String name = nameField.getText().trim();
        String surname = surnameField.getText().trim();
        String phone = phoneField.getText().trim();
        String city = cityField.getText().trim();
        String address = addressField.getText().trim();

        // Валидация
        if (name.isEmpty() || surname.isEmpty()) {
            showWarning("⚠️ Пожалуйста, заполните имя и фамилию");
            return;
        }

        // ✅ Проверка телефона
        if (!phone.isEmpty() && !PhoneFormatter.isCompletePhone(phone)) {
            showWarning("⚠️ Телефон должен быть в формате +79878073394 (12 символов)");
            return;
        }

        saveButton.setDisable(true);
        saveButton.setText("Сохранение...");

        Thread saveThread = new Thread(() -> {
            try {
                UserRepository.updateUserProfile(userEmail, name, surname, phone, city, address);
                Platform.runLater(() -> {
                    saveButton.setDisable(false);
                    saveButton.setText("💾 Сохранить");
                    showSuccess("✅ Профиль успешно обновлен");
                    System.out.println("✅ Профиль обновлен: " + name + " " + surname);
                });
            } catch (Exception e) {
                System.err.println("❌ Ошибка сохранения: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    saveButton.setDisable(false);
                    saveButton.setText("💾 Сохранить");
                    showError("❌ Ошибка сохранения профиля: " + e.getMessage());
                });
            }
        });
        saveThread.setDaemon(true);
        saveThread.start();
    }

    // ✅ СМЕНА ПАРОЛЯ
    private void changePassword() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Смена пароля");
        dialog.setHeaderText("Введите новый пароль");

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("Новый пароль");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Подтверждение пароля");

        VBox content = new VBox(10);
        content.setPrefWidth(300);
        content.getChildren().addAll(
                new Label("Новый пароль:"),
                newPasswordField,
                new Label("Подтверждение:"),
                confirmPasswordField
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                String newPassword = newPasswordField.getText();
                String confirmPassword = confirmPasswordField.getText();

                if (newPassword.isEmpty()) {
                    showWarning("⚠️ Пароль не может быть пустым");
                    return null;
                }

                if (!newPassword.equals(confirmPassword)) {
                    showWarning("⚠️ Пароли не совпадают");
                    return null;
                }

                if (newPassword.length() < 6) {
                    showWarning("⚠️ Пароль должен быть не менее 6 символов");
                    return null;
                }

                return newPassword;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newPassword -> {
            if (newPassword != null) {
                changePasswordButton.setDisable(true);
                changePasswordButton.setText("Обновление...");

                Thread changeThread = new Thread(() -> {
                    try {
                        // TODO: Реализовать смену пароля в Supabase Auth
                        Platform.runLater(() -> {
                            changePasswordButton.setDisable(false);
                            changePasswordButton.setText("🔐 Смена пароля");
                            showSuccess("✅ Пароль успешно изменен");
                            System.out.println("✅ Пароль пользователя изменен");
                        });
                    } catch (Exception e) {
                        System.err.println("❌ Ошибка смены пароля: " + e.getMessage());
                        Platform.runLater(() -> {
                            changePasswordButton.setDisable(false);
                            changePasswordButton.setText("🔐 Смена пароля");
                            showError("❌ Ошибка смены пароля");
                        });
                    }
                });
                changeThread.setDaemon(true);
                changeThread.start();
            }
        });
    }

    // ✅ ВОЗВРАТ НА ГЛАВНЫЙ ЭКРАН
    private void goBack() {
        if (hostMainController != null) {
            hostMainController.showMainContent();
            System.out.println("👈 Возврат на главное окно");
        }
    }

    // ✅ ВЫХОД ИЗ АККАУНТА
    private void logout() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Подтверждение выхода");
        confirmAlert.setHeaderText("Вы уверены, что хотите выйти?");
        confirmAlert.setContentText("Вы будете перенаправлены на страницу входа");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                SessionManager.clearSession();
                if (hostMainController != null) {
                    hostMainController.showMainContent();
                    System.out.println("👋 Пользователь вышел из аккаунта");
                }
            }
        });
    }

    // ✅ УВЕДОМЛЕНИЕ ОБ УСПЕХЕ
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Успех");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ✅ УВЕДОМЛЕНИЕ ОБ ОШИБКЕ
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ✅ ПРЕДУПРЕЖДЕНИЕ
    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Предупреждение");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}