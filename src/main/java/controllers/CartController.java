package controllers;

import com.example.authapp.models.Cart;
import com.example.authapp.models.Cart.CartItem;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import java.net.URL;
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
    private double discountPercent = 0;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (mainController != null) mainController.hideCategoriesAndSearch();
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
                if (mainController != null) mainController.showCategoriesAndSearch();
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
        box.setStyle("-fx-background-color: #fff; -fx-border-color: #e5e7eb; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 16 20 16 20; -fx-effect: dropshadow(gaussian, rgba(24,32,64,0.06), 2,0,0,1);");
        box.setAlignment(Pos.CENTER_LEFT);
        ImageView image = new ImageView();
        image.setFitHeight(52);
        image.setFitWidth(52);
        image.setPreserveRatio(true);
        String imgUrl = item.getProduct().getImageUrl();
        try {
            if (imgUrl != null && !imgUrl.isBlank())
                image.setImage(new Image(imgUrl, true));
        } catch (Exception ignored) {}
        VBox info = new VBox(5);
        Label name = new Label(item.getProduct().getName());
        name.setFont(Font.font("Segoe UI", 15));
        name.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label price = new Label(String.format("%.2f ₽", item.getProduct().getPrice()));
        price.setStyle("-fx-font-size: 13px; -fx-text-fill: #3b82f6;");
        info.getChildren().addAll(name, price);
        Spinner<Integer> quantity = new Spinner<>(1, 99, item.getQuantity());
        quantity.setMaxWidth(58);
        quantity.setEditable(false);
        quantity.valueProperty().addListener((obs, oldVal, newVal) -> {
            item.setQuantity(newVal);
            updateTotal();
        });
        Button remove = new Button("🗑");
        remove.getStyleClass().add("button-danger");
        remove.setOnAction(e -> {
            Cart.getInstance().removeProduct(item.getProduct());
            loadCartItems();
            updateTotal();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        box.getChildren().addAll(image, info, spacer, quantity, remove);
        return box;
    }

    private void applyPromo() {
        String code = promoCodeField.getText().trim();
        if (code.isEmpty()) {
            showAlert("Ошибка", "Введите код промокода");
            return;
        }
        if (code.equalsIgnoreCase("SALE10")) {
            discountPercent = 10;
            showAlert("Успех", "Промокод применен! Скидка 10%");
        } else if (code.equalsIgnoreCase("SALE20")) {
            discountPercent = 20;
            showAlert("Успех", "Промокод применен! Скидка 20%");
        } else {
            showAlert("Ошибка", "Неверный промокод");
            discountPercent = 0;
        }
        updateTotal();
    }

    private void updateTotal() {
        double total = Cart.getInstance().getTotal();
        double discount = total * (discountPercent / 100.0);
        double finalTotal = total - discount;
        if (discountLabel != null) discountLabel.setText(String.format("Скидка: %.2f ₽", discount));
        if (totalLabel != null) totalLabel.setText(String.format("Итого: %,.2f ₽", finalTotal));
    }

    private void checkout() {
        if (Cart.getInstance().getItems().isEmpty()) {
            showAlert("Ошибка", "Корзина пуста");
            return;
        }
        showAlert("Успех", "Заказ оформлен! Товаров: " + Cart.getInstance().getTotalQuantity());
        Cart.getInstance().clear();
        loadCartItems();
        updateTotal();
    }

    private void goBack() {
        if (mainController != null) mainController.showMainContent();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
