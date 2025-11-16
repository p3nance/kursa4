package controllers;

import com.example.authapp.models.Cart;
import com.example.authapp.models.Cart.CartItem;
import com.example.authapp.services.CartService;
import com.example.authapp.services.OrderService;
import com.example.authapp.services.ProductService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class CartController implements Initializable {

    @FXML private VBox cartItemsContainer;
    @FXML private TextField promoCodeField;
    @FXML private Button applyPromoButton;
    @FXML private Label discountLabel;
    @FXML private Label totalLabel;
    @FXML private Button continueShopping;
    @FXML private Button checkoutButton;

    private MainController mainController;
    private CartService cartService;
    private ProductService productService;
    private OrderService orderService;

    private double appliedDiscount = 0;
    private String appliedPromoCode = null;
    private boolean isCheckingOut = false; // ✅ Флаг для предотвращения двойного клика

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        System.out.println("📦 Инициализация CartController...");

        try {
            cartService = new CartService();
            productService = new ProductService();
            orderService = new OrderService(cartService, productService);

            cartService.loadUserCart();
            System.out.println("✅ Корзина загружена");

        } catch (Exception e) {
            System.err.println("⚠️ Ошибка загрузки корзины: " + e.getMessage());
        }

        if (mainController != null) {
            mainController.hideCategoriesAndSearch();
        }

        loadCartItems();
        setupButtons();
        updateTotal();
    }

    public void setMainController(MainController controller) {
        this.mainController = controller;
    }

    private void setupButtons() {
        if (continueShopping != null) {
            continueShopping.setOnAction(e -> {
                appliedDiscount = 0;
                appliedPromoCode = null;
                promoCodeField.clear();
                if (mainController != null) {
                    mainController.showCategoriesAndSearch();
                }
                goBack();
            });
        }

        if (checkoutButton != null) {
            checkoutButton.setOnAction(e -> checkout());
        }

        if (applyPromoButton != null) {
            applyPromoButton.setOnAction(e -> applyPromo());
        }
    }

    public void loadCartItems() {
        if (cartItemsContainer == null) return;

        cartItemsContainer.getChildren().clear();

        for (CartItem item : Cart.getInstance().getItems()) {
            cartItemsContainer.getChildren().add(createCartItemCard(item));
        }

        updateTotal();
    }

    private HBox createCartItemCard(CartItem item) {
        HBox box = new HBox(18);
        box.setStyle("-fx-background-color: #fff; -fx-border-color: #e5e7eb; -fx-border-radius: 12; " +
                "-fx-background-radius: 12; -fx-padding: 16 20 16 20; " +
                "-fx-effect: dropshadow(gaussian, rgba(24,32,64,0.06), 2,0,0,1);");
        box.setAlignment(Pos.CENTER_LEFT);

        // Изображение
        ImageView image = new ImageView();
        image.setFitHeight(52);
        image.setFitWidth(52);
        image.setPreserveRatio(true);

        String imgUrl = item.getProduct().getImageUrl();
        try {
            if (imgUrl != null && !imgUrl.isBlank()) {
                image.setImage(new Image(imgUrl, true));
            }
        } catch (Exception ignored) {}

        // Информация о товаре
        VBox info = new VBox(5);
        Label name = new Label(item.getProduct().getName());
        name.setFont(Font.font("Segoe UI", 15));
        name.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label price = new Label(String.format("%.2f ₽", item.getProduct().getPrice()));
        price.setStyle("-fx-font-size: 13px; -fx-text-fill: #3b82f6;");

        info.getChildren().addAll(name, price);

        // Контроль количества
        Spinner<Integer> quantity = new Spinner<>(1, 99, item.getQuantity());
        quantity.setMaxWidth(58);
        quantity.setEditable(false);

        quantity.valueProperty().addListener((obs, oldVal, newVal) -> {
            try {
                cartService.updateCartItemQuantity(item.getProduct(), newVal);
                updateTotal();
                System.out.println("✅ Количество обновлено: " + item.getProduct().getName() + " -> " + newVal);
            } catch (Exception e) {
                showAlert("Ошибка", "Не удалось обновить количество: " + e.getMessage());
                quantity.getValueFactory().setValue(oldVal);
            }
        });

        // ✅ Кнопка удаления с КРАСНЫМ стилем
        Button remove = new Button("🗑");
        remove.setStyle("-fx-font-size: 14px; -fx-padding: 8px 12px; " +
                "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-background-radius: 6; -fx-cursor: hand;");
        remove.setOnMouseEntered(e -> remove.setStyle("-fx-font-size: 14px; -fx-padding: 8px 12px; " +
                "-fx-background-color: #b91c1c; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-background-radius: 6; -fx-cursor: hand;"));
        remove.setOnMouseExited(e -> remove.setStyle("-fx-font-size: 14px; -fx-padding: 8px 12px; " +
                "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-background-radius: 6; -fx-cursor: hand;"));

        remove.setOnAction(e -> {
            try {
                cartService.removeFromCart(item.getProduct());
                loadCartItems();
                updateTotal();
                System.out.println("✅ Товар удален: " + item.getProduct().getName());
            } catch (Exception ex) {
                showAlert("Ошибка", "Не удалось удалить товар: " + ex.getMessage());
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        box.getChildren().addAll(image, info, spacer, quantity, remove);
        return box;
    }

    /**
     * ✅ Применяет промокод
     */
    private void applyPromo() {
        String code = promoCodeField.getText().trim();

        if (code.isEmpty()) {
            showAlert("Ошибка", "Введите код промокода");
            return;
        }

        try {
            double discount = cartService.applyDiscount(code);

            appliedDiscount = discount;
            appliedPromoCode = code;

            System.out.println("✅ Промокод применен: " + code + " Скидка: " + discount + " ₽");

            showAlert("Успех", String.format("Промокод '%s' применен!\nСкидка: %.2f ₽", code, discount));
            updateTotal();

        } catch (Exception e) {
            System.err.println("❌ Ошибка применения промокода: " + e.getMessage());
            showAlert("Ошибка", "Неверный промокод: " + e.getMessage());
            appliedDiscount = 0;
            appliedPromoCode = null;
            updateTotal();
        }
    }

    /**
     * ✅ Обновляет итоговую сумму
     */
    private void updateTotal() {
        double total = Cart.getInstance().getTotal();
        double finalTotal = total - appliedDiscount;

        if (discountLabel != null) {
            discountLabel.setText(String.format("Скидка: %.2f ₽", appliedDiscount));
        }

        if (totalLabel != null) {
            totalLabel.setText(String.format("Итого: %,.2f ₽", finalTotal));
        }
    }

    /**
     * ✅ ГЛАВНЫЙ МЕТОД: Оформление заказа
     */
    @FXML
    private void checkout() {
        // ✅ Проверяем флаг, чтобы избежать двойного клика
        if (isCheckingOut) {
            System.out.println("⚠️ Оформление уже в процессе, ждите...");
            return;
        }

        if (Cart.getInstance().getItems().isEmpty()) {
            showAlert("Ошибка", "Корзина пуста");
            return;
        }

        System.out.println("\n🛒 ========== НАЧАЛО ОФОРМЛЕНИЯ ЗАКАЗА ==========");

        // Диалог подтверждения
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Подтверждение заказа");
        confirmDialog.setHeaderText("Оформить заказ?");

        double total = Cart.getInstance().getTotal();
        double finalTotal = total - appliedDiscount;

        String message = String.format(
                "Товаров в корзине: %d\n\n" +
                        "Сумма: %.2f ₽\n" +
                        "Скидка: %.2f ₽\n" +
                        "Итого к оплате: %.2f ₽",
                Cart.getInstance().getTotalQuantity(),
                total,
                appliedDiscount,
                finalTotal
        );

        confirmDialog.setContentText(message);

        // ✅ ИСПРАВЛЕНО: Правильно обрабатываем результат
        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            System.out.println("❌ Оформление отменено пользователем");
            return;
        }

        // ✅ Устанавливаем флаг
        isCheckingOut = true;
        checkoutButton.setDisable(true);

        // ✅ Выполняем оформление в отдельном потоке
        Thread checkoutThread = new Thread(() -> {
            try {
                System.out.println("📤 Отправка заказа на сервер...");

                // ✅ Используем OrderService
                int orderId = orderService.createOrderFromCart(appliedPromoCode);

                Platform.runLater(() -> {
                    // Очищаем UI
                    appliedDiscount = 0;
                    appliedPromoCode = null;
                    promoCodeField.clear();
                    discountLabel.setText("Скидка: -");
                    loadCartItems();
                    updateTotal();

                    // Показываем сообщение об успехе
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("✅ Успешно");
                    successAlert.setHeaderText("Заказ оформлен");
                    successAlert.setContentText(
                            String.format(
                                    "✅ Ваш заказ #%d успешно создан!\n\n" +
                                            "Сумма: %.2f ₽\n" +
                                            "Скидка: %.2f ₽\n" +
                                            "К оплате: %.2f ₽\n\n" +
                                            "Спасибо за покупку!\n" +
                                            "История заказов доступна в личном кабинете.",
                                    orderId,
                                    total,
                                    appliedDiscount,
                                    (total - appliedDiscount)
                            )
                    );
                    successAlert.showAndWait();

                    // Возвращаемся в главное меню
                    if (mainController != null) {
                        mainController.showMainContent();
                    }

                    System.out.println("✅ Заказ #" + orderId + " успешно завершен");

                    // ✅ Сбрасываем флаг
                    isCheckingOut = false;
                    checkoutButton.setDisable(false);
                });

            } catch (Exception e) {
                System.err.println("❌ ОШИБКА ОФОРМЛЕНИЯ ЗАКАЗА: " + e.getMessage());
                e.printStackTrace();

                Platform.runLater(() -> {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("❌ Ошибка");
                    errorAlert.setHeaderText("Ошибка оформления заказа");
                    errorAlert.setContentText(
                            "Не удалось оформить заказ:\n\n" + e.getMessage()
                    );
                    errorAlert.showAndWait();

                    System.err.println("❌ Заказ не был создан");

                    // ✅ Сбрасываем флаг при ошибке
                    isCheckingOut = false;
                    checkoutButton.setDisable(false);
                });
            }
        });

        checkoutThread.setDaemon(true);
        checkoutThread.start();
    }

    private void goBack() {
        if (mainController != null) {
            mainController.showMainContent();
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
