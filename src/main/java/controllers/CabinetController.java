package controllers;

import config.SessionManager;

import com.example.authapp.repositories.UserRepository;

import com.example.authapp.dto.UserDTO;

import com.example.authapp.dto.OrderDTO;

import com.example.authapp.dto.OrderItemDTO;

import com.example.authapp.services.OrderService;

import com.example.authapp.services.CartService;

import com.example.authapp.services.ProductService;

import javafx.application.Platform;

import javafx.fxml.FXML;

import javafx.fxml.Initializable;

import javafx.geometry.Pos;

import javafx.scene.Node;

import javafx.scene.Parent;

import javafx.scene.Scene;

import javafx.scene.control.*;

import javafx.scene.layout.*;

import javafx.stage.Stage;

import javafx.fxml.FXMLLoader;

import java.net.URL;

import java.util.List;

import java.util.ResourceBundle;

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

    // ✅ ДОБАВЛЯЕМ ССЫЛКУ НА ROOT BORDERPANE (КОНТЕЙНЕР ВСЕЙ ПАНЕЛИ)
    @FXML private BorderPane rootBorderPane;

    private static MainController hostMainController;
    private OrderService orderService;
    private String userEmail;
    private boolean isAdmin = false;

    // ✅ ДОБАВЛЯЕМ ПЕРЕМЕННУЮ ДЛЯ АДМИН-КОНТРОЛЛЕРА
    private AdminController adminController = null;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        System.out.println("👤 Инициализация CabinetController...");
        try {
            CartService cartService = new CartService();
            ProductService productService = new ProductService();
            orderService = new OrderService(cartService, productService);
            userEmail = SessionManager.getUserEmail();

            if (userEmail != null) {
                userEmailLabel.setText(userEmail);
                emailField.setText(userEmail);
                loadUserData();
                setupButtons();
                loadOrderHistory();
                checkIfAdmin();
                System.out.println("✅ CabinetController инициализирован");
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка инициализации: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void setHostMainController(MainController controller) {
        hostMainController = controller;
    }

    /**
     * ✅ Загружает данные пользователя
     */
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
                        System.out.println(" Email: " + user.email);
                        System.out.println(" Is Admin: " + user.is_admin);
                    }
                });
            } catch (Exception e) {
                System.err.println("❌ Ошибка загрузки профиля: " + e.getMessage());
                e.printStackTrace();
            }
        });
        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * ✅ Загружает историю заказов
     */
    private void loadOrderHistory() {
        Thread loadThread = new Thread(() -> {
            try {
                List<OrderDTO> orders = orderService.getUserOrderHistory();
                Platform.runLater(() -> {
                    VBox ordersVBox = new VBox(15);
                    ordersVBox.setStyle("-fx-background-color: #ffffff; -fx-padding: 20; -fx-border-color: #e5e7eb; -fx-border-width: 1 0 0 0;");

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
            }
        });
        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * ✅ Создает карточку заказа
     */
    private VBox createOrderCard(OrderDTO order) {
        VBox cardVBox = new VBox(10);
        cardVBox.setStyle("-fx-border-color: #e5e7eb; -fx-border-radius: 12; " +
                "-fx-background-color: #f9fafb; -fx-background-radius: 12; " +
                "-fx-padding: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 2,0,0,1);");

        // Заголовок
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

        // Товары
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
        }

        // Суммы
        VBox summaryBox = new VBox(8);
        summaryBox.setStyle("-fx-padding: 12; -fx-background-color: #f9fafb; -fx-border-radius: 8;");
        HBox totalBox = createSummaryRow("Сумма:", String.format("%.2f ₽", order.totalAmount), "#666");
        if (order.discountAmount > 0) {
            HBox discountBox = createSummaryRow("Скидка:", String.format("-%.2f ₽", order.discountAmount), "#ef4444");
            summaryBox.getChildren().addAll(totalBox, discountBox, new Separator());
        } else {
            summaryBox.getChildren().addAll(totalBox, new Separator());
        }
        HBox finalBox = createSummaryRow("К оплате:", String.format("%.2f ₽", order.finalAmount), "#059669");
        summaryBox.getChildren().add(finalBox);

        cardVBox.getChildren().addAll(headerBox, itemsBox, summaryBox);
        return cardVBox;
    }

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

    private String getStatusLabel(String status) {
        switch (status) {
            case "pending": return "⏳ В ожидании";
            case "completed": return "✅ Завершен";
            case "cancelled": return "❌ Отменен";
            default: return "❓ Неизвестно";
        }
    }

    private String getStatusStyle(String status) {
        switch (status) {
            case "pending": return "-fx-background-color: #fef3c7; -fx-text-fill: #92400e;";
            case "completed": return "-fx-background-color: #d1fae5; -fx-text-fill: #065f46;";
            case "cancelled": return "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b;";
            default: return "-fx-background-color: #e5e7eb; -fx-text-fill: #374151;";
        }
    }

    /**
     * ✅ Проверяет администратора и добавляет кнопку
     */
    private void checkIfAdmin() {
        Thread checkThread = new Thread(() -> {
            try {
                UserDTO user = UserRepository.getUserProfileByEmail(userEmail);
                System.out.println("🔍 Проверка администратора для: " + userEmail);
                if (user != null) {
                    System.out.println(" User found: " + user.email);
                    System.out.println(" is_admin: " + user.is_admin);
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

    /**
     * ✅ Добавляет кнопку админ панели
     */
    private void addAdminButton() {
        System.out.println("📌 Добавление кнопки админ панели...");
        Button adminButton = new Button("🔐 Админ панель");
        adminButton.setStyle("-fx-font-size: 13; -fx-padding: 10 20; " +
                "-fx-background-color: #dc2626; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-weight: bold;");

        // ✅ ИСПРАВЛЕНО: Вызываем MainController.openAdminPanel()
        adminButton.setOnAction(e -> {
            if (hostMainController != null) {
                hostMainController.openAdminPanel();  // ✅ ВОТ ЭТА СТРОКА
            } else {
                System.err.println("❌ hostMainController is null!");
            }
        });

        // Ищем VBox с кнопками безопасности
        for (Node node : mainContentVBox.getChildren()) {
            if (node instanceof VBox) {
                VBox vbox = (VBox) node;
                for (Node child : vbox.getChildren()) {
                    if (child instanceof Label) {
                        Label lbl = (Label) child;
                        if (lbl.getText().contains("Безопасность")) {
                            for (Node sibling : vbox.getChildren()) {
                                if (sibling instanceof HBox) {
                                    HBox btnBox = (HBox) sibling;
                                    btnBox.getChildren().add(0, adminButton);
                                    System.out.println("✅ Админ кнопка добавлена и вызывает openAdminPanel()");
                                    return;
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
        System.out.println("❌ Не удалось добавить админ кнопку!");
    }


    private void setupButtons() {
        saveButton.setOnAction(e -> saveUserData());
        changePasswordButton.setOnAction(e -> showChangePasswordDialog());
        backButton.setOnAction(e -> goBack());
        logoutButton.setOnAction(e -> logout());
    }

    private void saveUserData() {
        String name = nameField.getText().trim();
        String surname = surnameField.getText().trim();
        if (name.isEmpty() || surname.isEmpty()) {
            showAlert("Ошибка", "⚠️ Введите имя и фамилию", Alert.AlertType.WARNING);
            return;
        }

        Thread saveThread = new Thread(() -> {
            try {
                UserRepository.updateUserProfile(userEmail, name, surnameField.getText(),
                        phoneField.getText(), cityField.getText(), addressField.getText());
                Platform.runLater(() -> showAlert("Успех", "✅ Профиль обновлен", Alert.AlertType.INFORMATION));
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Ошибка", "❌ Ошибка сохранения", Alert.AlertType.ERROR));
            }
        });
        saveThread.setDaemon(true);
        saveThread.start();
    }

    private void showChangePasswordDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Изменение пароля");
        dialog.setHeaderText("🔒 Установите новый пароль");
        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 15;");
        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText("Новый пароль");
        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Подтверждение пароля");
        content.getChildren().addAll(
                new Label("Новый пароль:"), newPassword,
                new Label("Подтверждение:"), confirmPassword
        );
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                if (newPassword.getText().isEmpty()) {
                    showAlert("Ошибка", "⚠️ Введите пароль", Alert.AlertType.WARNING);
                    return null;
                }
                if (!newPassword.getText().equals(confirmPassword.getText())) {
                    showAlert("Ошибка", "⚠️ Пароли не совпадают", Alert.AlertType.WARNING);
                    return null;
                }
                showAlert("Успех", "✅ Пароль изменен", Alert.AlertType.INFORMATION);
            }
            return null;
        });
        dialog.showAndWait();
    }

    /**
     * ✅ ИСПРАВЛЕНИЕ: Открывает админ панель как отдельное окно или с полной заменой интерфейса
     */
    private void openAdminPanel() {
        try {
            System.out.println("🔐 Открытие админ панели...");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/admin.fxml"));
            BorderPane adminNode = loader.load();

            // ✅ ПОЛУЧАЕМ КОНТРОЛЛЕР АДМИНКИ ДЛЯ ДАЛЬНЕЙШЕЙ РАБОТЫ
            adminController = loader.getController();
            adminController.setMainController(hostMainController);
            adminController.setCabinetController(this); // ✅ ПЕРЕДАЁМ КАБИНЕТ АДМИНКЕ

            // ✅ ЗАМЕНЯЕМ ВЕСЬ BORDERPANE СОДЕРЖИМОЕ НА АДМИНКУ
            // Получаем родителя rootBorderPane (обычно это Scene или StackPane)
            Parent parent = rootBorderPane.getParent();

            if (parent instanceof Pane) {
                Pane pane = (Pane) parent;
                pane.getChildren().clear();
                pane.getChildren().add(adminNode);
                System.out.println("✅ Админ панель полностью загружена");
            } else if (parent instanceof StackPane) {
                StackPane stackPane = (StackPane) parent;
                stackPane.getChildren().clear();
                stackPane.getChildren().add(adminNode);
                System.out.println("✅ Админ панель полностью загружена в StackPane");
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка открытия админ панели: " + e.getMessage());
            e.printStackTrace();
            showAlert("Ошибка", "❌ Не удалось открыть админ панель", Alert.AlertType.ERROR);
        }
    }

    /**
     * ✅ Возвращает кабинет из админ панели
     */
    public void returnFromAdminPanel() {
        try {
            System.out.println("🚪 Возврат из админ панели в кабинет...");

            // Перезагружаем данные кабинета
            mainContentVBox.getChildren().clear();
            loadOrderHistory();

            // Останавливаем сервис если нужно
            if (adminController != null) {
                adminController.stopRefreshService();
            }

            System.out.println("✅ Возврат в кабинет завершён");
        } catch (Exception e) {
            System.err.println("❌ Ошибка возврата: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void goBack() {
        if (hostMainController != null) {
            hostMainController.showMainContent();
        }
    }

    private void logout() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Выход");
        confirmAlert.setContentText("🚪 Вы выйдете из аккаунта?");
        if (confirmAlert.showAndWait().get() == ButtonType.OK) {
            try {
                SessionManager.clearSession();
                goBack();
            } catch (Exception e) {
                showAlert("Ошибка", "❌ Ошибка при выходе", Alert.AlertType.ERROR);
            }
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}