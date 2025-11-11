package controllers;

import com.example.authapp.models.Cart;
import com.example.authapp.models.Product;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class CartController {
    public static final Cart cart = new Cart();

    @FXML private VBox cartItemsBox;
    @FXML private Label totalLabel;
    @FXML private Button clearBtn, buyBtn, backBtn;

    // Reference на MainController для возврата к основной сцене
    private MainController mainController;

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void initialize() {
        refreshCartView();
        clearBtn.setOnAction(e -> {
            cart.clear();
            refreshCartView();
        });
        buyBtn.setOnAction(e -> handleBuy());
        backBtn.setOnAction(e -> handleBack()); // рабочий обработчик назад
    }

    private void refreshCartView() {
        if (cartItemsBox == null || totalLabel == null) return;
        cartItemsBox.getChildren().clear();
        for (Product p : cart.getItems()) {
            Label label = new Label(p.getName() + " — " + p.getPrice() + " ₽");
            label.getStyleClass().add("cart-item-label");
            cartItemsBox.getChildren().add(label);
        }
        totalLabel.setText("Итого: " + cart.getTotalPrice() + " ₽");
    }

    public void addToCart(Product product) {
        cart.addProduct(product);
        refreshCartView();
    }

    private void handleBuy() {
        new Alert(Alert.AlertType.INFORMATION, "Оплата реализуется тут!").showAndWait();
        cart.clear();
        refreshCartView();
    }

    private void handleBack() {
        if (mainController != null) {
            mainController.showMainContent();
        } else {
            // Фоллбэк: просто можно закрыть корзину (если вызывается с другого окна)
            // Например, очистить центр главного окна если есть доступ к нему, или ничего не делать
        }
    }
}
