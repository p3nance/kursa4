package controllers;

import com.example.authapp.models.Product;
import com.example.authapp.models.PromoCode;
import com.example.authapp.services.AppStateManager;
import com.example.authapp.services.ProductService;
import com.example.authapp.services.PromoCodeService;
import com.example.authapp.utils.ValidationUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class AdminController implements Initializable {
    // Товары
    @FXML private TableView<Product> productsTable;
    @FXML private TableColumn<Product, String> productNameColumn;
    @FXML private TableColumn<Product, Double> priceColumn;
    @FXML private TableColumn<Product, Integer> stockColumn;
    @FXML private TableColumn<Product, String> categoryColumn;

    @FXML private TextField productNameField;
    @FXML private TextField productPriceField;
    @FXML private TextField productStockField;
    @FXML private TextField productImageUrlField;
    @FXML private TextArea productDescriptionArea;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private TextField manufacturerField;
    @FXML private Button addProductButton;
    @FXML private Button updateProductButton;
    @FXML private Button deleteProductButton;

    // Промокоды
    @FXML private TableView<PromoCode> promoCodesTable;
    @FXML private TableColumn<PromoCode, String> codeColumn;
    @FXML private TableColumn<PromoCode, Double> discountColumn;
    @FXML private TableColumn<PromoCode, Integer> maxUsesColumn;
    @FXML private TableColumn<PromoCode, Boolean> activeColumn;

    @FXML private TextField promoCodeField;
    @FXML private TextField discountPercentField;
    @FXML private TextField maxUsesField;
    @FXML private DatePicker expiryDatePicker;
    @FXML private CheckBox activeCheckBox;
    @FXML private Button addPromoButton;
    @FXML private Button deletePromoButton;

    private ProductService productService;
    private PromoCodeService promoCodeService;
    private Product selectedProduct;
    private PromoCode selectedPromoCode;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        AppStateManager manager = AppStateManager.getInstance();
        this.productService = manager.getProductService();
        this.promoCodeService = manager.getPromoCodeService();

        setupProductsTable();
        setupPromoCodesTable();
        setupCategoryCombo();
        setupButtons();
        loadData();
    }

    private void setupProductsTable() {
        productNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("price"));
        stockColumn.setCellValueFactory(new PropertyValueFactory<>("stock"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));

        productsTable.setOnMouseClicked(event -> {
            Product selected = productsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selectedProduct = selected;
                populateProductFields(selected);
            }
        });
    }

    private void setupPromoCodesTable() {
        codeColumn.setCellValueFactory(new PropertyValueFactory<>("code"));
        discountColumn.setCellValueFactory(new PropertyValueFactory<>("discountPercent"));
        maxUsesColumn.setCellValueFactory(new PropertyValueFactory<>("maxUses"));
        activeColumn.setCellValueFactory(new PropertyValueFactory<>("isActive"));

        promoCodesTable.setOnMouseClicked(event -> {
            PromoCode selected = promoCodesTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selectedPromoCode = selected;
                populatePromoFields(selected);
            }
        });
    }

    private void setupCategoryCombo() {
        categoryCombo.setItems(FXCollections.observableArrayList(
                "Процессоры", "Видеокарты", "Материнские платы",
                "Оперативная память", "Жёсткие диски", "SSD-накопители",
                "Блоки питания", "Корпуса", "Охлаждение", "Мониторы",
                "Клавиатуры", "Мыши", "Аксессуары"
        ));
    }

    private void setupButtons() {
        addProductButton.setOnAction(e -> addProduct());
        updateProductButton.setOnAction(e -> updateProduct());
        deleteProductButton.setOnAction(e -> deleteProduct());
        addPromoButton.setOnAction(e -> addPromoCode());
        deletePromoButton.setOnAction(e -> deletePromoCode());
    }

    @FXML
    private void addProduct() {
        try {
            String name = productNameField.getText().trim();
            String priceStr = productPriceField.getText().trim();
            String stockStr = productStockField.getText().trim();
            String imageUrl = productImageUrlField.getText().trim();
            String description = productDescriptionArea.getText().trim();
            String category = categoryCombo.getValue();
            String manufacturer = manufacturerField.getText().trim();

            if (!ValidationUtil.isNotEmpty(name)) {
                showAlert("Ошибка", "Введите название товара");
                return;
            }

            if (!ValidationUtil.isValidPrice(priceStr)) {
                showAlert("Ошибка", "Введите корректную цену");
                return;
            }

            if (!ValidationUtil.isValidStock(stockStr)) {
                showAlert("Ошибка", "Введите корректное количество");
                return;
            }

            double price = Double.parseDouble(priceStr);
            int stock = Integer.parseInt(stockStr);

            productService.addProduct(name, price, description, stock, imageUrl, category);
            showAlert("Успех", "Товар успешно добавлен");
            clearProductFields();
            loadData();
        } catch (Exception e) {
            showAlert("Ошибка", "Ошибка добавления товара: " + e.getMessage());
        }
    }

    @FXML
    private void updateProduct() {
        if (selectedProduct == null) {
            showAlert("Ошибка", "Выберите товар для обновления");
            return;
        }

        try {
            String name = productNameField.getText().trim();
            String priceStr = productPriceField.getText().trim();
            String stockStr = productStockField.getText().trim();
            String description = productDescriptionArea.getText().trim();
            String category = categoryCombo.getValue();
            String manufacturer = manufacturerField.getText().trim();

            if (!ValidationUtil.isNotEmpty(name) || !ValidationUtil.isValidPrice(priceStr) ||
                    !ValidationUtil.isValidStock(stockStr)) {
                showAlert("Ошибка", "Проверьте корректность данных");
                return;
            }

            double price = Double.parseDouble(priceStr);
            int stock = Integer.parseInt(stockStr);

            productService.updateProduct(selectedProduct.getId(), name, price, stock, description, category, manufacturer);
            showAlert("Успех", "Товар успешно обновлен");
            clearProductFields();
            loadData();
        } catch (Exception e) {
            showAlert("Ошибка", "Ошибка обновления товара: " + e.getMessage());
        }
    }

    @FXML
    private void deleteProduct() {
        if (selectedProduct == null) {
            showAlert("Ошибка", "Выберите товар для удаления");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Подтверждение");
        confirmAlert.setContentText("Удалить товар: " + selectedProduct.getName() + "?");

        if (confirmAlert.showAndWait().get() == ButtonType.OK) {
            try {
                productService.deleteProduct(selectedProduct.getId());
                showAlert("Успех", "Товар удален");
                clearProductFields();
                loadData();
            } catch (Exception e) {
                showAlert("Ошибка", "Ошибка удаления товара: " + e.getMessage());
            }
        }
    }

    @FXML
    private void addPromoCode() {
        try {
            String code = promoCodeField.getText().trim().toUpperCase();
            String discountStr = discountPercentField.getText().trim();
            String maxUsesStr = maxUsesField.getText().trim();
            LocalDate expiryDate = expiryDatePicker.getValue();

            if (!ValidationUtil.isValidPromoCode(code)) {
                showAlert("Ошибка", "Неверный формат кода (только латиница и цифры)");
                return;
            }

            try {
                double discount = Double.parseDouble(discountStr);
                int maxUses = Integer.parseInt(maxUsesStr);

                if (discount < 0 || discount > 100 || maxUses <= 0 || expiryDate == null) {
                    showAlert("Ошибка", "Проверьте корректность данных");
                    return;
                }

                promoCodeService.createPromoCode(code, discount, maxUses, expiryDate);
                showAlert("Успех", "Промокод успешно создан");
                clearPromoFields();
                loadData();
            } catch (NumberFormatException e) {
                showAlert("Ошибка", "Некорректный формат скидки или максимального использования");
            }
        } catch (Exception e) {
            showAlert("Ошибка", "Ошибка создания промокода: " + e.getMessage());
        }
    }

    @FXML
    private void deletePromoCode() {
        if (selectedPromoCode == null) {
            showAlert("Ошибка", "Выберите промокод для удаления");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Подтверждение");
        confirmAlert.setContentText("Удалить промокод: " + selectedPromoCode.getCode() + "?");

        if (confirmAlert.showAndWait().get() == ButtonType.OK) {
            try {
                promoCodeService.deletePromoCode(selectedPromoCode.getCode());
                showAlert("Успех", "Промокод удален");
                clearPromoFields();
                loadData();
            } catch (Exception e) {
                showAlert("Ошибка", "Ошибка удаления промокода: " + e.getMessage());
            }
        }
    }

    private void populateProductFields(Product product) {
        productNameField.setText(product.getName());
        productPriceField.setText(String.valueOf(product.getPrice()));
        productStockField.setText(String.valueOf(product.getStock()));
        productImageUrlField.setText(product.getImageUrl());
        productDescriptionArea.setText(product.getDescription());
        categoryCombo.setValue(product.getCategory());
        manufacturerField.setText(product.getManufacturer());
    }

    private void populatePromoFields(PromoCode promo) {
        promoCodeField.setText(promo.getCode());
        discountPercentField.setText(String.valueOf(promo.getDiscountPercent()));
        maxUsesField.setText(String.valueOf(promo.getMaxUses()));
        expiryDatePicker.setValue(LocalDate.parse(promo.getExpiryDate()));
        activeCheckBox.setSelected(promo.getIsActive());
    }

    private void loadData() {
        try {
            List<Product> products = productService.getAllProducts();
            productsTable.setItems(FXCollections.observableArrayList(products));
        } catch (Exception e) {
            System.err.println("Ошибка загрузки товаров: " + e.getMessage());
        }
    }

    private void clearProductFields() {
        productNameField.clear();
        productPriceField.clear();
        productStockField.clear();
        productImageUrlField.clear();
        productDescriptionArea.clear();
        manufacturerField.clear();
        selectedProduct = null;
    }

    private void clearPromoFields() {
        promoCodeField.clear();
        discountPercentField.clear();
        maxUsesField.clear();
        expiryDatePicker.setValue(null);
        activeCheckBox.setSelected(false);
        selectedPromoCode = null;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}