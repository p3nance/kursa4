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
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * ✅ Контроллер личного кабинета пользователя
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
            userId    = SessionManager.getUserId();

            if (userEmail != null) {
                userEmailLabel.setText(userEmail);
                emailField.setText(userEmail);
                emailField.setEditable(false);

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

    // ═══════════════════════════════════════════════════════
    // ЗАГРУЗКА ДАННЫХ
    // ═══════════════════════════════════════════════════════

    private void loadUserData() {
        Thread t = new Thread(() -> {
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
        t.setDaemon(true);
        t.start();
    }

    private void loadOrderHistory() {
        Thread t = new Thread(() -> {
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
        t.setDaemon(true);
        t.start();
    }

    // ═══════════════════════════════════════════════════════
    // КАРТОЧКА ЗАКАЗА
    // ═══════════════════════════════════════════════════════

    private VBox createOrderCard(OrderDTO order) {
        VBox card = new VBox(10);
        card.setStyle("-fx-border-color: #e5e7eb; -fx-border-radius: 12; " +
                "-fx-background-color: #f9fafb; -fx-background-radius: 12; " +
                "-fx-padding: 16; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 2,0,0,1);");

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

        VBox itemsBox = new VBox(5);
        itemsBox.setStyle("-fx-padding: 10; -fx-background-color: #ffffff; " +
                "-fx-background-radius: 8; -fx-border-color: #e5e7eb; -fx-border-width: 1;");

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

        VBox summaryBox = new VBox(8);
        summaryBox.setStyle("-fx-padding: 12; -fx-background-color: #f9fafb; -fx-border-radius: 8;");
        summaryBox.getChildren().add(createSummaryRow("Сумма:", String.format("%.2f ₽", order.totalAmount), "#666"));

        if (order.discountAmount > 0) {
            summaryBox.getChildren().add(createSummaryRow("Скидка:",
                    String.format("-%.2f ₽", order.discountAmount), "#ef4444"));
        }
        summaryBox.getChildren().add(new Separator());
        summaryBox.getChildren().add(createSummaryRow("К оплате:",
                String.format("%.2f ₽", order.finalAmount), "#059669"));

        card.getChildren().addAll(headerBox, itemsBox, summaryBox);
        return card;
    }

    private HBox createSummaryRow(String label, String value, String color) {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 13; -fx-text-fill: " + color + "; -fx-font-weight: bold;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label val = new Label(value);
        val.setStyle("-fx-font-size: 13; -fx-text-fill: " + color + "; -fx-font-weight: bold;");

        box.getChildren().addAll(lbl, sp, val);
        return box;
    }

    private String getStatusLabel(String status) {
        if (status == null) return "❓ Неизвестно";
        switch (status.toLowerCase()) {
            case "pending":   return "⏳ В ожидании";
            case "completed": return "✅ Завершен";
            case "cancelled": return "❌ Отменен";
            default:          return "❓ Неизвестно";
        }
    }

    private String getStatusStyle(String status) {
        if (status == null) return "-fx-background-color: #e5e7eb;";
        switch (status.toLowerCase()) {
            case "pending":   return "-fx-background-color: #fbbf24; -fx-text-fill: #000;";
            case "completed": return "-fx-background-color: #10b981; -fx-text-fill: #fff;";
            case "cancelled": return "-fx-background-color: #ef4444; -fx-text-fill: #fff;";
            default:          return "-fx-background-color: #e5e7eb;";
        }
    }

    // ═══════════════════════════════════════════════════════
    // АДМИН
    // ═══════════════════════════════════════════════════════

    private void checkIfAdmin() {
        Thread t = new Thread(() -> {
            try {
                UserDTO user = UserRepository.getUserProfileByEmail(userEmail);
                if (user != null && user.is_admin) {
                    isAdmin = true;
                    System.out.println("✅ Пользователь — администратор!");
                    Platform.runLater(this::addAdminButton);
                }
            } catch (Exception e) {
                System.err.println("⚠️ Ошибка проверки администратора: " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void addAdminButton() {
        Button adminButton = new Button("🔐 Админ панель");
        adminButton.setStyle("-fx-font-size: 13; -fx-padding: 10 20; " +
                "-fx-background-color: #dc2626; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-weight: bold;");
        adminButton.setOnAction(e -> openAdminPanel());

        if (rootBorderPane != null) {
            VBox topContainer = (VBox) rootBorderPane.getTop();
            if (topContainer != null) {
                for (javafx.scene.Node node : topContainer.getChildren()) {
                    if (node instanceof HBox) {
                        ((HBox) node).getChildren().add(adminButton);
                        System.out.println("✅ Админ кнопка добавлена");
                        return;
                    }
                }
            }
        }
    }

    private void openAdminPanel() {
        try {
            if (hostMainController != null) {
                hostMainController.openAdminPanel();
            } else {
                showError("❌ Ошибка: не удалось открыть админ панель");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError("❌ Ошибка открытия админ панели:\n" + e.getMessage());
        }
    }

    public void returnFromAdminPanel() {
        try {
            mainContentVBox.getChildren().clear();
            loadOrderHistory();
            if (adminController != null) adminController.stopRefreshService();
        } catch (Exception e) {
            System.err.println("❌ Ошибка возврата: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    // КНОПКИ
    // ═══════════════════════════════════════════════════════

    private void setupButtons() {
        saveButton.setOnAction(e -> saveProfile());
        changePasswordButton.setOnAction(e -> showChangePasswordDialog());
        backButton.setOnAction(e -> goBack());
        logoutButton.setOnAction(e -> logout());
    }

    // ═══════════════════════════════════════════════════════
    // СОХРАНЕНИЕ ПРОФИЛЯ
    // ═══════════════════════════════════════════════════════

    private void saveProfile() {
        String name    = nameField.getText().trim();
        String surname = surnameField.getText().trim();
        String phone   = phoneField.getText().trim();
        String city    = cityField.getText().trim();
        String address = addressField.getText().trim();

        if (name.isEmpty() || surname.isEmpty()) {
            showWarning("⚠️ Пожалуйста, заполните имя и фамилию");
            return;
        }
        if (!phone.isEmpty() && !PhoneFormatter.isCompletePhone(phone)) {
            showWarning("⚠️ Телефон должен быть в формате +79878073394 (12 символов)");
            return;
        }

        saveButton.setDisable(true);
        saveButton.setText("Сохранение...");

        Thread t = new Thread(() -> {
            try {
                UserRepository.updateUserProfile(userEmail, name, surname, phone, city, address);
                Platform.runLater(() -> {
                    saveButton.setDisable(false);
                    saveButton.setText("💾 Сохранить");
                    showSuccess("✅ Профиль успешно обновлен");
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    saveButton.setDisable(false);
                    saveButton.setText("💾 Сохранить");
                    showError("❌ Ошибка сохранения профиля: " + e.getMessage());
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ═══════════════════════════════════════════════════════
    // ОКНО СМЕНЫ ПАРОЛЯ
    // ═══════════════════════════════════════════════════════

    private void showChangePasswordDialog() {

        // Затемнение
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.45);");
        overlay.setPrefSize(rootBorderPane.getWidth(), rootBorderPane.getHeight());

        // Карточка — фиксированный размер
        VBox card = new VBox(16);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPrefWidth(400);
        card.setMaxWidth(400);
        card.setMinWidth(400);
        card.setPadding(new Insets(30));
        card.setStyle(
                "-fx-background-color: #ffffff;" +
                        "-fx-background-radius: 18;" +
                        "-fx-border-radius: 18;" +
                        "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.25), 30, 0, 0, 8);"
        );
        StackPane.setAlignment(card, Pos.CENTER);

        // Заголовок
        HBox titleBox = new HBox(10);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("🔑");
        icon.setStyle("-fx-font-size: 20px;");
        Label titleLbl = new Label("Смена пароля");
        titleLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        titleBox.getChildren().addAll(icon, titleLbl);

        Separator sep = new Separator();

        // Поле: текущий пароль
        Label oldPassLabel = new Label("Текущий пароль");
        oldPassLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
        PasswordField oldPasswordField = new PasswordField();
        oldPasswordField.setPromptText("Введите текущий пароль");
        oldPasswordField.setMaxWidth(Double.MAX_VALUE);
        oldPasswordField.setStyle(fieldStyle(false));

        // Поле: новый пароль
        Label newPassLabel = new Label("Новый пароль");
        newPassLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("Минимум 6 символов");
        newPasswordField.setMaxWidth(Double.MAX_VALUE);
        newPasswordField.setStyle(fieldStyle(false));

        // Поле: подтверждение
        Label confirmPassLabel = new Label("Подтверждение пароля");
        confirmPassLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Повторите новый пароль");
        confirmPasswordField.setMaxWidth(Double.MAX_VALUE);
        confirmPasswordField.setStyle(fieldStyle(false));

        // Inline-ошибка
        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(340);
        errorLabel.setStyle(
                "-fx-font-size: 12px; -fx-text-fill: #ef4444;" +
                        "-fx-padding: 8 12; -fx-background-color: #fef2f2;" +
                        "-fx-background-radius: 6; -fx-border-radius: 6;" +
                        "-fx-border-color: #fecaca; -fx-border-width: 1;"
        );
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // Кнопки
        Button confirmBtn = new Button("✅  Сменить пароль");
        confirmBtn.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #3b82f6, #2563eb);" +
                        "-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;" +
                        "-fx-padding: 11 0; -fx-background-radius: 10; -fx-border-width: 0;" +
                        "-fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.4), 8, 0, 0, 3);"
        );
        confirmBtn.setMaxWidth(Double.MAX_VALUE);

        Button cancelBtn = new Button("Отмена");
        cancelBtn.setStyle(
                "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;" +
                        "-fx-font-size: 13px; -fx-font-weight: bold;" +
                        "-fx-padding: 11 0; -fx-background-radius: 10; -fx-border-width: 0;" +
                        "-fx-cursor: hand;"
        );
        cancelBtn.setMaxWidth(Double.MAX_VALUE);

        HBox btnBox = new HBox(10);
        btnBox.getChildren().addAll(confirmBtn, cancelBtn);
        HBox.setHgrow(confirmBtn, Priority.ALWAYS);
        HBox.setHgrow(cancelBtn, Priority.ALWAYS);

        card.getChildren().addAll(
                titleBox, sep,
                oldPassLabel, oldPasswordField,
                newPassLabel, newPasswordField,
                confirmPassLabel, confirmPasswordField,
                errorLabel, btnBox
        );

        overlay.getChildren().add(card);

        // Встраиваем overlay поверх текущего контента
        StackPane root = new StackPane();
        javafx.scene.Node currentCenter = rootBorderPane.getCenter();
        rootBorderPane.setCenter(root);
        root.getChildren().addAll(currentCenter, overlay);

        // Закрытие
        Runnable closeDialog = () -> {
            root.getChildren().remove(overlay);
            rootBorderPane.setCenter(currentCenter);
        };

        cancelBtn.setOnAction(e -> closeDialog.run());
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) closeDialog.run();
        });

        // Логика подтверждения
        confirmBtn.setOnAction(e -> {
            String oldPassword     = oldPasswordField.getText();
            String newPassword     = newPasswordField.getText();
            String confirmPassword = confirmPasswordField.getText();

            // Сброс подсветки
            oldPasswordField.setStyle(fieldStyle(false));
            newPasswordField.setStyle(fieldStyle(false));
            confirmPasswordField.setStyle(fieldStyle(false));
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);

            // Валидация
            if (oldPassword.isEmpty()) {
                oldPasswordField.setStyle(fieldStyle(true));
                showFieldError(errorLabel, "⚠️ Введите текущий пароль");
                return;
            }
            if (newPassword.isEmpty() || newPassword.length() < 6) {
                newPasswordField.setStyle(fieldStyle(true));
                showFieldError(errorLabel, "⚠️ Новый пароль — минимум 6 символов");
                return;
            }
            if (newPassword.equals(oldPassword)) {
                newPasswordField.setStyle(fieldStyle(true));
                showFieldError(errorLabel, "⚠️ Новый пароль совпадает со старым");
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                confirmPasswordField.setStyle(fieldStyle(true));
                showFieldError(errorLabel, "⚠️ Пароли не совпадают");
                return;
            }

            confirmBtn.setDisable(true);
            confirmBtn.setText("Проверка...");

            Thread t = new Thread(() -> {
                try {
                    // Шаг 1: проверяем старый пароль
                    UserRepository.verifyPassword(userEmail, oldPassword);

                    // Шаг 2: меняем пароль через Admin API
                    UserRepository.updatePassword(userId, newPassword);

                    Platform.runLater(() -> {
                        closeDialog.run();
                        showSuccess("✅ Пароль успешно изменён");
                        System.out.println("✅ Пароль изменён");
                    });
                } catch (Exception ex) {
                    System.err.println("❌ Ошибка смены пароля: " + ex.getMessage());
                    Platform.runLater(() -> {
                        confirmBtn.setDisable(false);
                        confirmBtn.setText("✅  Сменить пароль");
                        showFieldError(errorLabel, "❌ " + ex.getMessage());
                    });
                }
            });
            t.setDaemon(true);
            t.start();
        });
    }

    /** Стиль поля: нормальный или с красной рамкой */
    private String fieldStyle(boolean error) {
        String border = error ? "#ef4444" : "#cbd5e1";
        String bg     = error ? "#fef2f2" : "#f8fafc";
        return "-fx-padding: 10; -fx-font-size: 13px;" +
                "-fx-background-color: " + bg + ";" +
                "-fx-border-color: " + border + ";" +
                "-fx-border-radius: 8; -fx-background-radius: 8;";
    }

    /** Inline-ошибка внутри диалога */
    private void showFieldError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    // ═══════════════════════════════════════════════════════
    // НАВИГАЦИЯ
    // ═══════════════════════════════════════════════════════

    private void goBack() {
        if (hostMainController != null) {
            hostMainController.showMainContent();
            System.out.println("👈 Возврат на главное окно");
        }
    }

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

    // ═══════════════════════════════════════════════════════
    // АЛЕРТЫ
    // ═══════════════════════════════════════════════════════

    private void showSuccess(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Успех"); a.setHeaderText(null); a.setContentText(message);
        a.showAndWait();
    }

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Ошибка"); a.setHeaderText(null); a.setContentText(message);
        a.showAndWait();
    }

    private void showWarning(String message) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Предупреждение"); a.setHeaderText(null); a.setContentText(message);
        a.showAndWait();
    }
}