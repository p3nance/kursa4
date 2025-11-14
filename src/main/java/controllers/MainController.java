package controllers;

import com.example.authapp.models.Cart;
import config.SessionManager;
import controllers.CabinetController;
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
        System.out.println("✅ MainController инициализируется...");

        loadCategories();
        setupSearch();
        setupProfileButton();
        setupCartButton();

        // Загружаем товары в отдельном потоке
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
            System.out.println("🔄 Загрузка товаров из Supabase...");
            allProducts = ProductRepository.loadProductsFromSupabase();
            System.out.println("✅ Загружено товаров: " + allProducts.size());

            if (allProducts.isEmpty()) {
                System.out.println("⚠️ ВНИМАНИЕ: Товары не найдены в БД!");
                // Для тестирования - добавляем тестовые товары
                addTestProducts();
            }

            popularProducts = allProducts.stream()
                    .filter(p -> p.getCategory() != null &&
                            (p.getCategory().equalsIgnoreCase("Видеокарты") ||
                                    p.getCategory().equalsIgnoreCase("Процессоры")))
                    .toList();

            System.out.println("📦 Популярных товаров: " + popularProducts.size());

            // Обновляем UI в основном потоке
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
        allProducts.add(new Product(1, "RTX 4090", "Мощная видеокарта", 180000, 5, "", "Видеокарты", "NVIDIA"));
        allProducts.add(new Product(2, "Intel i9", "Процессор последнего поколения", 95000, 10, "", "Процессоры", "Intel"));
        allProducts.add(new Product(3, "DDR5 32GB", "Оперативная память", 15000, 20, "", "Оперативная память", "Kingston"));
        System.out.println("📝 Добавлены тестовые товары");
    }

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
        productPane.setStyle("-fx-background-color: #f5f5f5;");

        System.out.println("📊 Отображение товаров: " + products.size());

        if (products.isEmpty()) {
            Label emptyLabel = new Label("😔 Товары не найдены");
            emptyLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #888;");
            productPane.getChildren().add(emptyLabel);
            return;
        }

        for (Product product : products) {
            try {
                VBox card = createProductCard(product);
                productPane.getChildren().add(card);
            } catch (Exception e) {
                System.err.println("❌ Ошибка создания карточки: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("✅ Карточки добавлены в FlowPane. Всего: " + productPane.getChildren().size());
        lastCenter = contentScroll;
    }

    private VBox createProductCard(Product product) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(200);
        card.setMaxWidth(200);
        card.setMinHeight(320);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-border-color: #e5e7eb; -fx-border-width: 1; -fx-border-radius: 8; " +
                "-fx-background-color: #ffffff; -fx-background-radius: 8; " +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.08), 4, 0, 0, 2);");

        // Изображение
        ImageView imageView = new ImageView();
        imageView.setFitWidth(180);
        imageView.setFitHeight(120);
        imageView.setPreserveRatio(true);
        String img = product.getImageUrl() != null ? product.getImageUrl() : "";
        boolean isValidImageUrl = !img.isBlank() && (img.startsWith("http://") || img.startsWith("https://") || img.startsWith("file:/"));

        try {
            if (isValidImageUrl) {
                imageView.setImage(new Image(img, true));
            } else {
                imageView.setImage(new Image("file:src/main/resources/images/default-image.png"));
            }
        } catch (Exception e) {
            imageView.setStyle("-fx-background-color: #e5e7eb; -fx-min-width: 180; -fx-min-height: 120;");
        }

        // Название
        Label name = new Label(product.getName() == null ? "Без названия" : product.getName());
        name.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1f2937;");
        name.setWrapText(true);
        name.setPrefHeight(40);
        name.setMinHeight(40);
        VBox.setVgrow(name, javafx.scene.layout.Priority.NEVER);

        // Производитель
        Label manufacturer = new Label(product.getManufacturer() == null ? "" : product.getManufacturer());
        manufacturer.setStyle("-fx-font-size: 10px; -fx-text-fill: #9ca3af;");
        manufacturer.setPrefHeight(25);
        manufacturer.setMinHeight(25);

        // Цена
        Label price = new Label(String.format("%,.0f ₽", product.getPrice()));
        price.setStyle("-fx-text-fill: #10b981; -fx-font-size: 14px; -fx-font-weight: bold;");
        price.setPrefHeight(25);
        price.setMinHeight(25);

        // Spacer
        Region spacer = new Region();
        VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        // КНОПКИ
        HBox buttonBox = new HBox(5);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPrefHeight(35);
        buttonBox.setMinHeight(35);

        Button detailsBtn = new Button("Подробнее");
        detailsBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 4; -fx-padding: 6px 8px; -fx-font-size: 11px;");
        detailsBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(detailsBtn, javafx.scene.layout.Priority.ALWAYS);
        detailsBtn.setOnAction(event -> showProductDetail(product));

        Button addToCartBtn = new Button("🛒");
        addToCartBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-background-radius: 4; -fx-padding: 6px 10px; -fx-cursor: hand;");
        addToCartBtn.setOnAction(e -> addProductToCart(product, 1));  // ✅ ПРОСТО И ПОНЯТНО

        buttonBox.getChildren().addAll(detailsBtn, addToCartBtn);
        card.getChildren().addAll(imageView, name, manufacturer, price, spacer, buttonBox);

        return card;
    }

    private void showProductDetail(Product product) {
        productPane.getChildren().clear();
        VBox detail = new VBox(15);
        detail.setPadding(new Insets(25));
        detail.setStyle("-fx-background-color: #ffffff;");

        Button backBtn = new Button("← Назад к товарам");
        backBtn.setStyle("-fx-background-color: #6b7280; -fx-text-fill: white; -fx-padding: 10px 20px; -fx-font-size: 12px; -fx-cursor: hand; -fx-background-radius: 6;");
        backBtn.setOnAction(e -> filterByCategory(selectedCategory));

        ImageView largeImage = new ImageView();
        largeImage.setFitWidth(400);
        largeImage.setFitHeight(300);
        largeImage.setPreserveRatio(true);
        String img = product.getImageUrl() != null ? product.getImageUrl() : "";
        boolean isValidImageUrl = !img.isBlank() && (img.startsWith("http://") || img.startsWith("https://") || img.startsWith("file:/"));

        try {
            if (isValidImageUrl) {
                largeImage.setImage(new Image(img, true));
            } else {
                largeImage.setImage(new Image("file:src/main/resources/images/default-image.png"));
            }
        } catch (Exception e) {
            largeImage.setStyle("-fx-background-color: #e5e7eb; -fx-min-width: 400; -fx-min-height: 300;");
        }

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
                Cart.getInstance().addProduct(product);;
            }
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Успешно");
            alert.setHeaderText(null);
            alert.setContentText(product.getName() + " добавлен в корзину!");
            alert.showAndWait();
        });

        Button buyNowBtn = new Button("💳 Купить сейчас");
        buyNowBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-padding: 12px 40px; -fx-font-size: 14px;");
        buyNowBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Оформление");
            alert.setHeaderText(null);
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
            // Сохраняем текущее содержимое ПЕРЕД заменой
            lastCenter = productPane; // или contentScroll

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/auth.fxml"));
            Node authNode = loader.load();

            // Скрываем компоненты но НЕ устанавливаем их в null
            headerPane.setVisible(false);
            headerPane.setManaged(false);
            categoryPane.setVisible(false);
            categoryPane.setManaged(false);

            // Заменяем только контент
            mainPane.setCenter(authNode);
            mainPane.setTop(null);
            mainPane.setLeft(null);

            AuthController.setMainController(this);

            System.out.println("📧 Форма авторизации открыта");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void openCabinetInMain() {
        try {
            lastCenter = contentScroll;
            System.out.println("💾 Сохранено lastCenter как contentScroll");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/cabinet.fxml"));
            Node cabinetNode = loader.load();

            headerPane.setVisible(false);
            headerPane.setManaged(false);  // ✅ Это важно!

            categoryPane.setVisible(false);
            categoryPane.setManaged(false);

            mainPane.setCenter(cabinetNode);
            mainPane.setTop(null);
            mainPane.setLeft(null);

            CabinetController.setHostMainController(this);
            System.out.println("👤 Кабинет открыт");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showMainContent() {
        System.out.println("🔄 Восстановление главного контента...");

        // ✅ ВОССТАНАВЛИВАЕМ ВСЕ КОМПОНЕНТЫ
        headerPane.setVisible(true);
        headerPane.setManaged(true);  // ✅ Это важно!

        categoryPane.setVisible(true);
        categoryPane.setManaged(true);

        searchField.setVisible(true);
        searchField.setManaged(true);

        profileBtn.setVisible(true);
        profileBtn.setManaged(true);

        cartBtn.setVisible(true);  // ✅ ВОССТАНАВЛИВАЕМ КНОПКУ КОРЗИНЫ
        cartBtn.setManaged(true);

        // Восстанавливаем структуру BorderPane
        mainPane.setTop(headerPane);
        mainPane.setLeft(categoryPane);
        mainPane.setCenter(contentScroll);

        // Очищаем CSS если был добавлен
        mainPane.getStylesheets().clear();

        // Перезагружаем товары
        filterByCategory(selectedCategory);

        System.out.println("✅ Главный контент полностью восстановлен! Кнопка корзины видима!");
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

    private void setupCartButton() {
        cartBtn.setOnAction(e -> openCartView());
    }
    private void addProductToCart(Product product, int quantity) {
        try {
            System.out.println("🛒 Добавление в корзину:");
            System.out.println("   Товар: " + product.getName());
            System.out.println("   Цена: " + product.getPrice());
            System.out.println("   Количество: " + quantity);

            // Используем CartService для добавления в БД
            CartService cartService = new CartService();
            cartService.addProductToCart(product, quantity);

            Cart cart = Cart.getInstance();
            System.out.println("📊 Состояние корзины:");
            System.out.println("   Товаров: " + cart.getTotalQuantity());
            System.out.println("   Сумма: " + cart.getTotal());
            System.out.println("✅ Товар добавлен успешно!");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Корзина");
            alert.setHeaderText(null);
            alert.setContentText(product.getName() + " добавлен в корзину!\nТоваров в корзине: " +
                    cart.getTotalQuantity() + "\nИтого: " + String.format("%.2f ₽", cart.getTotal()));
            alert.showAndWait();
        } catch (Exception e) {
            System.err.println("❌ Ошибка добавления в корзину: " + e.getMessage());
            e.printStackTrace();

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText(null);
            alert.setContentText("Не удалось добавить товар: " + e.getMessage());
            alert.showAndWait();
        }
    }

    public void hideCategoriesAndSearch() {
        if (categoryPane != null) {
            categoryPane.setVisible(false);
            categoryPane.setManaged(false);
        }
        if (searchField != null) {
            searchField.setVisible(false);
            searchField.setManaged(false);
        }
    }
    public void showCategoriesAndSearch() {
        if (categoryPane != null) {
            categoryPane.setVisible(true);
            categoryPane.setManaged(true);
        }
        if (searchField != null) {
            searchField.setVisible(true);
            searchField.setManaged(true);
        }
    }
    // ✅ НОВЫЙ ПУБЛИЧНЫЙ МЕТОД
    public void openAdminPanel() {
        try {
            lastCenter = contentScroll;
            System.out.println("💾 Сохранено lastCenter как contentScroll");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/admin.fxml"));
            Node adminNode = loader.load();

            // НЕ скрываем headerPane полностью, только меняем видимость кнопок
            headerPane.setVisible(false);
            headerPane.setManaged(false);  // ✅ Это важно!

            categoryPane.setVisible(false);
            categoryPane.setManaged(false);

            mainPane.setCenter(adminNode);
            mainPane.setTop(null);
            mainPane.setLeft(null);

            AdminController adminController = loader.getController();
            adminController.setMainController(this);

            System.out.println("👑 АДМИН ПАНЕЛЬ ОТКРЫТА");
        } catch (Exception e) {
            e.printStackTrace();
            showErrorMessage("Ошибка", "Не удалось загрузить админ панель: " + e.getMessage());
        }
    }






}
