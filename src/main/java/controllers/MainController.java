package controllers;

import config.SessionManager;
import controllers.CabinetController;
import com.example.authapp.models.Product;
import com.example.authapp.repositories.ProductRepository;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class MainController implements Initializable {
    @FXML
    private ListView<String> categoryList;
    @FXML
    private FlowPane productPane;
    @FXML
    private TextField searchField;
    @FXML
    private Button profileBtn;

    private List<Product> allProducts = new ArrayList<>();
    private List<Product> popularProducts = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadCategories();
        setupSearch();
        setupCategoryListCellFactory();
        setupProfileButton();
        loadProductsFromSupabase();
        lastCenter = contentScroll;
    }

    private void loadCategories() {
        categoryList.getItems().setAll(
                "Все",
                "Популярное",
                "Процессоры",
                "Видеокарты",
                "Материнские платы",
                "Оперативная память",
                "Жёсткие диски",
                "SSD-накопители",
                "Блоки питания",
                "Корпуса",
                "Охлаждение",
                "Мониторы",
                "Клавиатуры",
                "Мыши",
                "Аксессуары"
        );
        categoryList.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> filterByCategory(newVal)
        );
        categoryList.getSelectionModel().selectFirst();
    }

    private void setupCategoryListCellFactory() {
        categoryList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setPadding(new Insets(10, 12, 10, 12));
                    setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 13));
                    setTextFill(isSelected() ? javafx.scene.paint.Color.WHITE : javafx.scene.paint.Color.web("#374151"));
                    setStyle(isSelected()
                            ? "-fx-background-color: #3b82f6; -fx-background-radius: 6;"
                            : "-fx-background-color: transparent;");
                }
            }
        });
        categoryList.getSelectionModel().selectedIndexProperty().addListener((obs, oldSel, newSel) -> categoryList.refresh());
    }

    private void loadProductsFromSupabase() {
        try {
            allProducts = ProductRepository.loadProductsFromSupabase();
            System.out.println("Загружено товаров: " + allProducts.size());
            popularProducts = allProducts.stream()
                    .filter(p -> p.getCategory() != null &&
                            (p.getCategory().equalsIgnoreCase("Видеокарты") ||
                                    p.getCategory().equalsIgnoreCase("Процессоры")))
                    .collect(Collectors.toList());
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
            String selected = categoryList.getSelectionModel().getSelectedItem();
            filterByCategory(selected != null ? selected : "Все");
        } else {
            List<Product> filtered = allProducts.stream()
                    .filter(p -> p.getName() != null && p.getName().toLowerCase().contains(text))
                    .collect(Collectors.toList());
            showProducts(filtered);
        }
    }

    private void showProducts(List<Product> products) {
        productPane.getChildren().clear();
        productPane.setStyle("-fx-background-color: white;");
        System.out.println("Отображаем товаров: " + products.size());

        if (products.isEmpty()) {
            Label emptyLabel = new Label("😔 Товары не найдены");
            emptyLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #888;");
            productPane.getChildren().add(emptyLabel);
            return;
        }

        for (Product product : products) {
            VBox card = createProductCard(product);
            productPane.getChildren().add(card);
            System.out.println("Карточка: " + product.getName());
        }
        lastCenter = contentScroll;
    }

    private VBox createProductCard(Product product) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(200);
        card.setStyle("-fx-background-color: white; -fx-border-radius: 8; -fx-border-width: 1; -fx-border-color: #e5e7eb;"
                + "-fx-effect: dropshadow(gaussian, #0000001a, 4, 0, 0, 2); -fx-cursor: hand;");
        card.setPadding(new Insets(12));

        ImageView imageView = new ImageView();
        imageView.setFitWidth(180);
        imageView.setFitHeight(120);
        imageView.setPreserveRatio(true);

        String img = product.getImageUrl();
        boolean isValidImageUrl = false;
        if (img != null && !img.isBlank()) {
            try {
                if (img.toLowerCase().startsWith("http://") || img.toLowerCase().startsWith("https://") || img.toLowerCase().startsWith("file:/")) {
                    new java.net.URL(img);
                    isValidImageUrl = true;
                }
            } catch (Exception ignored) {}
        }
        if (isValidImageUrl) {
            try {
                imageView.setImage(new Image(img, true));
            } catch (Exception ex) {
                imageView.setImage(new Image("default-image.png"));
            }
        } else {
            imageView.setImage(new Image("default-image.png"));
        }

        Label name = new Label(product.getName() == null ? "Без названия" : product.getName());
        name.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1f2937;");
        name.setWrapText(true);

        Label manufacturer = new Label(product.getManufacturer() == null ? "" : product.getManufacturer());
        manufacturer.setStyle("-fx-font-size: 10px; -fx-text-fill: #9ca3af;");

        Label price = new Label(String.format("%,.0f ₽", product.getPrice()));
        price.setStyle("-fx-text-fill: #10b981; -fx-font-size: 14px; -fx-font-weight: bold;");

        Button btn = new Button("Подробнее →");
        btn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 6px 12px; -fx-font-size: 11px; -fx-cursor: hand;");
        btn.setPrefWidth(180);
        btn.setOnAction(event -> showProductDetail(product));

        card.getChildren().addAll(imageView, name, manufacturer, price, btn);
        return card;
    }

    private void showProductDetail(Product product) {
        productPane.getChildren().clear();
        productPane.setStyle("-fx-background-color: white;");

        VBox detail = new VBox(15);
        detail.setPadding(new Insets(25));
        detail.setStyle("-fx-background-color: white;");

        Button backBtn = new Button("← Назад к товарам");
        backBtn.setStyle("-fx-background-color: #6b7280; -fx-text-fill: white; -fx-padding: 10px 20px; -fx-font-size: 12px; -fx-cursor: hand; -fx-background-radius: 6;");
        backBtn.setOnAction(e -> {
            String selected = categoryList.getSelectionModel().getSelectedItem();
            filterByCategory(selected != null ? selected : "Все");
        });

        ImageView largeImage = new ImageView();
        largeImage.setFitWidth(400);
        largeImage.setFitHeight(300);
        largeImage.setPreserveRatio(true);

        String img = product.getImageUrl();
        boolean isValidImageUrl = false;
        if (img != null && !img.isBlank()) {
            try {
                if (img.toLowerCase().startsWith("http://") || img.toLowerCase().startsWith("https://") || img.toLowerCase().startsWith("file:/")) {
                    new java.net.URL(img);
                    isValidImageUrl = true;
                }
            } catch (Exception ignored) {}
        }
        if (isValidImageUrl) {
            try {
                largeImage.setImage(new Image(img));
            } catch (Exception ex) {
                largeImage.setImage(new Image("default-image.png"));
            }
        } else {
            largeImage.setImage(new Image("default-image.png"));
        }

        Label nameLabel = new Label(product.getName() == null ? "Без названия" : product.getName());
        nameLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #1f2937;");

        Label manufacturerLabel = new Label("🏭 Производитель: " +
                (product.getManufacturer() == null || product.getManufacturer().isEmpty() ? "Не указан" : product.getManufacturer()));
        manufacturerLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");

        Label categoryLabel = new Label("📦 Категория: " +
                (product.getCategory() == null || product.getCategory().isEmpty() ? "Не указана" : product.getCategory()));
        categoryLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");

        Label descriptionLabel = new Label(
                product.getDescription() == null || product.getDescription().isEmpty() ? "Описание отсутствует" : product.getDescription());
        descriptionLabel.setWrapText(true);
        descriptionLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        Label priceLabel = new Label(String.format("%,.0f ₽", product.getPrice()));
        priceLabel.setStyle("-fx-font-size: 26px; -fx-text-fill: #10b981; -fx-font-weight: bold;");

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

        Button addToCartBtn = new Button("🛒 Добавить в корзину");
        addToCartBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 12px 40px; -fx-font-size: 14px; -fx-cursor: hand; -fx-background-radius: 6;");
        addToCartBtn.setOnAction(e -> {
            System.out.println("✓ Добавлено в корзину: " + product.getName());
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Успешно");
            alert.setHeaderText(null);
            alert.setContentText(product.getName() + " добавлен в корзину!");
            alert.showAndWait();
        });

        Button buyNowBtn = new Button("💳 Купить сейчас");
        buyNowBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-padding: 12px 40px; -fx-font-size: 14px; -fx-cursor: hand; -fx-background-radius: 6;");
        buyNowBtn.setOnAction(e -> {
            System.out.println("✓ Оформление покупки: " + product.getName());
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Оформление");
            alert.setHeaderText(null);
            alert.setContentText("Переход к оформлению заказа для " + product.getName());
            alert.showAndWait();
        });

        buttonBox.getChildren().addAll(addToCartBtn, buyNowBtn);

        detail.getChildren().addAll(
                backBtn,
                new Separator(),
                largeImage,
                nameLabel,
                manufacturerLabel,
                categoryLabel,
                descriptionLabel,
                priceLabel,
                buttonBox
        );

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
                    .collect(Collectors.toList());
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

    @FXML private HBox headerPane;
    @FXML private VBox categoryPane;
    @FXML private ScrollPane contentScroll;
    @FXML private BorderPane mainPane;
    private Node lastCenter;


    private boolean isAuthorized() {
        return SessionManager.getAccessToken() != null && SessionManager.getUserEmail() != null;
    }

    private void showAuthForm() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/auth.fxml"));
            Node authNode = loader.load();
            mainPane.setCenter(authNode);
            AuthController.setMainController(this); // Связь для обратного вызова после входа
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



}
