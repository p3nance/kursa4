package controllers;

import com.example.authapp.models.Cart;
import config.SessionManager;
import com.example.authapp.services.CartService;
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
    @FXML private TilePane productPane;
    @FXML private TextField searchField;
    @FXML private Button profileBtn, cartBtn, configuratorBtn;
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

    // ─── Стили карточки ──────────────────────────────────────────────────────
    private static final String CARD_STYLE =
            "-fx-background-color: #ffffff;" +
                    "-fx-border-color: #e5e7eb;" +
                    "-fx-border-width: 1;" +
                    "-fx-border-radius: 14;" +
                    "-fx-background-radius: 14;" +
                    "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.10), 8, 0, 0, 2);";

    private static final String CARD_HOVER_STYLE =
            "-fx-background-color: #ffffff;" +
                    "-fx-border-color: #bfdbfe;" +
                    "-fx-border-width: 1;" +
                    "-fx-border-radius: 14;" +
                    "-fx-background-radius: 14;" +
                    "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.25), 14, 0, 0, 5);";

    private static final String BTN_PRIMARY =
            "-fx-background-color: #3b82f6;" +
                    "-fx-text-fill: #ffffff;" +
                    "-fx-font-size: 11px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-radius: 8;" +
                    "-fx-border-width: 0;" +
                    "-fx-padding: 7 14;" +
                    "-fx-cursor: hand;";

    private static final String BTN_PRIMARY_HOVER =
            "-fx-background-color: #2563eb;" +
                    "-fx-text-fill: #ffffff;" +
                    "-fx-font-size: 11px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-radius: 8;" +
                    "-fx-border-width: 0;" +
                    "-fx-padding: 7 14;" +
                    "-fx-cursor: hand;";

    private static final String BTN_CART =
            "-fx-background-color: #1d4ed8;" +
                    "-fx-text-fill: #ffffff;" +
                    "-fx-font-size: 12px;" +
                    "-fx-background-radius: 8;" +
                    "-fx-border-width: 0;" +
                    "-fx-padding: 7 10;" +
                    "-fx-cursor: hand;";

    private static final String BTN_CART_HOVER =
            "-fx-background-color: #1e40af;" +
                    "-fx-text-fill: #ffffff;" +
                    "-fx-font-size: 12px;" +
                    "-fx-background-radius: 8;" +
                    "-fx-border-width: 0;" +
                    "-fx-padding: 7 10;" +
                    "-fx-cursor: hand;";
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadCategories();
        setupSearch();
        setupProfileButton();
        setupCartButton();
        setupConfiguratorButton();

        Thread loadThread = new Thread(() -> {
            try {
                loadProductsFromSupabase();
            } catch (Exception e) {
                System.err.println("❌ Ошибка загрузки товаров: " + e.getMessage());
                e.printStackTrace();
            }
        });
        loadThread.setDaemon(true);
        loadThread.start();

        lastCenter = contentScroll;

        contentScroll.widthProperty().addListener((obs, oldVal, newVal) ->
                updateCardSize(newVal.doubleValue()));
    }

    // -------- Адаптивная сетка --------

    private void updateCardSize(double scrollWidth) {
        double padding = 48;
        double hgap = 16;
        double available = scrollWidth - padding - 20;

        int columns;
        if (available < 400) {
            columns = 1;
        } else if (available < 620) {
            columns = 2;
        } else if (available < 900) {
            columns = 3;
        } else {
            columns = 4;
        }

        double cardWidth = (available - hgap * (columns - 1)) / columns;
        cardWidth = Math.max(180, cardWidth);

        productPane.setPrefColumns(columns);
        productPane.setPrefTileWidth(cardWidth);
    }

    // -------- Категории --------

    private void loadCategories() {
        categoryItemsPane.getChildren().clear();
        categoryItemsPane.setAlignment(Pos.CENTER_LEFT);
        for (String cat : categories) {
            Button btn = new Button(cat);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setMinHeight(34);
            btn.setStyle(getCategoryButtonStyle(cat.equals(selectedCategory)));
            btn.setOnAction(e -> {
                selectedCategory = cat;
                loadCategories();
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
                    "-fx-padding: 6 20;" +
                    "-fx-cursor: hand;" +
                    "-fx-alignment: center;" +  // <-- Добавлено центрирование текста
                    "-fx-text-alignment: center;" +
                    "-fx-effect: dropshadow(gaussian, #2563eb22, 2,0,0,2);";
        } else {
            return "-fx-background-color: transparent;" +
                    "-fx-text-fill: #1f2937;" +
                    "-fx-font-size: 13px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-background-radius: 9;" +
                    "-fx-padding: 6 20;" +
                    "-fx-cursor: hand;" +
                    "-fx-alignment: center;" +  // <-- Добавлено центрирование текста
                    "-fx-text-alignment: center;";
        }
    }

    // -------- Загрузка товаров --------

    private void loadProductsFromSupabase() {
        try {
            allProducts = ProductRepository.loadProductsFromSupabase();

            if (allProducts.isEmpty()) {
                addTestProducts();
            }

            popularProducts = allProducts.stream()
                    .filter(p -> p.getCategory() != null &&
                            (p.getCategory().equalsIgnoreCase("Видеокарты") ||
                                    p.getCategory().equalsIgnoreCase("Процессоры")))
                    .toList();

            javafx.application.Platform.runLater(() -> showProducts(allProducts));

        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки товаров: " + e.getMessage());
            e.printStackTrace();
            javafx.application.Platform.runLater(() -> {
                showProducts(Collections.emptyList());
                showErrorMessage("Ошибка загрузки товаров", e.getMessage());
            });
        }
    }

    private void addTestProducts() {
        allProducts.add(new Product(1, "RTX 4090", "Мощная видеокарта. gpu_power:450", 180000, 5, "", "Видеокарты", "NVIDIA"));
        allProducts.add(new Product(2, "Intel i9", "Процессор. socket:LGA1700 tdp:125", 95000, 10, "", "Процессоры", "Intel"));
        allProducts.add(new Product(3, "DDR5 32GB", "Оперативная память. memory:DDR5", 15000, 20, "", "Оперативная память", "Kingston"));
    }

    public void reloadProducts() {
        Thread loadThread = new Thread(() -> {
            try {
                List<Product> freshProducts = ProductRepository.loadProductsFromSupabase();
                allProducts.clear();
                allProducts.addAll(freshProducts);
                popularProducts = allProducts.stream()
                        .filter(p -> p.getCategory() != null &&
                                (p.getCategory().equalsIgnoreCase("Видеокарты") ||
                                        p.getCategory().equalsIgnoreCase("Процессоры")))
                        .toList();
                javafx.application.Platform.runLater(() -> filterByCategory(selectedCategory));
            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() ->
                        showErrorMessage("Ошибка", "Не удалось перезагрузить товары: " + e.getMessage()));
            }
        });
        loadThread.setDaemon(true);
        loadThread.start();
    }

    // -------- Отображение товаров --------

    private void showErrorMessage(String title, String message) {
        Label errorLabel = new Label("❌ " + title + "\n" + message);
        errorLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #ef4444; -fx-wrap-text: true;");
        productPane.getChildren().clear();
        productPane.getChildren().add(errorLabel);
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
        productPane.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 24;");

        if (products.isEmpty()) {
            Label emptyLabel = new Label("😔 Товары не найдены");
            emptyLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #888;");
            productPane.getChildren().add(emptyLabel);
            return;
        }

        for (Product product : products) {
            try {
                productPane.getChildren().add(createProductCard(product));
            } catch (Exception e) {
                System.err.println("❌ Ошибка создания карточки: " + e.getMessage());
            }
        }

        javafx.application.Platform.runLater(() ->
                updateCardSize(contentScroll.getWidth()));

        lastCenter = contentScroll;
    }

    private VBox createProductCard(Product product) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinWidth(180);
        card.setMinHeight(270);
        card.setPadding(new Insets(14));
        card.setStyle(CARD_STYLE);

        card.setOnMouseEntered(e -> card.setStyle(CARD_HOVER_STYLE));
        card.setOnMouseExited(e -> card.setStyle(CARD_STYLE));

        // Изображение
        ImageView imageView = new ImageView();
        imageView.setFitWidth(180);
        imageView.setFitHeight(110);
        imageView.setPreserveRatio(true);
        loadImage(imageView, product.getImageUrl(), 180, 110);

        // Плашка категории
        Label categoryBadge = new Label(product.getCategory() == null ? "" : product.getCategory());
        categoryBadge.setStyle(
                "-fx-background-color: #eff6ff;" +
                        "-fx-text-fill: #3b82f6;" +
                        "-fx-font-size: 10px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 2 8;" +
                        "-fx-background-radius: 20;"
        );

        // Название
        Label name = new Label(product.getName() == null ? "Без названия" : product.getName());
        name.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        name.setWrapText(true);
        name.setPrefHeight(40);
        name.setMinHeight(40);
        VBox.setVgrow(name, Priority.NEVER);

        // Производитель
        Label manufacturer = new Label(
                product.getManufacturer() == null || product.getManufacturer().isEmpty()
                        ? "" : "🏭 " + product.getManufacturer());
        manufacturer.setStyle("-fx-font-size: 11px; -fx-text-fill: #9ca3af;");

        // Цена
        Label price = new Label(String.format("%,.0f ₽", product.getPrice()));
        price.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #10b981;");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Кнопки
        HBox buttonBox = new HBox(6);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setMinHeight(36);

        Button detailsBtn = new Button("Подробнее");
        detailsBtn.setStyle(BTN_PRIMARY);
        detailsBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(detailsBtn, Priority.ALWAYS);
        detailsBtn.setOnMouseEntered(e -> detailsBtn.setStyle(BTN_PRIMARY_HOVER));
        detailsBtn.setOnMouseExited(e -> detailsBtn.setStyle(BTN_PRIMARY));
        detailsBtn.setOnAction(e -> showProductDetail(product));

        Button addToCartBtn = new Button("🛒");
        addToCartBtn.setStyle(BTN_CART);
        addToCartBtn.setOnMouseEntered(e -> addToCartBtn.setStyle(BTN_CART_HOVER));
        addToCartBtn.setOnMouseExited(e -> addToCartBtn.setStyle(BTN_CART));
        addToCartBtn.setOnAction(e -> addProductToCart(product, 1));

        buttonBox.getChildren().addAll(detailsBtn, addToCartBtn);
        card.getChildren().addAll(imageView, categoryBadge, name, manufacturer, price, spacer, buttonBox);
        return card;
    }

    private void showProductDetail(Product product) {
        // Создаем контейнер специально для детальной страницы (не TilePane)
        VBox detailPageContainer = new VBox(16);
        detailPageContainer.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 24;");
        detailPageContainer.setMaxWidth(Double.MAX_VALUE);

        // Кнопка назад
        Button backBtn = new Button("← Назад к товарам");
        String backStyle =
                "-fx-background-color: #6b7280; -fx-text-fill: white; -fx-padding: 9 18;" +
                        "-fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;" +
                        "-fx-background-radius: 8; -fx-border-width: 0;";
        String backHover =
                "-fx-background-color: #4b5563; -fx-text-fill: white; -fx-padding: 9 18;" +
                        "-fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;" +
                        "-fx-background-radius: 8; -fx-border-width: 0;";
        backBtn.setStyle(backStyle);
        backBtn.setOnMouseEntered(e -> backBtn.setStyle(backHover));
        backBtn.setOnMouseExited(e -> backBtn.setStyle(backStyle));
        backBtn.setOnAction(e -> {
            // Возвращаем TilePane с товарами обратно в ScrollPane
            contentScroll.setContent(productPane);
            filterByCategory(selectedCategory);
        });

        // Изображение
        ImageView largeImage = new ImageView();
        largeImage.setPreserveRatio(true);
        loadImage(largeImage, product.getImageUrl(), 340, 260);

        Label catBadge = new Label("📦 " + (product.getCategory() == null ? "" : product.getCategory()));
        catBadge.setStyle(
                "-fx-background-color: #eff6ff;" +
                        "-fx-text-fill: #3b82f6;" +
                        "-fx-font-size: 12px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 4 14;" +
                        "-fx-background-radius: 20;"
        );

        VBox imageBox = new VBox(12);
        imageBox.setAlignment(Pos.TOP_CENTER);
        imageBox.getChildren().addAll(largeImage, catBadge);

        // Информация
        Label nameLabel = new Label(product.getName() == null ? "Без названия" : product.getName());
        nameLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        nameLabel.setWrapText(true);

        Label manufacturerLabel = new Label("🏭 Производитель: " +
                (product.getManufacturer() == null || product.getManufacturer().isEmpty()
                        ? "Не указан" : product.getManufacturer()));
        manufacturerLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #6b7280;");

        Label priceLabel = new Label(String.format("%,.0f ₽", product.getPrice()));
        priceLabel.setStyle("-fx-font-size: 28px; -fx-text-fill: #10b981; -fx-font-weight: bold;");

        Label descTitle = new Label("Описание:");
        descTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1f2937;");

        String rawDesc = product.getDescription() == null ? "" : product.getDescription();
        String cleanDesc = rawDesc
                .replaceAll("socket:\\S+", "")
                .replaceAll("memory:\\S+", "")
                .replaceAll("tdp:\\S+", "")
                .replaceAll("gpu_power:\\S+", "")
                .replaceAll("psu_power:\\S+", "")
                .trim();

        Label descriptionLabel = new Label(cleanDesc.isEmpty() ? "Описание отсутствует" : cleanDesc);
        descriptionLabel.setWrapText(true);
        descriptionLabel.setMaxWidth(Double.MAX_VALUE);
        descriptionLabel.setStyle(
                "-fx-font-size: 13px; -fx-text-fill: #374151;" +
                        "-fx-background-color: #f9fafb;" +
                        "-fx-border-color: #e5e7eb; -fx-border-width: 1;" +
                        "-fx-border-radius: 8; -fx-background-radius: 8;" +
                        "-fx-padding: 12;"
        );

        // Стили кнопок действий
        String btnAddStyle =
                "-fx-background-color: #3b82f6; -fx-text-fill: #ffffff;" +
                        "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 12 28;" +
                        "-fx-background-radius: 10; -fx-border-width: 0; -fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(59,130,246,0.4), 8, 0, 0, 2);";
        String btnAddHover =
                "-fx-background-color: #2563eb; -fx-text-fill: #ffffff;" +
                        "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 12 28;" +
                        "-fx-background-radius: 10; -fx-border-width: 0; -fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.5), 10, 0, 0, 3);";
        String btnBuyStyle =
                "-fx-background-color: #10b981; -fx-text-fill: #ffffff;" +
                        "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 12 28;" +
                        "-fx-background-radius: 10; -fx-border-width: 0; -fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(16,185,129,0.4), 8, 0, 0, 2);";
        String btnBuyHover =
                "-fx-background-color: #059669; -fx-text-fill: #ffffff;" +
                        "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 12 28;" +
                        "-fx-background-radius: 10; -fx-border-width: 0; -fx-cursor: hand;" +
                        "-fx-effect: dropshadow(gaussian, rgba(5,150,105,0.5), 10, 0, 0, 3);";

        Button addToCartBtn = new Button("🛒 В корзину");
        addToCartBtn.setStyle(btnAddStyle);
        addToCartBtn.setOnMouseEntered(e -> addToCartBtn.setStyle(btnAddHover));
        addToCartBtn.setOnMouseExited(e -> addToCartBtn.setStyle(btnAddStyle));
        addToCartBtn.setOnAction(e -> addProductToCart(product, 1));

        Button buyNowBtn = new Button("💳 Купить сейчас");
        buyNowBtn.setStyle(btnBuyStyle);
        buyNowBtn.setOnMouseEntered(e -> buyNowBtn.setStyle(btnBuyHover));
        buyNowBtn.setOnMouseExited(e -> buyNowBtn.setStyle(btnBuyStyle));
        buyNowBtn.setOnAction(e -> addProductToCart(product, 1));

        HBox actionBox = new HBox(12);
        actionBox.setAlignment(Pos.CENTER_LEFT);
        actionBox.getChildren().addAll(addToCartBtn, buyNowBtn);

        VBox infoBox = new VBox(14);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        VBox.setVgrow(infoBox, Priority.ALWAYS);
        infoBox.getChildren().addAll(
                nameLabel, manufacturerLabel, priceLabel,
                new Separator(), descTitle, descriptionLabel,
                new Separator(), actionBox
        );

        // Адаптивная карточка (располагаем элементы в зависимости от ширины)
        String detailCardStyle =
                "-fx-background-color: #ffffff;" +
                        "-fx-border-color: #e5e7eb;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 16;" +
                        "-fx-background-radius: 16;" +
                        "-fx-padding: 24;" +
                        "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 8, 0, 0, 2);";

        // Лямбда для обновления раскладки (переключение HBox <-> VBox)
        javafx.beans.value.ChangeListener<Number> widthListener = (obs, oldVal, newVal) -> {
            double w = newVal.doubleValue();
            detailPageContainer.getChildren().removeIf(node -> node != backBtn);

            Pane detailCard;
            if (w > 750) {
                // Широкий экран: картинка слева, текст справа
                largeImage.setFitWidth(320);
                largeImage.setFitHeight(240);
                imageBox.setMinWidth(340);
                imageBox.setMaxWidth(340);
                HBox hCard = new HBox(28);
                hCard.getChildren().addAll(imageBox, infoBox);
                detailCard = hCard;
            } else {
                // Узкий экран: картинка сверху, текст снизу
                largeImage.setFitWidth(Math.min(w - 96, 400));
                largeImage.setFitHeight(220);
                imageBox.setMaxWidth(Double.MAX_VALUE);
                VBox vCard = new VBox(20);
                vCard.getChildren().addAll(imageBox, infoBox);
                detailCard = vCard;
            }

            detailCard.setStyle(detailCardStyle);
            detailCard.setMaxWidth(Double.MAX_VALUE);
            detailPageContainer.getChildren().add(detailCard);
        };

        // Задаем начальную раскладку
        widthListener.changed(null, null, contentScroll.getWidth());

        // Подписываемся на изменение ширины окна
        contentScroll.widthProperty().addListener(widthListener);

        detailPageContainer.getChildren().add(0, backBtn);

        // ВМЕСТО того чтобы класть карточку в TilePane, мы меняем всё содержимое скролла
        contentScroll.setContent(detailPageContainer);
    }


    // -------- Вспомогательные --------

    private void loadImage(ImageView iv, String url, double w, double h) {
        boolean valid = url != null && !url.isBlank() &&
                (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:/"));
        try {
            if (valid) {
                iv.setImage(new Image(url, true));
            } else {
                iv.setImage(new Image("file:src/main/resources/images/default-image.png"));
            }
        } catch (Exception e) {
            iv.setStyle(String.format(
                    "-fx-background-color: #f3f4f6; -fx-min-width: %.0f; -fx-min-height: %.0f;", w, h));
        }
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

    // -------- Навигация --------

    private void setupSearch() {
        searchField.setOnKeyReleased(event -> onSearch());
    }

    private void setupProfileButton() {
        profileBtn.setOnAction(e -> {
            if (isAuthorized()) openCabinetInMain();
            else showAuthForm();
        });
    }

    private void setupCartButton() {
        cartBtn.setOnAction(e -> openCartView());
    }

    private void setupConfiguratorButton() {
        if (configuratorBtn != null) {
            configuratorBtn.setOnAction(e -> openConfigurator());
        }
    }

    private boolean isAuthorized() {
        return SessionManager.getAccessToken() != null && SessionManager.getUserEmail() != null;
    }

    private void addProductToCart(Product product, int quantity) {
        try {
            CartService cartService = new CartService();
            cartService.addProductToCart(product, quantity);
            Cart cart = Cart.getInstance();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Корзина");
            alert.setHeaderText(null);
            alert.setContentText(product.getName() + " добавлен в корзину!\n" +
                    "Товаров: " + cart.getTotalQuantity() + "\n" +
                    "Итого: " + String.format("%.2f ₽", cart.getTotal()));
            alert.showAndWait();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText(null);
            alert.setContentText("Не удалось добавить товар: " + e.getMessage());
            alert.showAndWait();
        }
    }

    private void showAuthForm() {
        try {
            lastCenter = productPane;
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/auth.fxml"));
            Node authNode = loader.load();
            headerPane.setVisible(false); headerPane.setManaged(false);
            categoryPane.setVisible(false); categoryPane.setManaged(false);
            mainPane.setCenter(authNode);
            mainPane.setTop(null); mainPane.setLeft(null);
            AuthController.setMainController(this);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void openCabinetInMain() {
        try {
            lastCenter = contentScroll;
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/cabinet.fxml"));
            Node cabinetNode = loader.load();
            headerPane.setVisible(false); headerPane.setManaged(false);
            categoryPane.setVisible(false); categoryPane.setManaged(false);
            mainPane.setCenter(cabinetNode);
            mainPane.setTop(null); mainPane.setLeft(null);
            CabinetController cabinetCtrl = loader.getController();
            cabinetCtrl.setHostMainController(this);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void showMainContent() {
        headerPane.setVisible(true); headerPane.setManaged(true);
        categoryPane.setVisible(true); categoryPane.setManaged(true);
        searchField.setVisible(true); searchField.setManaged(true);
        profileBtn.setVisible(true); profileBtn.setManaged(true);
        cartBtn.setVisible(true); cartBtn.setManaged(true);
        if (configuratorBtn != null) {
            configuratorBtn.setVisible(true);
            configuratorBtn.setManaged(true);
        }
        mainPane.setTop(headerPane);
        mainPane.setLeft(categoryPane);
        mainPane.setCenter(contentScroll);
        mainPane.getStylesheets().clear();
        filterByCategory(selectedCategory);
    }

    private void openCartView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/cart.fxml"));
            Node cartNode = loader.load();
            cartController = loader.getController();
            cartController.setMainController(this);
            mainPane.setCenter(cartNode);
            categoryPane.setVisible(false);
            mainPane.setLeft(null);
        } catch (Exception e) {
            e.printStackTrace();
            showErrorMessage("Ошибка", "Не удалось загрузить корзину");
        }
    }

    public void openConfigurator() {
        try {
            lastCenter = contentScroll;
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/configurator.fxml"));
            Node configNode = loader.load();
            headerPane.setVisible(false); headerPane.setManaged(false);
            categoryPane.setVisible(false); categoryPane.setManaged(false);
            mainPane.setCenter(configNode);
            mainPane.setTop(null); mainPane.setLeft(null);
            ConfiguratorController configCtrl = loader.getController();
            configCtrl.setMainController(this);
            configCtrl.setProducts(allProducts);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void openAdminPanel() {
        try {
            lastCenter = contentScroll;
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/admin.fxml"));
            Node adminNode = loader.load();
            headerPane.setVisible(false); headerPane.setManaged(false);
            categoryPane.setVisible(false); categoryPane.setManaged(false);
            mainPane.setCenter(adminNode);
            mainPane.setTop(null); mainPane.setLeft(null);
            AdminController adminController = loader.getController();
            adminController.setMainController(this);
            adminController.setCabinetController(null);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void hideCategoriesAndSearch() {
        if (categoryPane != null) { categoryPane.setVisible(false); categoryPane.setManaged(false); }
        if (searchField != null) { searchField.setVisible(false); searchField.setManaged(false); }
    }

    public void showCategoriesAndSearch() {
        if (categoryPane != null) { categoryPane.setVisible(true); categoryPane.setManaged(true); }
        if (searchField != null) { searchField.setVisible(true); searchField.setManaged(true); }
    }
}
