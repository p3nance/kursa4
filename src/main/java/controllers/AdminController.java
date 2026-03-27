package controllers;

import com.example.authapp.dto.ProductDTO;
import com.example.authapp.dto.UserDTO;
import com.example.authapp.dto.OrderDTO;
import com.example.authapp.dto.PromoCodeDTO;
import com.example.authapp.models.PromoCode;
import com.example.authapp.repositories.AdminRepository;
import com.example.authapp.services.SupabaseStorageService;
import com.example.authapp.services.AdminRefreshService;

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
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class AdminController implements Initializable {

    // ============ ОБЩИЕ ЭЛЕМЕНТЫ ============
    @FXML private TabPane adminTabs;
    @FXML private Button  exitAdminBtn;

    // ============ ТОВАРЫ ============
    @FXML private Button              addProductBtn;
    @FXML private TableView<ProductDTO> productsTable;
    private ObservableList<ProductDTO> productsData = FXCollections.observableArrayList();
    private boolean productsTableSetup = false;

    // ============ ПОЛЬЗОВАТЕЛИ ============
    @FXML private TableView<UserDTO> usersTable;
    @FXML private TextField          userSearchField;
    private ObservableList<UserDTO>  usersData = FXCollections.observableArrayList();
    private boolean usersTableSetup = false;

    // ============ ЗАКАЗЫ ============
    @FXML private TableView<OrderDTO>  ordersTable;
    @FXML private ComboBox<String>     orderStatusFilter;
    private ObservableList<OrderDTO>   ordersData = FXCollections.observableArrayList();
    private boolean ordersTableSetup = false;

    // ============ ПРОМОКОДЫ ============
    @FXML private TableView<PromoCodeDTO> promoCodesTable;
    @FXML private Button                  addPromoCodeBtn;
    private ObservableList<PromoCodeDTO>  promoCodesData = FXCollections.observableArrayList();
    private boolean promoCodesTableSetup = false;

    // ============ КОНТРОЛЛЕРЫ И СЕРВИСЫ ============
    private MainController    mainController;
    private CabinetController cabinetController;
    private AdminRefreshService refreshService;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        try {
            System.out.println("🎨 AdminController инициализирован");
            setupUI();
            loadAdminData();
            refreshService = new AdminRefreshService(this);
        } catch (Exception e) {
            System.err.println("❌ Ошибка инициализации: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setMainController(MainController controller) {
        this.mainController = controller;
    }

    public void setCabinetController(CabinetController cabinet) {
        this.cabinetController = cabinet;
    }

    // ============================================
    // ✅ Настройка UI элементов
    // ============================================
    private void setupUI() {
        if (exitAdminBtn != null) {
            exitAdminBtn.setOnAction(e -> {
                if (refreshService != null) refreshService.stop();
                if (mainController != null) mainController.showMainContent();
            });
        }

        if (addProductBtn != null) {
            addProductBtn.setOnAction(e -> showAddProductDialog());
        }

        if (addPromoCodeBtn != null) {
            addPromoCodeBtn.setOnAction(e -> showAddPromoCodeDialog());
        }

        if (userSearchField != null) {
            userSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == null || newVal.isEmpty()) {
                    usersTable.setItems(usersData);
                } else {
                    ObservableList<UserDTO> filtered = FXCollections.observableArrayList();
                    String searchText = newVal.toLowerCase();
                    for (UserDTO user : usersData) {
                        if (user.email.toLowerCase().contains(searchText) ||
                                (user.name    != null && user.name.toLowerCase().contains(searchText)) ||
                                (user.surname != null && user.surname.toLowerCase().contains(searchText))) {
                            filtered.add(user);
                        }
                    }
                    usersTable.setItems(filtered);
                }
            });
        }

        if (orderStatusFilter != null) {
            orderStatusFilter.setItems(FXCollections.observableArrayList("Все", "Ожидает", "Выполнен", "Отменен"));
            orderStatusFilter.setValue("Все");
            orderStatusFilter.valueProperty().addListener((obs, oldVal, newVal) -> {
                if ("Все".equals(newVal)) {
                    ordersTable.setItems(ordersData);
                } else {
                    // Маппинг русских фильтров → английских значений из БД
                    String dbStatus;
                    switch (newVal) {
                        case "Ожидает": dbStatus = "pending"; break;
                        case "Выполнен": dbStatus = "completed"; break;
                        case "Отменен": dbStatus = "cancelled"; break;
                        default: dbStatus = newVal; break;
                    }
                    ObservableList<OrderDTO> filtered = FXCollections.observableArrayList();
                    for (OrderDTO order : ordersData) {
                        if (dbStatus.equalsIgnoreCase(order.status)) {
                            filtered.add(order);
                        }
                    }
                    ordersTable.setItems(filtered);
                }
            });
        }
    }

    // ============================================
    // ✅ Загрузка всех данных
    // ============================================
    private void loadAdminData() {
        new Thread(() -> {
            try {
                loadProducts();
                loadUsers();
                loadOrders();
                loadPromoCodes();
                Platform.runLater(() -> {
                    if (refreshService != null) refreshService.start();
                });
            } catch (Exception e) {
                System.err.println("❌ Ошибка загрузки данных: " + e.getMessage());
            }
        }).start();
    }

    // ============================================
    // ✅ ТОВАРЫ
    // ============================================

    private void loadProducts() {
        try {
            List<ProductDTO> products = AdminRepository.getAllProducts();
            Platform.runLater(() -> {
                if (productsTable != null) {
                    if (!productsTableSetup) {
                        setupProductsTable();
                        productsTableSetup = true;
                    }
                    productsData.clear();
                    productsData.addAll(products);
                    productsTable.setItems(productsData);
                }
            });
        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки товаров: " + e.getMessage());
        }
    }

    private void setupProductsTable() {
        productsTable.getColumns().clear();

        TableColumn<ProductDTO, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().id).asObject());
        idCol.setPrefWidth(50);

        TableColumn<ProductDTO, String> nameCol = new TableColumn<>("Название");
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().name));
        nameCol.setPrefWidth(200);

        TableColumn<ProductDTO, String> categoryCol = new TableColumn<>("Категория");
        categoryCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().category));
        categoryCol.setPrefWidth(130);

        TableColumn<ProductDTO, Double> priceCol = new TableColumn<>("Цена (₽)");
        priceCol.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().price).asObject());
        priceCol.setPrefWidth(100);

        TableColumn<ProductDTO, Integer> stockCol = new TableColumn<>("Склад");
        stockCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().stock).asObject());
        stockCol.setPrefWidth(70);

        TableColumn<ProductDTO, Void> imageCol = new TableColumn<>("Фото");
        imageCol.setCellFactory(col -> new TableCell<>() {
            private final Button uploadBtn = new Button("📷");
            {
                uploadBtn.setStyle("-fx-font-size: 12px; -fx-padding: 4px 8px;");
                uploadBtn.setOnAction(e -> {
                    if (getIndex() >= 0) uploadProductImage(getTableView().getItems().get(getIndex()));
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : uploadBtn);
            }
        });
        imageCol.setPrefWidth(60);

        TableColumn<ProductDTO, Void> actionCol = new TableColumn<>("Действия");
        actionCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }

                ProductDTO product = getTableView().getItems().get(getIndex());
                HBox actions = new HBox(5);
                actions.setAlignment(Pos.CENTER);

                Button editBtn = new Button("Редактировать");
                editBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white;" +
                        " -fx-padding: 4px 8px; -fx-font-size: 12px;");
                editBtn.setOnAction(e -> {
                    if (getIndex() >= 0) showEditProductDialog(getTableView().getItems().get(getIndex()));
                });

                Button deleteBtn = new Button("🗑");
                deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white;" +
                        " -fx-padding: 4px 8px; -fx-font-size: 12px;");
                deleteBtn.setOnAction(e -> {
                    if (getIndex() >= 0) deleteProduct(getTableView().getItems().get(getIndex()));
                });

                actions.getChildren().addAll(editBtn, deleteBtn);
                setGraphic(actions);
            }
        });
        actionCol.setPrefWidth(80);

        productsTable.getColumns().addAll(idCol, nameCol, categoryCol, priceCol, stockCol, imageCol, actionCol);
        System.out.println("✅ Таблица товаров настроена");
    }

    private void showAddProductDialog() {
        Dialog<ProductDTO> dialog = new Dialog<>();
        dialog.setTitle("Добавить товар");
        dialog.setHeaderText("📦 Введите данные товара");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField     = new TextField(); nameField.setPromptText("Название");
        TextField priceField    = new TextField(); priceField.setPromptText("Цена (₽)");
        TextField stockField    = new TextField(); stockField.setPromptText("Количество");
        TextField categoryField = new TextField(); categoryField.setPromptText("Категория");

        grid.add(new Label("Название:"),  0, 0); grid.add(nameField,     1, 0);
        grid.add(new Label("Цена (₽):"),  0, 1); grid.add(priceField,    1, 1);
        grid.add(new Label("Склад:"),     0, 2); grid.add(stockField,    1, 2);
        grid.add(new Label("Категория:"), 0, 3); grid.add(categoryField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    String name     = nameField.getText().trim();
                    double price    = Double.parseDouble(priceField.getText());
                    int    stock    = Integer.parseInt(stockField.getText());
                    String category = categoryField.getText().trim();
                    if (name.isEmpty() || category.isEmpty()) {
                        showAlert("Ошибка", "Заполните поля!");
                        return null;
                    }
                    return new ProductDTO(0, name, "", price, stock, "", category, "");
                } catch (Exception e) {
                    showAlert("Ошибка", "Проверьте формат!");
                    return null;
                }
            }
            return null;
        });

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            saveProduct(result.get());
        }
    }

    private void saveProduct(ProductDTO product) {
        new Thread(() -> {
            try {
                AdminRepository.addProduct(product.name, product.description, product.price,
                        product.stock, product.category, product.manufacturer, "");
                Platform.runLater(() -> {
                    showAlert("✅ Успех", "Товар добавлен!");
                    loadProducts();
                    if (mainController != null) mainController.reloadProducts();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("❌ Ошибка", e.getMessage()));
            }
        }).start();
    }

    private void showEditProductDialog(ProductDTO product) {
        Dialog<ProductDTO> dialog = new Dialog<>();
        dialog.setTitle("Редактировать товар");
        dialog.setHeaderText("✏️ Редактирование товара #" + product.id);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField         = new TextField(product.name);
        TextField priceField        = new TextField(String.valueOf(product.price));
        TextField stockField        = new TextField(String.valueOf(product.stock));
        TextField categoryField     = new TextField(product.category);
        TextField manufacturerField = new TextField(product.manufacturer != null ? product.manufacturer : "");
        TextArea  descriptionArea   = new TextArea(product.description  != null ? product.description  : "");
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);

        grid.add(new Label("Название:"),      0, 0); grid.add(nameField,         1, 0);
        grid.add(new Label("Цена (₽):"),      0, 1); grid.add(priceField,        1, 1);
        grid.add(new Label("Склад:"),         0, 2); grid.add(stockField,        1, 2);
        grid.add(new Label("Категория:"),     0, 3); grid.add(categoryField,     1, 3);
        grid.add(new Label("Производитель:"), 0, 4); grid.add(manufacturerField, 1, 4);
        grid.add(new Label("Описание:"),      0, 5); grid.add(descriptionArea,   1, 5);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    String name         = nameField.getText().trim();
                    double price        = Double.parseDouble(priceField.getText());
                    int    stock        = Integer.parseInt(stockField.getText());
                    String category     = categoryField.getText().trim();
                    String manufacturer = manufacturerField.getText().trim();
                    String description  = descriptionArea.getText().trim();

                    if (name.isEmpty() || category.isEmpty()) {
                        showAlert("Ошибка", "Заполните обязательные поля!");
                        return null;
                    }

                    product.name         = name;
                    product.price        = price;
                    product.stock        = stock;
                    product.category     = category;
                    product.manufacturer = manufacturer;
                    product.description  = description;
                    return product;
                } catch (NumberFormatException e) {
                    showAlert("Ошибка", "Проверьте формат цены и количества!");
                    return null;
                }
            }
            return null;
        });

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            updateProduct(result.get());
        }
    }

    private void updateProduct(ProductDTO product) {
        new Thread(() -> {
            try {
                System.out.println("✏️ Обновление товара: " + product.name);
                AdminRepository.updateProduct(product.id, product.name, product.description,
                        product.price, product.stock, product.category, product.manufacturer);
                Platform.runLater(() -> {
                    showAlert("✅ Успех", "Товар обновлен!");
                    loadProducts();
                    if (mainController != null) mainController.reloadProducts();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("❌ Ошибка", "Ошибка обновления: " + e.getMessage()));
                System.err.println("❌ Ошибка обновления: " + e.getMessage());
            }
        }).start();
    }

    private void deleteProduct(ProductDTO product) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удалить?");
        confirm.setContentText("Удалить товар '" + product.name + "'?");

        if (confirm.showAndWait().get() == ButtonType.OK) {
            new Thread(() -> {
                try {
                    AdminRepository.deleteProduct(product.id);
                    Platform.runLater(() -> {
                        productsData.remove(product);
                        showAlert("✅ Успех", "Товар удален!");
                        if (mainController != null) mainController.reloadProducts();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert("❌ Ошибка", e.getMessage()));
                }
            }).start();
        }
    }

    private void uploadProductImage(ProductDTO product) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите изображение");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Изображения", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp"));

        File selectedFile = fileChooser.showOpenDialog(productsTable.getScene().getWindow());
        if (selectedFile != null) {
            new Thread(() -> {
                try {
                    String fileName = SupabaseStorageService.generateFileName(product.id, selectedFile.getName());
                    String imageUrl = SupabaseStorageService.uploadImage(selectedFile, fileName);
                    AdminRepository.updateProductImage(product.id, imageUrl);
                    Platform.runLater(() -> {
                        product.imageUrl = imageUrl;
                        loadProducts();
                        showAlert("✅ Успех", "Изображение загружено!");
                        if (mainController != null) mainController.reloadProducts();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert("❌ Ошибка", e.getMessage()));
                }
            }).start();
        }
    }

    // ============================================
    // ✅ ПОЛЬЗОВАТЕЛИ
    // ============================================

    private void loadUsers() {
        try {
            List<UserDTO> users = AdminRepository.getAllUsers();
            Platform.runLater(() -> {
                if (usersTable != null) {
                    if (!usersTableSetup) {
                        setupUsersTable();
                        usersTableSetup = true;
                    }
                    usersData.clear();
                    usersData.addAll(users);
                    usersTable.setItems(usersData);
                }
            });
        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки пользователей: " + e.getMessage());
        }
    }

    private void setupUsersTable() {
        usersTable.getColumns().clear();

        TableColumn<UserDTO, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().email));
        emailCol.setPrefWidth(200);

        TableColumn<UserDTO, String> nameCol = new TableColumn<>("Имя");
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().name != null ? cd.getValue().name : ""));
        nameCol.setPrefWidth(120);

        TableColumn<UserDTO, String> surnameCol = new TableColumn<>("Фамилия");
        surnameCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().surname != null ? cd.getValue().surname : ""));
        surnameCol.setPrefWidth(120);

        TableColumn<UserDTO, String> statusCol = new TableColumn<>("Статус");
        statusCol.setCellValueFactory(cd -> {
            UserDTO user = cd.getValue();
            String status = user.is_admin   ? "Админ"
                    : user.is_blocked       ? "🔒 Заблокирован"
                    : user.is_manager       ? "Менеджер"
                    : "✅ Активен";
            return new SimpleStringProperty(status);
        });
        statusCol.setPrefWidth(130);

        TableColumn<UserDTO, Void> actionCol = new TableColumn<>("Действия");
        actionCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }

                UserDTO user = getTableView().getItems().get(getIndex());
                HBox actions = new HBox(5);
                actions.setAlignment(Pos.CENTER);

                Button blockBtn = new Button(user.is_blocked ? "🔓 Разбл." : "🔒 Блок.");
                blockBtn.setStyle(user.is_blocked
                        ? "-fx-background-color: #10b981; -fx-text-fill: white; -fx-padding: 6px 12px; -fx-font-size: 11px;"
                        : "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-padding: 6px 12px; -fx-font-size: 11px;");
                blockBtn.setOnAction(e -> {
                    if (user.is_blocked) unblockUser(user.id, user.email);
                    else                 blockUser(user.id, user.email);
                });

                actions.getChildren().add(blockBtn);
                setGraphic(actions);
            }
        });
        actionCol.setPrefWidth(100);

        usersTable.getColumns().addAll(emailCol, nameCol, surnameCol, statusCol, actionCol);
    }

    private void blockUser(String userId, String email) {
        new Thread(() -> {
            try {
                AdminRepository.blockUser(userId);
                Platform.runLater(() -> {
                    showAlert("✅ Успешно", "Пользователь заблокирован!");
                    loadUsers();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("❌ Ошибка", e.getMessage()));
            }
        }).start();
    }

    private void unblockUser(String userId, String email) {
        new Thread(() -> {
            try {
                AdminRepository.unblockUser(userId);
                Platform.runLater(() -> {
                    showAlert("✅ Успешно", "Пользователь разблокирован!");
                    loadUsers();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("❌ Ошибка", e.getMessage()));
            }
        }).start();
    }

    // ============================================
    // ✅ ЗАКАЗЫ
    // ============================================

    private void loadOrders() {
        try {
            List<OrderDTO> orders = AdminRepository.getAllOrders();
            Platform.runLater(() -> {
                if (ordersTable != null) {
                    if (!ordersTableSetup) {
                        setupOrdersTable();
                        ordersTableSetup = true;
                    }
                    ordersData.clear();
                    ordersData.addAll(orders);
                    ordersTable.setItems(ordersData);
                }
            });
        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки заказов: " + e.getMessage());
        }
    }

    private void setupOrdersTable() {
        ordersTable.getColumns().clear();

        TableColumn<OrderDTO, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().orderId).asObject());
        idCol.setPrefWidth(60);

        TableColumn<OrderDTO, String> userCol = new TableColumn<>("Email");
        userCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().userId != null ? cd.getValue().userId : "-"));
        userCol.setPrefWidth(150);

        TableColumn<OrderDTO, Double> sumCol = new TableColumn<>("Сумма");
        sumCol.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().totalAmount).asObject());
        sumCol.setPrefWidth(100);

        TableColumn<OrderDTO, String> statusCol = new TableColumn<>("Статус");
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty(getOrderStatus(cd.getValue().status)));
        statusCol.setPrefWidth(100);

        // ✅ Дата в читаемом формате dd.MM.yyyy HH:mm
        TableColumn<OrderDTO, String> dateCol = new TableColumn<>("Дата");
        dateCol.setCellValueFactory(cd -> new SimpleStringProperty(formatDate(cd.getValue().orderDate)));
        dateCol.setPrefWidth(150);

        TableColumn<OrderDTO, Void> actionCol = new TableColumn<>("Действия");
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button completeBtn = new Button("✅");
            private final Button pendingBtn  = new Button("⏳");
            private final Button cancelBtn   = new Button("❌");
            private final HBox actions = new HBox(5, completeBtn, pendingBtn, cancelBtn);
            {
                actions.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }

                OrderDTO order = getTableView().getItems().get(getIndex());

                // ✅ Кнопка "Завершён"
                completeBtn.setDisable("completed".equalsIgnoreCase(order.status));
                completeBtn.setStyle("completed".equalsIgnoreCase(order.status)
                        ? "-fx-background-color: #9ca3af; -fx-padding: 6px 10px;"
                        : "-fx-background-color: #10b981; -fx-text-fill: white; -fx-padding: 6px 10px; -fx-font-size: 11px;");
                completeBtn.setOnAction(e -> updateOrderStatus(order.orderId, "completed"));

                // ⏳ Кнопка "В ожидании"
                pendingBtn.setDisable("pending".equalsIgnoreCase(order.status));
                pendingBtn.setStyle("pending".equalsIgnoreCase(order.status)
                        ? "-fx-background-color: #9ca3af; -fx-padding: 6px 10px;"
                        : "-fx-background-color: #fbbf24; -fx-text-fill: #000; -fx-padding: 6px 10px; -fx-font-size: 11px;");
                pendingBtn.setOnAction(e -> updateOrderStatus(order.orderId, "pending"));

                // ❌ Кнопка "Отменён"
                cancelBtn.setDisable("cancelled".equalsIgnoreCase(order.status));
                cancelBtn.setStyle("cancelled".equalsIgnoreCase(order.status)
                        ? "-fx-background-color: #9ca3af; -fx-padding: 6px 10px;"
                        : "-fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 6px 10px; -fx-font-size: 11px;");
                cancelBtn.setOnAction(e -> updateOrderStatus(order.orderId, "cancelled"));

                setGraphic(actions);
            }
        });
        actionCol.setPrefWidth(120);

        ordersTable.getColumns().addAll(idCol, userCol, sumCol, statusCol, dateCol, actionCol);
    }

    private void updateOrderStatus(int orderId, String newStatus) {
        new Thread(() -> {
            try {
                AdminRepository.updateOrderStatusAdmin(orderId, newStatus);
                Platform.runLater(() -> {
                    showAlert("✅ Успешно", "Статус заказа обновлен!");
                    loadOrders();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("❌ Ошибка", e.getMessage()));
            }
        }).start();
    }

    private String getOrderStatus(String status) {
        if (status == null) return "❓ Неизвестно";
        switch (status.toLowerCase()) {
            case "pending":   return "⏳ В ожидании";
            case "completed": return "✅ Завершен";
            case "cancelled": return "❌ Отменен";
            default:          return "❓ Неизвестно";
        }
    }

    // ============================================
    // ✅ ПРОМОКОДЫ
    // ============================================

    private void loadPromoCodes() {
        try {
            List<PromoCodeDTO> promoCodes = AdminRepository.getAllPromoCodes();
            Platform.runLater(() -> {
                if (promoCodesTable != null) {
                    if (!promoCodesTableSetup) {
                        setupPromoCodesTable();
                        promoCodesTableSetup = true;
                    }
                    promoCodesData.clear();
                    promoCodesData.addAll(promoCodes);
                    promoCodesTable.setItems(promoCodesData);
                }
            });
        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки промокодов: " + e.getMessage());
        }
    }

    private void setupPromoCodesTable() {
        promoCodesTable.getColumns().clear();

        TableColumn<PromoCodeDTO, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().id).asObject());
        idCol.setPrefWidth(50);

        TableColumn<PromoCodeDTO, String> codeCol = new TableColumn<>("Код");
        codeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().code));
        codeCol.setPrefWidth(150);

        TableColumn<PromoCodeDTO, Double> discountCol = new TableColumn<>("Скидка %");
        discountCol.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().discountPercent).asObject());
        discountCol.setPrefWidth(100);

        TableColumn<PromoCodeDTO, Integer> maxUsesCol = new TableColumn<>("Лимит");
        maxUsesCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().maxUses).asObject());
        maxUsesCol.setPrefWidth(80);

        TableColumn<PromoCodeDTO, Integer> usedCol = new TableColumn<>("Использовано");
        usedCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().usedCount).asObject());
        usedCol.setPrefWidth(120);

        TableColumn<PromoCodeDTO, String> expiryCol = new TableColumn<>("Срок действия");
        expiryCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().expiryDate));
        expiryCol.setPrefWidth(120);

        TableColumn<PromoCodeDTO, String> statusCol = new TableColumn<>("Статус");
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().isActive ? "✅ Активен" : "❌ Неактивен"));
        statusCol.setPrefWidth(100);

        TableColumn<PromoCodeDTO, Void> actionCol = new TableColumn<>("Действия");
        actionCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }

                PromoCodeDTO promo = getTableView().getItems().get(getIndex());
                HBox actions = new HBox(5);
                actions.setAlignment(Pos.CENTER);

                Button deleteBtn = new Button("🗑");
                deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white;" +
                        " -fx-padding: 6px 10px; -fx-font-size: 11px;");
                deleteBtn.setOnAction(e -> deletePromoCode(promo.id));

                actions.getChildren().add(deleteBtn);
                setGraphic(actions);
            }
        });
        actionCol.setPrefWidth(100);

        promoCodesTable.getColumns().addAll(idCol, codeCol, discountCol, maxUsesCol, usedCol, expiryCol, statusCol, actionCol);
        System.out.println("✅ Таблица промокодов настроена");
    }

    private void showAddPromoCodeDialog() {
        Dialog<PromoCodeDTO> dialog = new Dialog<>();
        dialog.setTitle("Создать промокод");
        dialog.setHeaderText("➕ Новый промокод");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField  codeField     = new TextField();    codeField.setPromptText("Например: SALE2025");
        TextField  discountField = new TextField();    discountField.setPromptText("От 0 до 100");
        TextField  maxUsesField  = new TextField();    maxUsesField.setPromptText("Например: 100");
        DatePicker expiryPicker  = new DatePicker();   expiryPicker.setValue(java.time.LocalDate.now().plusMonths(1));

        grid.add(new Label("Код промокода:"),        0, 0); grid.add(codeField,     1, 0);
        grid.add(new Label("Скидка (%):"),           0, 1); grid.add(discountField, 1, 1);
        grid.add(new Label("Лимит использований:"),  0, 2); grid.add(maxUsesField,  1, 2);
        grid.add(new Label("Срок действия:"),        0, 3); grid.add(expiryPicker,  1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    String code      = codeField.getText().trim().toUpperCase();
                    double discount  = Double.parseDouble(discountField.getText());
                    int    maxUses   = Integer.parseInt(maxUsesField.getText());
                    String expiryDate = expiryPicker.getValue().toString();

                    if (code.isEmpty() || code.length() > 50) {
                        showAlert("Ошибка", "Код промокода должен быть от 1 до 50 символов!"); return null;
                    }
                    if (discount < 0 || discount > 100) {
                        showAlert("Ошибка", "Скидка должна быть от 0 до 100%!"); return null;
                    }
                    if (maxUses < 1) {
                        showAlert("Ошибка", "Лимит должен быть не меньше 1!"); return null;
                    }

                    return new PromoCodeDTO(0, code, discount, maxUses, 0, expiryDate, true);

                } catch (NumberFormatException e) {
                    showAlert("Ошибка", "Проверьте формат чисел!"); return null;
                }
            }
            return null;
        });

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            createPromoCode(result.get());
        }
    }

    private void createPromoCode(PromoCodeDTO dto) {
        new Thread(() -> {
            try {
                System.out.println("➕ Создание промокода: " + dto.code);
                AdminRepository.createPromoCode(dto);
                Platform.runLater(() -> {
                    showAlert("✅ Успех", "Промокод создан!");
                    loadPromoCodes();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("❌ Ошибка", "Ошибка создания промокода: " + e.getMessage()));
                System.err.println("❌ Ошибка создания промокода: " + e.getMessage());
            }
        }).start();
    }

    private void deletePromoCode(int promoId) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Удаление промокода");
        confirmAlert.setHeaderText("Вы уверены?");
        confirmAlert.setContentText("Промокод будет деактивирован.");

        var result = confirmAlert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        new Thread(() -> {
            try {
                AdminRepository.deletePromoCode(promoId);
                Platform.runLater(() -> {
                    showAlert("✅ Успешно", "Промокод деактивирован!");
                    loadPromoCodes();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("❌ Ошибка", e.getMessage()));
            }
        }).start();
    }

    // ============================================
    // ✅ ПУБЛИЧНЫЕ МЕТОДЫ ДЛЯ ОБНОВЛЕНИЯ
    // ============================================

    public void refreshProductsList()   { loadProducts(); }
    public void refreshUsersList()      { loadUsers(); }
    public void refreshOrdersList()     { loadOrders(); }
    public void refreshPromoCodesList() { loadPromoCodes(); }

    public void stopRefreshService() {
        if (refreshService != null) refreshService.stop();
    }

    // ============================================
    // ✅ УТИЛИТЫ
    // ============================================

    /** ✅ Конвертирует ISO дату БД → читаемый формат dd.MM.yyyy HH:mm */
    private String formatDate(String rawDate) {
        if (rawDate == null || rawDate.isEmpty()) return "-";
        try {
            OffsetDateTime odt = OffsetDateTime.parse(rawDate);
            return odt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        } catch (Exception e1) {
            try {
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(rawDate.substring(0, 19));
                return ldt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            } catch (Exception e2) {
                return rawDate;
            }
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}