package controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import com.example.authapp.models.Product;

public class ProductCardController {
    @FXML
    private ImageView productImage;
    @FXML
    private Label productName, productDescription, productPrice;
    @FXML
    private Button addToCartBtn;
    @FXML
    private Label productManufacturer;

    public void setProduct(Product product) {
        productName.setText(product.getName());
        productDescription.setText(product.getDescription());
        productPrice.setText(product.getPrice() + " ₽");

        String imageUrl = product.getImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            try {
                productImage.setImage(new Image(imageUrl, true));
            } catch (Exception ex) {
                productImage.setImage(new Image("default-image.png")); // путь к локальной заглушке
            }
        } else {
            productImage.setImage(new Image("default-image.png")); // путь к локальной заглушке
        }

        if (product.getManufacturer() != null) {
            productManufacturer.setText(product.getManufacturer());
        }

        addToCartBtn.setOnAction(e -> {
            // Реализуй добавление в корзину
            System.out.println("В корзину: " + product.getName());
        });
    }
}
