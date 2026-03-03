package controllers;

import config.SessionManager; // Не забудь импорт для проверки авторизации
import com.example.authapp.models.Cart;
import com.example.authapp.models.Product;
import com.example.authapp.services.CartService;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.*;
import java.util.stream.Collectors;

public class ConfiguratorController {

    @FXML private BorderPane mainContent;
    @FXML private Button backBtn;
    @FXML private VBox categoriesContainer;

    @FXML private Label totalPrice;
    @FXML private HBox compatStatusBox;
    @FXML private Label compatIcon;
    @FXML private Label compatStatusText;

    @FXML private ListView<String> componentsList;
    @FXML private Button addBuildToCart;

    // Плашка статуса корзины
    @FXML private HBox cartStatusBox;
    @FXML private Label cartStatusIcon;
    @FXML private Label cartStatusText;

    // Элементы оверлея
    @FXML private VBox overlayPane;
    @FXML private Label overlayTitle;
    @FXML private Button closeOverlayBtn;
    @FXML private Button clearSelectionBtn;
    @FXML private TilePane overlayProductsGrid;

    private MainController mainController;
    private final List<Product> allProducts = new ArrayList<>();
    private final Map<String, Product> selectedComponents = new LinkedHashMap<>();

    private final String[] categoryNames = {
            "Процессоры", "Материнские платы", "Видеокарты",
            "Оперативная память", "SSD-накопители", "Блоки питания"
    };

    private String currentEditingCategory = null;

    public void initialize() {
        backBtn.setOnAction(e -> goBack());
        addBuildToCart.setOnAction(e -> addBuildToCart());

        closeOverlayBtn.setOnAction(e -> closeOverlay());
        clearSelectionBtn.setOnAction(e -> {
            if (currentEditingCategory != null) {
                selectedComponents.put(currentEditingCategory, null);
                renderCategories();
                recalcBuild();
                closeOverlay();
            }
        });

        for (String cat : categoryNames) {
            selectedComponents.put(cat, null);
        }

        compatStatusText.setWrapText(true);
        compatStatusText.maxWidthProperty().bind(compatStatusBox.widthProperty().subtract(40));

        cartStatusText.setWrapText(true);
    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    private void goBack() {
        if (mainController != null) mainController.showMainContent();
    }

    public void setProducts(List<Product> products) {
        this.allProducts.clear();
        this.allProducts.addAll(products);
        renderCategories();
        recalcBuild();
    }

    private void renderCategories() {
        categoriesContainer.getChildren().clear();

        for (String category : categoryNames) {
            HBox catBox = new HBox(15);
            catBox.setAlignment(Pos.CENTER_LEFT);
            catBox.setStyle("-fx-background-color: white; -fx-padding: 15 20; -fx-border-color: #e5e7eb; -fx-border-radius: 10; -fx-background-radius: 10;");

            Label icon = new Label(getIconForCategory(category));
            icon.setStyle("-fx-font-size: 24px;");

            VBox nameBox = new VBox(4);
            Label catLabel = new Label(category);
            catLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1f2937;");

            Product selected = selectedComponents.get(category);
            Label selectedLabel = new Label(selected != null ? selected.getName() : "Не выбрано");
            selectedLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (selected != null ? "#3b82f6" : "#9ca3af") + ";");
            nameBox.getChildren().addAll(catLabel, selectedLabel);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label priceLabel = new Label(selected != null ? String.format("%,.0f ₽", selected.getPrice()) : "");
            priceLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #111827;");

            Button selectBtn = new Button(selected != null ? "Изменить" : "Выбрать");

            String btnStyle = selected != null
                    ? "-fx-background-color: #f3f4f6; -fx-text-fill: #374151; " +
                    "-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 8 16; " +
                    "-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-width: 1; -fx-border-color: #d1d5db; -fx-cursor: hand;"
                    : "-fx-background-color: linear-gradient(to bottom, #3b82f6, #1d4ed8); " +
                    "-fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-font-weight: bold; " +
                    "-fx-padding: 8 16; -fx-background-radius: 8; -fx-border-radius: 8; " +
                    "-fx-border-width: 1.5; -fx-border-color: #93c5fd; -fx-cursor: hand; " +
                    "-fx-effect: dropshadow(gaussian, rgba(29,78,216,0.3), 8, 0, 0, 2);";

            selectBtn.setStyle(btnStyle);
            selectBtn.setOnAction(e -> openOverlayForCategory(category));

            catBox.getChildren().addAll(icon, nameBox, spacer, priceLabel, selectBtn);
            categoriesContainer.getChildren().add(catBox);
        }
    }

    private String getIconForCategory(String cat) {
        switch (cat) {
            case "Процессоры": return "⚙";
            case "Материнские платы": return "🖧";
            case "Видеокарты": return "📺";
            case "Оперативная память": return "☷";
            case "SSD-накопители": return "🖴";
            case "Блоки питания": return "🔋";
            default: return "📦";
        }
    }

    private void openOverlayForCategory(String category) {
        currentEditingCategory = category;
        overlayTitle.setText("Доступные " + category.toLowerCase());

        List<Product> availableProducts = allProducts.stream()
                .filter(p -> p.getCategory() != null && p.getCategory().equalsIgnoreCase(category))
                .collect(Collectors.toList());

        overlayProductsGrid.getChildren().clear();

        for (Product p : availableProducts) {
            VBox card = new VBox(8);
            card.setAlignment(Pos.TOP_CENTER);
            card.setStyle("-fx-background-color: white; -fx-border-color: #e5e7eb; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 15; -fx-cursor: hand;");
            card.setPrefWidth(210);

            ImageView iv = new ImageView();
            iv.setFitWidth(150); iv.setFitHeight(100); iv.setPreserveRatio(true);
            try { iv.setImage(new Image(p.getImageUrl(), true)); }
            catch (Exception e) { iv.setStyle("-fx-background-color: #e5e7eb;"); }

            Label name = new Label(p.getName());
            name.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
            name.setWrapText(true);
            name.setAlignment(Pos.CENTER);
            name.setMinHeight(35);

            Label price = new Label(String.format("%,.0f ₽", p.getPrice()));
            price.setStyle("-fx-font-weight: bold; -fx-text-fill: #10b981; -fx-font-size: 14px;");

            card.getChildren().addAll(iv, name, price);

            card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #3b82f6; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 15; -fx-cursor: hand;"));
            card.setOnMouseExited(e -> card.setStyle("-fx-background-color: white; -fx-border-color: #e5e7eb; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 15; -fx-cursor: hand;"));

            card.setOnMouseClicked(e -> {
                selectedComponents.put(category, p);
                renderCategories();
                recalcBuild();
                closeOverlay();
            });

            overlayProductsGrid.getChildren().add(card);
        }

        if (availableProducts.isEmpty()) {
            Label empty = new Label("Товары не найдены");
            empty.setStyle("-fx-font-size: 14px; -fx-text-fill: #888;");
            overlayProductsGrid.getChildren().add(empty);
        }

        mainContent.setDisable(true);
        overlayPane.setVisible(true);
        cartStatusBox.setVisible(false);
        cartStatusBox.setManaged(false);
    }

    private void closeOverlay() {
        overlayPane.setVisible(false);
        mainContent.setDisable(false);
        currentEditingCategory = null;
    }

    private void recalcBuild() {
        componentsList.getItems().clear();
        double sum = 0;
        int count = 0;

        Product cpu = selectedComponents.get("Процессоры");
        Product mobo = selectedComponents.get("Материнские платы");
        Product gpu = selectedComponents.get("Видеокарты");
        Product ram = selectedComponents.get("Оперативная память");
        Product storage = selectedComponents.get("SSD-накопители");
        Product psu = selectedComponents.get("Блоки питания");

        for (String cat : categoryNames) {
            Product p = selectedComponents.get(cat);
            if (p != null) {
                componentsList.getItems().add("• " + p.getName() + "\n  " + String.format("%,.0f ₽", p.getPrice()));
                sum += p.getPrice();
                count++;
            }
        }

        totalPrice.setText(String.format("%,.0f ₽", sum));

        List<String> issues = checkCompatibility(cpu, mobo, gpu, ram, storage, psu);

        compatStatusBox.setMinHeight(Region.USE_PREF_SIZE);

        if (count == 0) {
            compatStatusBox.setStyle("-fx-background-color: #f3f4f6; -fx-padding: 10 15; -fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #d1d5db;");
            compatIcon.setText("ℹ");
            compatIcon.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 16px;");
            compatStatusText.setText("Сборка пуста. Начните выбор компонентов.");
            compatStatusText.setStyle("-fx-text-fill: #4b5563; -fx-font-size: 13px; -fx-font-weight: bold;");

        } else if (issues.isEmpty()) {
            compatStatusBox.setStyle("-fx-background-color: #ecfdf5; -fx-padding: 10 15; -fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #10b981;");
            compatIcon.setText("✅");
            compatIcon.setStyle("-fx-text-fill: #10b981; -fx-font-size: 16px;");
            compatStatusText.setText("Все выбранные компоненты совместимы!");
            compatStatusText.setStyle("-fx-text-fill: #065f46; -fx-font-size: 13px; -fx-font-weight: bold;");

        } else {
            compatStatusBox.setStyle("-fx-background-color: #fef2f2; -fx-padding: 10 15; -fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #ef4444;");
            compatIcon.setText("❌");
            compatIcon.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 16px;");
            compatStatusText.setText(String.join("\n", issues));
            compatStatusText.setStyle("-fx-text-fill: #991b1b; -fx-font-size: 13px; -fx-font-weight: bold;");
        }
    }

    private String getParam(Product product, String key) {
        if (product == null || product.getDescription() == null) return null;
        String desc = product.getDescription().toLowerCase();
        String searchKey = key.toLowerCase() + ":";
        int idx = desc.indexOf(searchKey);
        if (idx == -1) return null;
        int start = idx + searchKey.length();
        int end = desc.indexOf(" ", start);
        return end == -1 ? desc.substring(start).trim() : desc.substring(start, end).trim();
    }

    private int getIntParam(Product product, String key) {
        String val = getParam(product, key);
        if (val == null) return 0;
        try { return Integer.parseInt(val); } catch (Exception e) { return 0; }
    }

    private List<String> checkCompatibility(Product cpu, Product mobo, Product gpu, Product ram, Product storage, Product psu) {
        List<String> issues = new ArrayList<>();

        if (cpu != null && mobo != null) {
            String cpuSocket = getParam(cpu, "socket");
            String moboSocket = getParam(mobo, "socket");
            if (cpuSocket != null && moboSocket != null && !cpuSocket.equalsIgnoreCase(moboSocket)) {
                issues.add("Сокет процессора не подходит к плате");
            }
        }

        if (mobo != null && ram != null) {
            String moboMem = getParam(mobo, "memory");
            String ramMem = getParam(ram, "memory");
            if (moboMem != null && ramMem != null && !moboMem.equalsIgnoreCase(ramMem)) {
                issues.add("Память не поддерживается платой");
            }
        }

        if (psu != null) {
            int psuPower = getIntParam(psu, "psu_power");
            int cpuTdp = getIntParam(cpu, "tdp");
            int gpuPower = getIntParam(gpu, "gpu_power");
            int totalNeed = cpuTdp + gpuPower;

            if (psuPower > 0 && totalNeed > 0 && psuPower < totalNeed) {
                issues.add("Блок питания слабоват. Нужно минимум " + totalNeed + "Вт");
            }
        }
        return issues;
    }

    // ─────────────────────────────────────────────
    //  ДОБАВЛЕНИЕ В КОРЗИНУ (БЕЗ ALERT'ОВ)
    // ─────────────────────────────────────────────

    private void addBuildToCart() {
        // Проверка авторизации
        if (SessionManager.getAccessToken() == null || SessionManager.getUserEmail() == null) {
            showCartStatus("Вы не авторизованы. Войдите в аккаунт, чтобы добавить товары в корзину.", false);
            return;
        }

        List<Product> toAdd = selectedComponents.values().stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (toAdd.isEmpty()) {
            showCartStatus("Сборка пуста! Выберите хотя бы один компонент.", false);
            return;
        }

        try {
            CartService cartService = new CartService();
            for (Product p : toAdd) {
                cartService.addProductToCart(p, 1);
            }
            showCartStatus("Товары успешно добавлены в корзину!", true);
        } catch (Exception e) {
            showCartStatus("Ошибка: " + e.getMessage(), false);
        }
    }

    private void showCartStatus(String message, boolean isSuccess) {
        cartStatusBox.setVisible(true);
        cartStatusBox.setManaged(true);
        cartStatusText.setText(message);

        if (isSuccess) {
            cartStatusBox.setStyle("-fx-background-color: #ecfdf5; -fx-padding: 10 15; -fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #10b981;");
            cartStatusIcon.setText("✅");
            cartStatusIcon.setStyle("-fx-text-fill: #10b981; -fx-font-size: 16px;");
            cartStatusText.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #065f46;");
        } else {
            cartStatusBox.setStyle("-fx-background-color: #fef2f2; -fx-padding: 10 15; -fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #ef4444;");
            cartStatusIcon.setText("❌");
            cartStatusIcon.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 16px;");
            cartStatusText.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #991b1b;");
        }

        // Автоматически скрывать сообщение через 5 секунд
        PauseTransition pause = new PauseTransition(Duration.seconds(5));
        pause.setOnFinished(event -> {
            cartStatusBox.setVisible(false);
            cartStatusBox.setManaged(false);
        });
        pause.play();
    }
}
