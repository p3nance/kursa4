package controllers;

import config.SessionManager;
import controllers.CabinetController;
import controllers.CartController;
import com.example.authapp.models.Product;
import com.example.authapp.repositories.ProductRepository;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;

import java.net.URL;
import java.util.*;

public class MainController implements Initializable {
    @FXML private VBox categoryItemsPane;
    @FXML private FlowPane productPane;
    @FXML private TextField searchField;
    @FXML private Button profileBtn, cartBtn;
    @FXML private HBox headerPane;
    @FXML private VBox categoryPane;
    @FXML private ScrollPane contentScroll;
    @FXML private BorderPane mainPane;

    private Node lastCenter;
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> popularProducts = new ArrayList<>();
    private CartController cartController;
    private String selectedCategory = "Все";

    private List<String> categories = Arrays.asList(
            "Все", "Популярное", "Процессоры", "Видеокарты", "Материнские платы",
            "Оперативная память", "Жёсткие диски", "SSD-накопители", "Блоки питания",
            "Корпуса", "Охлаждение", "Мониторы", "Клавиатуры", "Мыши", "Аксессуары"
    );

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadCategories();
        setupSearch();
        setupProfileButton();
        setupCartButton();
        loadProductsFromSupabase();
        lastCenter = contentScroll;
    }

    private void loadCategories() {
        categoryItemsPane.getChildren().clear();
        categoryItemsPane.setAlignment(Pos.CENTER);
        for (String cat : categories) {
            Button btn = new Button(cat);
            btn.setMaxWidth(Region.USE_COMPUTED_SIZE);
            btn.setMinHeight(36);
            btn.setStyle(getCategoryButtonStyle(cat.equals(selectedCategory)));
            btn.setOnAction(e -> {
                selectedCategory = cat;
                loadCategories(); // Сбросить стили
                filterByCategory(selectedCategory);
            });
            categoryItemsPane.getChildren().add(btn);
        }
    }

    private String getCategoryButtonStyle(boolean selected) {
        if (selected) {
            return "-fx-background-color: #3b82f6;" +
                    "-fx-text-fill: #fff;" +
                    "-fx-font-size: 13px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-radius: 9;" +
                    "-fx-border-radius: 9;" +
                    "-fx-border-width: 2;" +
                    "-fx-border-color: #2563eb;" +
                    "-fx-padding: 6 24;" +
                    "-fx-cursor: hand;" +
                    "-fx-effect: dropshadow(gaussian, #2563eb22, 2,0,0,2);";
        } else {
            return "-fx-background-color: transparent;" +
                    "-fx-text-fill: #1f2937;" +
                    "-fx-font-size: 13px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-radius: 9;" +
                    "-fx-padding: 6 24;" +
                    "-fx-cursor: hand;";
        }
    }

    private void loadProductsFromSupabase() {
        try {
            allProducts = ProductRepository.loadProductsFromSupabase();
            popularProducts = allProducts.stream()
                    .filter(p -> p.getCategory() != null &&
                            (p.getCategory().equalsIgnoreCase("Видеокарты") ||
                                    p.getCategory().equalsIgnoreCase("Процессоры")))
                    .toList();
            showProducts(allProducts);
        } catch (Exception e) {
            e.printStackTrace();
            showProducts(Collections.emptyList());
        }
    }

    @FXML
    private void onSearch() {
        String text = searchField.getText().toLowerCase().trim();
        if (text.isEmpty()) {
            filterByCategory(selectedCategory);
        } else {
            List<Product> filtered = allProducts.stream()
                    .filter(p -> p.getName() != null && p.getName().toLowerCase().contains(text))
                    .toList();
            showProducts(filtered);
        }
    }

    private void showProducts(List<Product> products) {
        productPane.getChildren().clear();
        if (products.isEmpty()) {
            Label emptyLabel = new Label("😔 Товары не найдены");
            emptyLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #888;");
            productPane.getChildren().add(emptyLabel);
            return;
        }
        for (Product product : products) {
            VBox card = createProductCard(product);
            productPane.getChildren().add(card);
        }
        lastCenter = contentScroll;
    }

    private VBox createProductCard(Product product) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(200);
        card.setPadding(new Insets(12));

        ImageView imageView = new ImageView();
        imageView.setFitWidth(180); imageView.setFitHeight(120); imageView.setPreserveRatio(true);
        String img = product.getImageUrl() != null ? product.getImageUrl() : "";
        boolean isValidImageUrl = !img.isBlank() && (img.startsWith("http://") || img.startsWith("https://") || img.startsWith("file:/"));
        imageView.setImage(isValidImageUrl ? new Image(img, true) : new Image("default-image.png"));

        Label name = new Label(product.getName() == null ? "Без названия" : product.getName());
        name.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1f2937;"); name.setWrapText(true);
        Label manufacturer = new Label(product.getManufacturer() == null ? "" : product.getManufacturer());
        manufacturer.setStyle("-fx-font-size: 10px; -fx-text-fill: #9ca3af;");
        Label price = new Label(String.format("%,.0f ₽", product.getPrice()));
        price.setStyle("-fx-text-fill: #10b981; -fx-font-size: 14px; -fx-font-weight: bold;");

        Button btn = new Button("Подробнее →");
        btn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 6px 12px;");
        btn.setPrefWidth(180);
        btn.setOnAction(event -> showProductDetail(product));

        Button addToCartBtn = new Button("🛒 В корзину");
        addToCartBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 8px 16px;");
        addToCartBtn.setPrefWidth(180);
        addToCartBtn.setOnAction(e -> {
            if (cartController != null) {
                cartController.addToCart(product);
            } else {
                CartController.cart.addProduct(product);
            }
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Корзина"); alert.setHeaderText(null);
            alert.setContentText(product.getName() + " добавлен в корзину!");
            alert.showAndWait();
        });

        card.getChildren().addAll(imageView, name, manufacturer, price, btn, addToCartBtn);
        return card;
    }

    private void showProductDetail(Product product) {
        productPane.getChildren().clear();
        VBox detail = new VBox(15);
        detail.setPadding(new Insets(25));
        detail.setStyle("-fx-background-color: white;");

        Button backBtn = new Button("← Назад к товарам");
        backBtn.setStyle("-fx-background-color: #6b7280; -fx-text-fill: white; -fx-padding: 10px 20px; -fx-font-size: 12px; -fx-cursor: hand; -fx-background-radius: 6;");
        backBtn.setOnAction(e -> filterByCategory(selectedCategory));

        ImageView largeImage = new ImageView();
        largeImage.setFitWidth(400); largeImage.setFitHeight(300); largeImage.setPreserveRatio(true);
        String img = product.getImageUrl() != null ? product.getImageUrl() : "";
        boolean isValidImageUrl = !img.isBlank() && (img.startsWith("http://") || img.startsWith("https://") || img.startsWith("file:/"));
        largeImage.setImage(isValidImageUrl ? new Image(img, true) : new Image("default-image.png"));

        Label nameLabel = new Label(product.getName() == null ? "Без названия" : product.getName());
        nameLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #1f2937;");
        Label manufacturerLabel = new Label("🏭 Производитель: " +
                (product.getManufacturer() == null || product.getManufacturer().isEmpty() ? "Не указан" : product.getManufacturer()));
        manufacturerLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
        Label categoryLabel = new Label("📦 Категория: " +
                (product.getCategory() == null || product.getCategory().isEmpty() ? "Не указана" : product.getCategory()));
        categoryLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
        Label descriptionLabel = new Label(product.getDescription() == null || product.getDescription().isEmpty() ? "Описание отсутствует" : product.getDescription());
        descriptionLabel.setWrapText(true);
        descriptionLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");
        Label priceLabel = new Label(String.format("%,.0f ₽", product.getPrice()));
        priceLabel.setStyle("-fx-font-size: 26px; -fx-text-fill: #10b981; -fx-font-weight: bold;");

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

        Button addToCartBtn = new Button("🛒 Добавить в корзину");
        addToCartBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 12px 40px; -fx-font-size: 14px;");
        addToCartBtn.setOnAction(e -> {
            if (cartController != null) {
                cartController.addToCart(product);
            } else {
                CartController.cart.addProduct(product);
            }
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Успешно"); alert.setHeaderText(null);
            alert.setContentText(product.getName() + " добавлен в корзину!");
            alert.showAndWait();
        });

        Button buyNowBtn = new Button("💳 Купить сейчас");
        buyNowBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-padding: 12px 40px; -fx-font-size: 14px;");
        buyNowBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Оформление"); alert.setHeaderText(null);
            alert.setContentText("Переход к оформлению заказа для " + product.getName());
            alert.showAndWait();
        });

        buttonBox.getChildren().addAll(addToCartBtn, buyNowBtn);
        detail.getChildren().addAll(backBtn, new Separator(), largeImage, nameLabel, manufacturerLabel, categoryLabel, descriptionLabel, priceLabel, buttonBox);
        productPane.getChildren().add(detail);
    }

    private void filterByCategory(String category) {
        List<Product> filtered;
        if ("Все".equals(category)) {
            filtered = allProducts;
        } else if ("Популярное".equals(category)) {
            filtered = popularProducts;
        } else {
            filtered = allProducts.stream()
                    .filter(p -> p.getCategory() != null && p.getCategory().equalsIgnoreCase(category))
                    .toList();
        }
        showProducts(filtered);
    }

    private void setupSearch() {
        searchField.setOnKeyReleased(event -> onSearch());
    }

    private void setupProfileButton() {
        profileBtn.setOnAction(e -> {
            if (isAuthorized()) {
                openCabinetInMain();
            } else {
                showAuthForm();
            }
        });
    }

    private boolean isAuthorized() {
        return SessionManager.getAccessToken() != null && SessionManager.getUserEmail() != null;
    }

    private void showAuthForm() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/auth.fxml"));
            Node authNode = loader.load();
            mainPane.setCenter(authNode);
            AuthController.setMainController(this);
            headerPane.setVisible(false);
            categoryPane.setVisible(false);
            mainPane.setTop(null);
            mainPane.setLeft(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void openCabinetInMain() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/cabinet.fxml"));
            Node cabinetNode = loader.load();
            lastCenter = mainPane.getCenter();
            mainPane.setCenter(cabinetNode);
            headerPane.setVisible(false);
            categoryPane.setVisible(false);
            mainPane.setTop(null);
            mainPane.setLeft(null);
            CabinetController.setHostMainController(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showMainContent() {
        headerPane.setVisible(true);
        categoryPane.setVisible(true);
        mainPane.setTop(headerPane);
        mainPane.setLeft(categoryPane);
        mainPane.setCenter(lastCenter);
    }

    private void openCartView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/cart.fxml"));
            Node cartNode = loader.load();
            cartController = loader.getController();
            cartController.setMainController(this);
            mainPane.setCenter(cartNode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupCartButton() {
        cartBtn.setOnAction(e -> openCartView());
    }
}
