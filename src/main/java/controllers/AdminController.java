package controllers;

import com.example.authapp.dto.ProductDTO;
import com.example.authapp.dto.UserDTO;
import com.example.authapp.dto.OrderDTO;
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
import java.util.List;
import java.util.ResourceBundle;

public class AdminController implements Initializable {

    @FXML private TabPane adminTabs;
    @FXML private Button exitAdminBtn;
    @FXML private Button addProductBtn;
    @FXML private TableView<ProductDTO> productsTable;
    @FXML private TableView<UserDTO> usersTable;
    @FXML private TextField userSearchField;
    @FXML private TableView<OrderDTO> ordersTable;
    @FXML private ComboBox<String> orderStatusFilter;

    private MainController mainController;
    private CabinetController cabinetController;
    private AdminRefreshService refreshService;

    private ObservableList<ProductDTO> productsData = FXCollections.observableArrayList();
    private ObservableList<UserDTO> usersData = FXCollections.observableArrayList();
    private ObservableList<OrderDTO> ordersData = FXCollections.observableArrayList();

    private boolean productsTableSetup = false;
    private boolean usersTableSetup = false;
    private boolean ordersTableSetup = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        try {
            setupUI();
            loadAdminData();
            refreshService = new AdminRefreshService(this);
        } catch (Exception e) {
            System.err.println("❌ Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setMainController(MainController controller) {
        this.mainController = controller;
    }

    public void setCabinetController(CabinetController cabinet) {
        this.cabinetController = cabinet;
    }

    private void setupUI() {
        // ВЫХОД
        if (exitAdminBtn != null) {
            exitAdminBtn.setOnAction(e -> {
                if (refreshService != null) refreshService.stop();
                if (mainController != null) mainController.showMainContent();
            });
        }

        // КНОПКА ДОБАВИТЬ ТОВАР
        if (addProductBtn != null) {
            addProductBtn.setOnAction(e -> showAddProductDialog());
        }

        // ПОИСК ПОЛЬЗОВАТЕЛЕЙ
        if (userSearchField != null) {
            userSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == null || newVal.isEmpty()) {
                    usersTable.setItems(usersData);
                } else {
                    ObservableList<UserDTO> filtered = FXCollections.observableArrayList();
                    String searchText = newVal.toLowerCase();
                    for (UserDTO user : usersData) {
                        if (user.email.toLowerCase().contains(searchText) ||
                                (user.name != null && user.name.toLowerCase().contains(searchText)) ||
                                (user.surname != null && user.surname.toLowerCase().contains(searchText))) {
                            filtered.add(user);
                        }
                    }
                    usersTable.setItems(filtered);
                }
            });
        }

        // ФИЛЬТР ЗАКАЗОВ
        if (orderStatusFilter != null) {
            orderStatusFilter.setItems(FXCollections.observableArrayList("Все", "Ожидает", "Выполнен", "Отменен"));
            orderStatusFilter.setValue("Все");
            orderStatusFilter.valueProperty().addListener((obs, oldVal, newVal) -> {
                if ("Все".equals(newVal)) {
                    ordersTable.setItems(ordersData);
                } else {
                    ObservableList<OrderDTO> filtered = FXCollections.observableArrayList();
                    for (OrderDTO order : ordersData) {
                        if (newVal.equalsIgnoreCase(order.status)) {
                            filtered.add(order);
                        }
                    }
                    ordersTable.setItems(filtered);
                }
            });
        }
    }

    private void loadAdminData() {
        new Thread(() -> {
            try {
                loadProducts();
                loadUsers();
                loadOrders();
                Platform.runLater(() -> {
                    if (refreshService != null) refreshService.start();
                });
            } catch (Exception e) {
                System.err.println("❌ Ошибка загрузки: " + e.getMessage());
            }
        }).start();
    }

    // ТОВАРЫ

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

    private void showEditProductDialog(ProductDTO product) {
        Dialog<ProductDTO> dialog = new Dialog<>();
        dialog.setTitle("Редактировать товар");
        dialog.setHeaderText("✏️ Редактирование товара #" + product.id);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        nameField.setText(product.name);

        TextField priceField = new TextField();
        priceField.setText(String.valueOf(product.price));

        TextField stockField = new TextField();
        stockField.setText(String.valueOf(product.stock));

        TextField categoryField = new TextField();
        categoryField.setText(product.category);

        TextField manufacturerField = new TextField();
        manufacturerField.setText(product.manufacturer != null ? product.manufacturer : "");

        TextArea descriptionArea = new TextArea();
        descriptionArea.setText(product.description != null ? product.description : "");
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);

        grid.add(new Label("Название:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Цена (₽):"), 0, 1);
        grid.add(priceField, 1, 1);
        grid.add(new Label("Склад:"), 0, 2);
        grid.add(stockField, 1, 2);
        grid.add(new Label("Категория:"), 0, 3);
        grid.add(categoryField, 1, 3);
        grid.add(new Label("Производитель:"), 0, 4);
        grid.add(manufacturerField, 1, 4);
        grid.add(new Label("Описание:"), 0, 5);
        grid.add(descriptionArea, 1, 5);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    String name = nameField.getText().trim();
                    double price = Double.parseDouble(priceField.getText());
                    int stock = Integer.parseInt(stockField.getText());
                    String category = categoryField.getText().trim();
                    String manufacturer = manufacturerField.getText().trim();
                    String description = descriptionArea.getText().trim();

                    if (name.isEmpty() || category.isEmpty()) {
                        showAlert("Ошибка", "Заполните обязательные поля!");
                        return null;
                    }

                    product.name = name;
                    product.price = price;
                    product.stock = stock;
                    product.category = category;
                    product.manufacturer = manufacturer;
                    product.description = description;

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

    /**
     * Обновляет товар в БД
     */
    private void updateProduct(ProductDTO product) {
        new Thread(() -> {
            try {
                System.out.println("✏️ Обновление товара: " + product.name);

                AdminRepository.updateProduct(
                        product.id,
                        product.name,
                        product.description,
                        product.price,
                        product.stock,
                        product.category,
                        product.manufacturer
                );

                Platform.runLater(() -> {
                    showAlert("✅ Успех", "Товар обновлен!");
                    loadProducts();
                    if (mainController != null) {
                        mainController.reloadProducts();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("❌ Ошибка", "Ошибка обновления: " + e.getMessage()));
                System.err.println("❌ Ошибка обновления: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Обновляем setupProductsTable для добавления кнопки редактирования
     */
    private void setupProductsTable() {
        productsTable.getColumns().clear();

        TableColumn<ProductDTO, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().id).asObject());
        idCol.setPrefWidth(50);

        TableColumn<ProductDTO, String> nameCol = new TableColumn<>("Название");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name));
        nameCol.setPrefWidth(200);

        TableColumn<ProductDTO, String> categoryCol = new TableColumn<>("Категория");
        categoryCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().category));
        categoryCol.setPrefWidth(130);

        TableColumn<ProductDTO, Double> priceCol = new TableColumn<>("Цена (₽)");
        priceCol.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().price).asObject());
        priceCol.setPrefWidth(100);

        TableColumn<ProductDTO, Integer> stockCol = new TableColumn<>("Склад");
        stockCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().stock).asObject());
        stockCol.setPrefWidth(70);

        TableColumn<ProductDTO, Void> imageCol = new TableColumn<>("Фото");
        imageCol.setCellFactory(col -> new TableCell<ProductDTO, Void>() {
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

        // КОЛОНКА ДЕЙСТВИЙ (редактирование + удаление)
        TableColumn<ProductDTO, Void> actionCol = new TableColumn<>("Действия");
        actionCol.setCellFactory(col -> new TableCell<ProductDTO, Void>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }

                ProductDTO product = getTableView().getItems().get(getIndex());
                HBox actions = new HBox(5);
                actions.setAlignment(Pos.CENTER);

                // КНОПКА РЕДАКТИРОВАНИЯ
                Button editBtn = new Button("Редактировать");
                editBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 4px 8px; -fx-font-size: 12px;");
                editBtn.setOnAction(e -> {
                    if (getIndex() >= 0) showEditProductDialog(getTableView().getItems().get(getIndex()));
                });

                // КНОПКА УДАЛЕНИЯ
                Button deleteBtn = new Button("🗑");
                deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 4px 8px; -fx-font-size: 12px;");
                deleteBtn.setOnAction(e -> {
                    if (getIndex() >= 0) deleteProduct(getTableView().getItems().get(getIndex()));
                });

                actions.getChildren().addAll(editBtn, deleteBtn);
                setGraphic(actions);
            }
        });
        actionCol.setPrefWidth(80);

        productsTable.getColumns().addAll(idCol, nameCol, categoryCol, priceCol, stockCol, imageCol, actionCol);
        System.out.println("✅ Таблица товаров настроена с редактированием");
    }

    private void showAddProductDialog() {
        Dialog<ProductDTO> dialog = new Dialog<>();
        dialog.setTitle("Добавить товар");
        dialog.setHeaderText("📦 Введите данные товара");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("Название");
        TextField priceField = new TextField();
        priceField.setPromptText("Цена (₽)");
        TextField stockField = new TextField();
        stockField.setPromptText("Количество");
        TextField categoryField = new TextField();
        categoryField.setPromptText("Категория");

        grid.add(new Label("Название:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Цена (₽):"), 0, 1);
        grid.add(priceField, 1, 1);
        grid.add(new Label("Склад:"), 0, 2);
        grid.add(stockField, 1, 2);
        grid.add(new Label("Категория:"), 0, 3);
        grid.add(categoryField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    String name = nameField.getText().trim();
                    double price = Double.parseDouble(priceField.getText());
                    int stock = Integer.parseInt(stockField.getText());
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
                AdminRepository.addProduct(product.name, product.description, product.price, product.stock, product.category, product.manufacturer, "");
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
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Изображения", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp")
        );

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

    // ============ ПОЛЬЗОВАТЕЛИ ============

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
        emailCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().email));
        emailCol.setPrefWidth(200);

        TableColumn<UserDTO, String> nameCol = new TableColumn<>("Имя");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name != null ? cellData.getValue().name : ""));
        nameCol.setPrefWidth(120);

        TableColumn<UserDTO, String> surnameCol = new TableColumn<>("Фамилия");
        surnameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().surname != null ? cellData.getValue().surname : ""));
        surnameCol.setPrefWidth(120);

        TableColumn<UserDTO, String> statusCol = new TableColumn<>("Статус");
        statusCol.setCellValueFactory(cellData -> {
            UserDTO user = cellData.getValue();
            String status = user.is_admin ? "👑 Админ" : (user.is_blocked ? "🔒 Блокирован" : "✅ Активен");
            return new SimpleStringProperty(status);
        });
        statusCol.setPrefWidth(120);

        TableColumn<UserDTO, Void> actionCol = new TableColumn<>("Действия");
        actionCol.setCellFactory(col -> new TableCell<UserDTO, Void>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }

                UserDTO user = getTableView().getItems().get(getIndex());
                HBox actions = new HBox(5);
                actions.setAlignment(Pos.CENTER);

                Button blockBtn = new Button(user.is_blocked ? "🔓 Разбл." : "🔒 Блок.");
                blockBtn.setStyle(user.is_blocked ?
                        "-fx-background-color: #10b981; -fx-text-fill: white; -fx-padding: 6px 12px; -fx-font-size: 11px;" :
                        "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-padding: 6px 12px; -fx-font-size: 11px;");

                blockBtn.setOnAction(e -> {
                    if (user.is_blocked) {
                        unblockUser(user.id, user.email);
                    } else {
                        blockUser(user.id, user.email);
                    }
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

    // ============ ЗАКАЗЫ ============

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
        idCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().orderId).asObject());
        idCol.setPrefWidth(60);

        TableColumn<OrderDTO, String> userCol = new TableColumn<>("Email");
        userCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().userId != null ? cellData.getValue().userId : "-"));
        userCol.setPrefWidth(150);

        TableColumn<OrderDTO, Double> sumCol = new TableColumn<>("Сумма");
        sumCol.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().totalAmount).asObject());
        sumCol.setPrefWidth(100);

        TableColumn<OrderDTO, String> statusCol = new TableColumn<>("Статус");
        statusCol.setCellValueFactory(cellData -> new SimpleStringProperty(getOrderStatus(cellData.getValue().status)));
        statusCol.setPrefWidth(100);

        TableColumn<OrderDTO, String> dateCol = new TableColumn<>("Дата");
        dateCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().orderDate != null ? cellData.getValue().orderDate : "-"));
        dateCol.setPrefWidth(150);

        TableColumn<OrderDTO, Void> actionCol = new TableColumn<>("Действия");
        actionCol.setCellFactory(col -> new TableCell<OrderDTO, Void>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }

                OrderDTO order = getTableView().getItems().get(getIndex());
                HBox actions = new HBox(5);
                actions.setAlignment(Pos.CENTER);

                Button completeBtn = new Button("✅");
                completeBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-padding: 6px 10px; -fx-font-size: 11px;");
                if ("completed".equalsIgnoreCase(order.status)) {
                    completeBtn.setDisable(true);
                    completeBtn.setStyle("-fx-background-color: #9ca3af; -fx-padding: 6px 10px;");
                }
                completeBtn.setOnAction(e -> updateOrderStatus(order.orderId, "completed"));

                Button pendingBtn = new Button("⏳");
                pendingBtn.setStyle("-fx-background-color: #fbbf24; -fx-text-fill: #000; -fx-padding: 6px 10px; -fx-font-size: 11px;");
                if ("pending".equalsIgnoreCase(order.status)) {
                    pendingBtn.setDisable(true);
                    pendingBtn.setStyle("-fx-background-color: #9ca3af; -fx-padding: 6px 10px;");
                }
                pendingBtn.setOnAction(e -> updateOrderStatus(order.orderId, "pending"));

                Button cancelBtn = new Button("❌");
                cancelBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 6px 10px; -fx-font-size: 11px;");
                if ("cancelled".equalsIgnoreCase(order.status)) {
                    cancelBtn.setDisable(true);
                    cancelBtn.setStyle("-fx-background-color: #9ca3af; -fx-padding: 6px 10px;");
                }
                cancelBtn.setOnAction(e -> updateOrderStatus(order.orderId, "cancelled"));

                actions.getChildren().addAll(completeBtn, pendingBtn, cancelBtn);
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
            case "pending": return "⏳ В ожидании";
            case "completed": return "✅ Завершен";
            case "cancelled": return "❌ Отменен";
            default: return "❓ Неизвестно";
        }
    }

    // ============ ПУБЛИЧНЫЕ МЕТОДЫ ============

    public void refreshProductsList() {
        loadProducts();
    }

    public void refreshUsersList() {
        loadUsers();
    }

    public void refreshOrdersList() {
        loadOrders();
    }

    public void stopRefreshService() {
        if (refreshService != null) refreshService.stop();
    }

    // ============ УТИЛИТЫ ============

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