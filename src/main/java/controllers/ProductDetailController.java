package controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import com.example.authapp.models.Product;

public class ProductDetailController {
    @FXML
    private ImageView detailImage;
    @FXML
    private Label detailName, detailManufacturer, detailCategory, detailDescription, detailPrice;
    @FXML
    private Button addToCartBtn, closeBtn, closeWindowBtn;

    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
        closeWindowBtn.setOnAction(e -> stage.close());
        closeBtn.setOnAction(e -> stage.close());
    }

    public void setProduct(Product product) {
        detailName.setText(product.getName() == null ? "Без названия" : product.getName());
        detailManufacturer.setText(product.getManufacturer() == null ? "" : product.getManufacturer());
        detailCategory.setText(product.getCategory() == null ? "" : product.getCategory());
        detailDescription.setText(product.getDescription() == null ? "" : product.getDescription());
        detailPrice.setText(product.getPrice() + " ₽");

        String img = product.getImageUrl();
        boolean isValidImageUrl = false;
        if (img != null && !img.isBlank()) {
            try {
                if (img.toLowerCase().startsWith("http://") || img.toLowerCase().startsWith("https://") || img.toLowerCase().startsWith("file:/")) {
                    new java.net.URL(img);
                    isValidImageUrl = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (isValidImageUrl) {
            try {
                detailImage.setImage(new Image(img, true));
            } catch (Exception ex) {
                detailImage.setImage(new Image("default-image.png"));
            }
        } else {
            detailImage.setImage(new Image("default-image.png"));
        }

    }
}
