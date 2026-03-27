package controllers;

import com.example.authapp.dto.ProductDTO;
import com.example.authapp.repositories.AdminRepository;
import com.example.authapp.services.SupabaseStorageService;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class ManagerController implements Initializable {

    // ============ ШАПКА ============
    @FXML private Button    exitManagerBtn;
    @FXML private Label     managerEmailLabel;

    // ============ ТОВАРЫ ============
    @FXML private TableView<ProductDTO> productsTable;
    @FXML private TextField             searchField;
    @FXML private Button                addProductBtn;
    private ObservableList<ProductDTO>  productsData = FXCollections.observableArrayList();
    private boolean tableSetup = false;

    // ============ ЗАВИСИМОСТИ ============
    private MainController mainController;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        try {
            System.out.println("🗂️ ManagerController инициализирован");
            setupUI();
            loadProducts();
        } catch (Exception e) {
            System.err.println("❌ Ошибка инициализации ManagerController: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setMainController(MainController controller) {
        this.mainController = controller;
    }

    // ============================================
    // ✅ НАСТРОЙКА UI
    // ============================================

    private void setupUI() {
        if (managerEmailLabel != null) {
            String email = config.SessionManager.getUserEmail();
            managerEmailLabel.setText(email != null ? email : "Менеджер");
        }

        if (exitManagerBtn != null) {
            exitManagerBtn.setOnAction(e -> {
                if (mainController != null) mainController.showMainContent();
            });
        }

        if (addProductBtn != null) {
            addProductBtn.setOnAction(e -> showAddProductDialog());
        }

        // Живой поиск по таблице
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == null || newVal.isEmpty()) {
                    productsTable.setItems(productsData);
                } else {
                    String lower = newVal.toLowerCase();
                    ObservableList<ProductDTO> filtered = FXCollections.observableArrayList();
                    for (ProductDTO p : productsData) {
                        if ((p.name         != null && p.name.toLowerCase().contains(lower)) ||
                                (p.category     != null && p.category.toLowerCase().contains(lower)) ||
                                (p.manufacturer != null && p.manufacturer.toLowerCase().contains(lower))) {
                            filtered.add(p);
                        }
                    }
                    productsTable.setItems(filtered);
                }
            });
        }
    }

    // ============================================
    // ✅ ЗАГРУЗКА ТОВАРОВ
    // ============================================

    public void loadProducts() {
        new Thread(() -> {
            try {
                List<ProductDTO> products = AdminRepository.getAllProducts();
                Platform.runLater(() -> {
                    if (productsTable != null) {
                        if (!tableSetup) {
                            setupProductsTable();
                            tableSetup = true;
                        }
                        productsData.clear();
                        productsData.addAll(products);
                        productsTable.setItems(productsData);
                    }
                });
            } catch (Exception e) {
                System.err.println("❌ Ошибка загрузки товаров: " + e.getMessage());
            }
        }).start();
    }

    // ============================================
    // ✅ ТАБЛИЦА ТОВАРОВ
    // ============================================

    private void setupProductsTable() {
        productsTable.getColumns().clear();

        TableColumn<ProductDTO, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().id).asObject());
        idCol.setPrefWidth(50);

        TableColumn<ProductDTO, String> nameCol = new TableColumn<>("Название");
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().name));
        nameCol.setPrefWidth(200);

        TableColumn<ProductDTO, String> categoryCol = new TableColumn<>("Категория");
        categoryCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().category != null ? cd.getValue().category : "-"));
        categoryCol.setPrefWidth(120);

        TableColumn<ProductDTO, String> manufacturerCol = new TableColumn<>("Производитель");
        manufacturerCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().manufacturer != null ? cd.getValue().manufacturer : "-"));
        manufacturerCol.setPrefWidth(140);

        TableColumn<ProductDTO, Double> priceCol = new TableColumn<>("Цена (₽)");
        priceCol.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().price).asObject());
        priceCol.setPrefWidth(90);

        // ✅ Остаток с цветовой индикацией
        TableColumn<ProductDTO, Integer> stockCol = new TableColumn<>("Остаток");
        stockCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().stock).asObject());
        stockCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer stock, boolean empty) {
                super.updateItem(stock, empty);
                if (empty || stock == null) { setText(null); setStyle(""); return; }
                setText(stock + " шт.");
                if      (stock == 0) setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                else if (stock <= 5) setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: bold;");
                else                 setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
            }
        });
        stockCol.setPrefWidth(90);

        // ✅ Действия: редактировать, пополнить, фото, удалить
        TableColumn<ProductDTO, Void> actionCol = new TableColumn<>("Действия");
        actionCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0) { setGraphic(null); return; }

                ProductDTO p = getTableView().getItems().get(getIndex());
                HBox actions = new HBox(5);
                actions.setAlignment(Pos.CENTER);

                Button editBtn = new Button("✏️");
                editBtn.setTooltip(new Tooltip("Редактировать"));
                editBtn.setStyle(btnStyle("#3b82f6"));
                editBtn.setOnAction(e -> showEditProductDialog(p));

                Button restockBtn = new Button("➕");
                restockBtn.setTooltip(new Tooltip("Пополнить склад"));
                restockBtn.setStyle(btnStyle("#10b981"));
                restockBtn.setOnAction(e -> showRestockDialog(p));

                Button photoBtn = new Button("📷");
                photoBtn.setTooltip(new Tooltip("Загрузить фото"));
                photoBtn.setStyle(btnStyle("#6366f1"));
                photoBtn.setOnAction(e -> uploadProductImage(p));

                Button deleteBtn = new Button("🗑");
                deleteBtn.setTooltip(new Tooltip("Удалить товар"));
                deleteBtn.setStyle(btnStyle("#ef4444"));
                deleteBtn.setOnAction(e -> deleteProduct(p));

                actions.getChildren().addAll(editBtn, restockBtn, photoBtn, deleteBtn);
                setGraphic(actions);
            }
        });
        actionCol.setPrefWidth(190);

        productsTable.getColumns().addAll(idCol, nameCol, categoryCol, manufacturerCol, priceCol, stockCol, actionCol);
        System.out.println("✅ Таблица товаров (менеджер) настроена");
    }

    private String btnStyle(String color) {
        return "-fx-background-color: " + color + "; -fx-text-fill: white;" +
                " -fx-padding: 5 10; -fx-font-size: 13px;" +
                " -fx-background-radius: 5; -fx-border-width: 0; -fx-cursor: hand;";
    }

    // ============================================
    // ✅ ДОБАВЛЕНИЕ ТОВАРА
    // ============================================

    private void showAddProductDialog() {
        Dialog<ProductDTO> dialog = new Dialog<>();
        dialog.setTitle("➕ Добавить товар");
        dialog.setHeaderText("Заполните данные нового товара");

        GridPane grid = buildProductForm(null);

        TextField nameField         = (TextField) grid.getUserData();
        // поля извлекаем через lookup по индексу — используем отдельный массив
        TextField[] fields = (TextField[]) grid.getProperties().get("fields");
        TextField priceField        = fields[0];
        TextField stockField        = fields[1];
        TextField categoryField     = fields[2];
        TextField manufacturerField = fields[3];
        TextArea  descArea          = (TextArea) grid.getProperties().get("desc");

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(450);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    String name = nameField.getText().trim();
                    if (name.isEmpty()) { showAlert("❌ Ошибка", "Укажите название!"); return null; }
                    double price = Double.parseDouble(priceField.getText().trim());
                    int    stock = Integer.parseInt(stockField.getText().trim());
                    String cat   = categoryField.getText().trim();
                    String mfr   = manufacturerField.getText().trim();
                    String desc  = descArea.getText().trim();
                    return new ProductDTO(0, name, desc, price, stock, "", cat, mfr);
                } catch (NumberFormatException ex) {
                    showAlert("❌ Ошибка", "Проверьте формат цены и остатка!");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(product -> {
            if (product != null) saveNewProduct(product);
        });
    }

    private void saveNewProduct(ProductDTO product) {
        new Thread(() -> {
            try {
                AdminRepository.addProduct(product.name, product.description, product.price,
                        product.stock, product.category, product.manufacturer, "");
                Platform.runLater(() -> {
                    showAlert("✅ Успех", "Товар «" + product.name + "» добавлен!");
                    loadProducts();
                    if (mainController != null) mainController.reloadProducts();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("❌ Ошибка", e.getMessage()));
            }
        }).start();
    }

    // ============================================
    // ✅ РЕДАКТИРОВАНИЕ ТОВАРА
    // ============================================

    private void showEditProductDialog(ProductDTO product) {
        Dialog<ProductDTO> dialog = new Dialog<>();
        dialog.setTitle("✏️ Редактировать товар #" + product.id);
        dialog.setHeaderText("Редактирование: " + product.name);

        GridPane grid = buildProductForm(product);

        TextField nameField         = (TextField) grid.getUserData();
        TextField[] fields          = (TextField[]) grid.getProperties().get("fields");
        TextField priceField        = fields[0];
        TextField stockField        = fields[1];
        TextField categoryField     = fields[2];
        TextField manufacturerField = fields[3];
        TextArea  descArea          = (TextArea) grid.getProperties().get("desc");

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(450);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    String name = nameField.getText().trim();
                    if (name.isEmpty()) { showAlert("❌ Ошибка", "Укажите название!"); return null; }
                    double price = Double.parseDouble(priceField.getText().trim());
                    int    stock = Integer.parseInt(stockField.getText().trim());
                    String cat   = categoryField.getText().trim();
                    String mfr   = manufacturerField.getText().trim();
                    String desc  = descArea.getText().trim();
                    product.name         = name;
                    product.price        = price;
                    product.stock        = stock;
                    product.category     = cat;
                    product.manufacturer = mfr;
                    product.description  = desc;
                    return product;
                } catch (NumberFormatException ex) {
                    showAlert("❌ Ошибка", "Проверьте формат цены и остатка!");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updated -> {
            if (updated != null) updateProduct(updated);
        });
    }

    private void updateProduct(ProductDTO product) {
        new Thread(() -> {
            try {
                AdminRepository.updateProduct(product.id, product.name, product.description,
                        product.price, product.stock, product.category, product.manufacturer);
                Platform.runLater(() -> {
                    showAlert("✅ Успех", "Товар «" + product.name + "» обновлён!");
                    loadProducts();
                    if (mainController != null) mainController.reloadProducts();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("❌ Ошибка", e.getMessage()));
            }
        }).start();
    }

    // ============================================
    // ✅ ПОПОЛНЕНИЕ СКЛАДА
    // ============================================

    private void showRestockDialog(ProductDTO product) {
        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("➕ Пополнение склада");
        dialog.setHeaderText("Товар: " + product.name + "\nТекущий остаток: " + product.stock + " шт.");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField addField = new TextField();
        addField.setPromptText("Сколько добавить?");
        addField.setPrefWidth(200);

        grid.add(new Label("Добавить (шт.):"), 0, 0);
        grid.add(addField, 1, 0);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    int add = Integer.parseInt(addField.getText().trim());
                    if (add <= 0) { showAlert("❌ Ошибка", "Количество должно быть > 0!"); return null; }
                    return product.stock + add;
                } catch (NumberFormatException e) {
                    showAlert("❌ Ошибка", "Введите целое число!");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newStock -> {
            if (newStock != null) {
                new Thread(() -> {
                    try {
                        AdminRepository.updateProductStock(product.id, newStock);
                        Platform.runLater(() -> {
                            showAlert("✅ Успешно",
                                    "Остаток «" + product.name + "»:\n" +
                                            product.stock + " → " + newStock + " шт.");
                            loadProducts();
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert("❌ Ошибка", e.getMessage()));
                    }
                }).start();
            }
        });
    }

    // ============================================
    // ✅ ЗАГРУЗКА ФОТО
    // ============================================

    private void uploadProductImage(ProductDTO product) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Выберите изображение товара");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Изображения", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp"));

        File file = fc.showOpenDialog(productsTable.getScene().getWindow());
        if (file != null) {
            new Thread(() -> {
                try {
                    String fileName = SupabaseStorageService.generateFileName(product.id, file.getName());
                    String imageUrl = SupabaseStorageService.uploadImage(file, fileName);
                    AdminRepository.updateProductImage(product.id, imageUrl);
                    Platform.runLater(() -> {
                        product.imageUrl = imageUrl;
                        showAlert("✅ Успех", "Фото товара загружено!");
                        loadProducts();
                        if (mainController != null) mainController.reloadProducts();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert("❌ Ошибка", e.getMessage()));
                }
            }).start();
        }
    }

    // ============================================
    // ✅ УДАЛЕНИЕ ТОВАРА
    // ============================================

    private void deleteProduct(ProductDTO product) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("🗑 Удалить товар?");
        confirm.setHeaderText("Товар: " + product.name);
        confirm.setContentText("Это действие необратимо. Товар будет удалён со склада и из всех корзин.");

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        AdminRepository.deleteProduct(product.id);
                        Platform.runLater(() -> {
                            productsData.remove(product);
                            showAlert("✅ Успех", "Товар «" + product.name + "» удалён!");
                            if (mainController != null) mainController.reloadProducts();
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert("❌ Ошибка", e.getMessage()));
                    }
                }).start();
            }
        });
    }

    // ============================================
    // ✅ ВСПОМОГАТЕЛЬНЫЙ МЕТОД: форма товара
    // ============================================

    /**
     * Возвращает GridPane с полями товара.
     * Данные полей доступны через:
     *   grid.getUserData()               → nameField (TextField)
     *   grid.getProperties().get("fields") → TextField[] {price, stock, category, manufacturer}
     *   grid.getProperties().get("desc")   → TextArea description
     */
    private GridPane buildProductForm(ProductDTO product) {
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField         = new TextField(product != null ? product.name         : "");
        TextField priceField        = new TextField(product != null ? String.valueOf(product.price) : "");
        TextField stockField        = new TextField(product != null ? String.valueOf(product.stock) : "0");
        TextField categoryField     = new TextField(product != null && product.category     != null ? product.category     : "");
        TextField manufacturerField = new TextField(product != null && product.manufacturer != null ? product.manufacturer : "");
        TextArea  descArea          = new TextArea (product != null && product.description  != null ? product.description  : "");

        nameField.setPromptText("Название товара");
        priceField.setPromptText("Например: 9990.00");
        stockField.setPromptText("Количество на складе");
        categoryField.setPromptText("Например: Процессоры");
        manufacturerField.setPromptText("Например: Intel");
        descArea.setPromptText("Описание товара...");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);

        grid.add(new Label("Название *"),      0, 0); grid.add(nameField,         1, 0);
        grid.add(new Label("Цена (₽) *"),      0, 1); grid.add(priceField,        1, 1);
        grid.add(new Label("Остаток (шт.) *"), 0, 2); grid.add(stockField,        1, 2);
        grid.add(new Label("Категория"),        0, 3); grid.add(categoryField,     1, 3);
        grid.add(new Label("Производитель"),   0, 4); grid.add(manufacturerField, 1, 4);
        grid.add(new Label("Описание"),        0, 5); grid.add(descArea,          1, 5);

        grid.setUserData(nameField);
        grid.getProperties().put("fields", new TextField[]{priceField, stockField, categoryField, manufacturerField});
        grid.getProperties().put("desc", descArea);

        return grid;
    }

    // ============================================
    // ✅ УТИЛИТЫ
    // ============================================

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}