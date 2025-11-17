package controllers;

import com.example.authapp.dto.ProductDTO;
import com.example.authapp.dto.UserDTO;
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
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * ✅ AdminController - ПЕРЕПИСАННЫЙ ПОЛНОСТЬЮ
 * Управление товарами, пользователями и заказами в админ-панели
 */
public class AdminController implements Initializable {

    // ============ FXML ЭЛЕМЕНТЫ ============
    @FXML private TabPane adminTabs;
    @FXML private Button exitAdminBtn;
    @FXML private TableView<ProductDTO> productsTable;
    @FXML private TableView<UserDTO> usersTable;
    @FXML private TableView ordersTable;

    // ============ ПЕРЕМЕННЫЕ ============
    private MainController mainController;
    private CabinetController cabinetController;
    private AdminRefreshService refreshService;
    private File selectedImageFile = null;

    // ✅ ObservableList для автоматического обновления таблиц
    private ObservableList<ProductDTO> productsData = FXCollections.observableArrayList();
    private ObservableList<UserDTO> usersData = FXCollections.observableArrayList();

    // ✅ Флаги чтобы таблицы не переинициализировались каждый раз
    private boolean productsTableSetup = false;
    private boolean usersTableSetup = false;

    // ============ ИНИЦИАЛИЗАЦИЯ ============

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        System.out.println("📋 Инициализация AdminController...");

        try {
            setupUI();
            loadAdminData();

            // ✅ ИНИЦИАЛИЗИРУЕМ СЕРВИС СИНХРОНИЗАЦИИ
            refreshService = new AdminRefreshService(this);

            System.out.println("✅ AdminController полностью инициализирован");
        } catch (Exception e) {
            System.err.println("❌ Ошибка инициализации AdminController: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Передаём MainController для возврата на главную
     */
    public void setMainController(MainController controller) {
        this.mainController = controller;
        System.out.println("✅ MainController передан в AdminController");
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Передаём CabinetController
     */
    public void setCabinetController(CabinetController cabinet) {
        this.cabinetController = cabinet;
        System.out.println("✅ CabinetController передан в AdminController");
    }

    // ============ НАСТРОЙКА UI ============

    private void setupUI() {
        System.out.println("⚙️ Настройка UI админ-панели...");

        // ✅ КНОПКА ВЫХОДА
        if (exitAdminBtn != null) {
            exitAdminBtn.setOnAction(e -> {
                System.out.println("🚪 Выход из админ-панели");

                // Останавливаем сервис
                if (refreshService != null) {
                    refreshService.stop();
                    System.out.println("✅ Сервис синхронизации остановлен");
                }

                // ✅ ВОЗВРАЩАЕМСЯ НА ГЛАВНУЮ
                if (mainController != null) {
                    System.out.println("🏠 Возврат на главную страницу");
                    mainController.showMainContent();
                } else {
                    System.err.println("⚠️ mainController is null!");
                }
            });
            System.out.println("✅ Кнопка выхода установлена");
        }

        // ✅ КНОПКА ДОБАВИТЬ ТОВАР
        if (adminTabs != null && adminTabs.getTabs().size() > 0) {
            Tab productsTab = adminTabs.getTabs().get(0);
            Node content = productsTab.getContent();
            if (content instanceof VBox) {
                VBox productsContent = (VBox) content;
                for (Node node : productsContent.getChildren()) {
                    if (node instanceof HBox) {
                        HBox hbox = (HBox) node;
                        for (Node btn : hbox.getChildren()) {
                            if (btn instanceof Button) {
                                Button button = (Button) btn;
                                if (button.getText().contains("Добавить")) {
                                    button.setOnAction(e -> showAddProductDialog());
                                    System.out.println("✅ Кнопка 'Добавить товар' установлена");
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("✅ UI админ-панели настроена");
    }

    // ============ ЗАГРУЗКА ДАННЫХ ============

    /**
     * Загружает все данные из БД
     */
    private void loadAdminData() {
        new Thread(() -> {
            try {
                System.out.println("📦 Загрузка данных админ-панели...");
                loadProducts();
                loadUsers();

                // Запускаем сервис синхронизации
                Platform.runLater(() -> {
                    if (refreshService != null) {
                        refreshService.start();
                        System.out.println("✅ Сервис синхронизации запущен");
                    }
                });
            } catch (Exception e) {
                System.err.println("❌ Ошибка загрузки данных: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // ============ ТОВАРЫ ============

    /**
     * Загружает товары из БД
     */
    private void loadProducts() {
        try {
            System.out.println("📦 Загрузка товаров...");
            List<ProductDTO> products = AdminRepository.getAllProducts();

            Platform.runLater(() -> {
                if (productsTable != null) {
                    // Настраиваем таблицу только один раз
                    if (!productsTableSetup) {
                        setupProductsTable();
                        productsTableSetup = true;
                    }

                    // Обновляем данные
                    productsData.clear();
                    productsData.addAll(products);
                    productsTable.setItems(productsData);
                    System.out.println("✅ Таблица товаров обновлена: " + products.size() + " товаров");
                }
            });
        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки товаров: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Настраивает таблицу товаров
     */
    private void setupProductsTable() {
        System.out.println("⚙️ Настройка таблицы товаров...");
        productsTable.getColumns().clear();

        // ID
        TableColumn<ProductDTO, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().id).asObject());
        idCol.setPrefWidth(50);

        // Название
        TableColumn<ProductDTO, String> nameCol = new TableColumn<>("Название");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name));
        nameCol.setPrefWidth(200);

        // Категория
        TableColumn<ProductDTO, String> categoryCol = new TableColumn<>("Категория");
        categoryCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().category));
        categoryCol.setPrefWidth(130);

        // Цена
        TableColumn<ProductDTO, Double> priceCol = new TableColumn<>("Цена (₽)");
        priceCol.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().price).asObject());
        priceCol.setPrefWidth(100);

        // Склад
        TableColumn<ProductDTO, Integer> stockCol = new TableColumn<>("Склад");
        stockCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().stock).asObject());
        stockCol.setPrefWidth(70);

        // ✅ Фото
        TableColumn<ProductDTO, Void> imageCol = new TableColumn<>("Фото");
        imageCol.setCellFactory(col -> new TableCell<ProductDTO, Void>() {
            private final Button uploadBtn = new Button("📷");

            {
                uploadBtn.setStyle("-fx-font-size: 12px; -fx-padding: 4px 8px;");
                uploadBtn.setOnAction(e -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        ProductDTO product = getTableView().getItems().get(getIndex());
                        uploadProductImage(product);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : uploadBtn);
            }
        });
        imageCol.setPrefWidth(60);

        // ✅ Действия
        TableColumn<ProductDTO, Void> actionCol = new TableColumn<>("Действия");
        actionCol.setCellFactory(col -> new TableCell<ProductDTO, Void>() {
            private final Button deleteBtn = new Button("🗑️");

            {
                deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 4px 8px; -fx-font-size: 12px;");
                deleteBtn.setOnAction(e -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        ProductDTO product = getTableView().getItems().get(getIndex());
                        deleteProduct(product);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
            }
        });
        actionCol.setPrefWidth(60);

        productsTable.getColumns().addAll(idCol, nameCol, categoryCol, priceCol, stockCol, imageCol, actionCol);
        System.out.println("✅ Таблица товаров настроена");
    }

    /**
     * Диалог добавления товара
     */
    private void showAddProductDialog() {
        System.out.println("➕ Открытие диалога добавления товара...");

        Dialog<ProductDTO> dialog = new Dialog<>();
        dialog.setTitle("Добавить товар");
        dialog.setHeaderText("📦 Введите данные нового товара");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("Название товара");

        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("Описание");
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);

        TextField priceField = new TextField();
        priceField.setPromptText("Цена (₽)");

        TextField stockField = new TextField();
        stockField.setPromptText("Количество");

        TextField categoryField = new TextField();
        categoryField.setPromptText("Категория");

        TextField manufacturerField = new TextField();
        manufacturerField.setPromptText("Производитель");

        grid.add(new Label("Название:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Описание:"), 0, 1);
        grid.add(descriptionArea, 1, 1);
        grid.add(new Label("Цена (₽):"), 0, 2);
        grid.add(priceField, 1, 2);
        grid.add(new Label("Склад:"), 0, 3);
        grid.add(stockField, 1, 3);
        grid.add(new Label("Категория:"), 0, 4);
        grid.add(categoryField, 1, 4);
        grid.add(new Label("Производитель:"), 0, 5);
        grid.add(manufacturerField, 1, 5);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    String name = nameField.getText().trim();
                    String description = descriptionArea.getText().trim();
                    double price = Double.parseDouble(priceField.getText());
                    int stock = Integer.parseInt(stockField.getText());
                    String category = categoryField.getText().trim();
                    String manufacturer = manufacturerField.getText().trim();

                    if (name.isEmpty() || category.isEmpty()) {
                        showAlert("Ошибка", "Заполните все обязательные поля!", Alert.AlertType.WARNING);
                        return null;
                    }

                    return new ProductDTO(0, name, description, price, stock, "", category, manufacturer);
                } catch (NumberFormatException e) {
                    showAlert("Ошибка", "Проверьте формат цены и количества!", Alert.AlertType.ERROR);
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

    /**
     * Сохраняет товар в БД
     */
    private void saveProduct(ProductDTO product) {
        new Thread(() -> {
            try {
                System.out.println("💾 Сохранение товара: " + product.name);

                AdminRepository.addProduct(
                        product.name,
                        product.description,
                        product.price,
                        product.stock,
                        product.category,
                        product.manufacturer,
                        ""
                );

                Platform.runLater(() -> {
                    showAlert("Успех", "✅ Товар добавлен!", Alert.AlertType.INFORMATION);
                    loadProducts();
                    if (mainController != null) {
                        mainController.reloadProducts();  // Обновляет главную страницу
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Ошибка", "❌ " + e.getMessage(), Alert.AlertType.ERROR));
                System.err.println("❌ Ошибка сохранения: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Удаляет товар
     */
    private void deleteProduct(ProductDTO product) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Подтверждение");
        confirm.setHeaderText(null);
        confirm.setContentText("Удалить товар '" + product.name + "'?");

        if (confirm.showAndWait().get() == ButtonType.OK) {
            new Thread(() -> {
                try {
                    System.out.println("🗑️ Удаление товара: " + product.name);
                    AdminRepository.deleteProduct(product.id);
                    Platform.runLater(() -> {
                        productsData.remove(product);
                        showAlert("Успех", "✅ Товар удален!", Alert.AlertType.INFORMATION);
                        if (mainController != null) {
                            mainController.reloadProducts();  // Обновляет главную страницу
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert("Ошибка", "❌ " + e.getMessage(), Alert.AlertType.ERROR));
                }
            }).start();
        }
    }

    /**
     * Загружает изображение для товара
     */
    private void uploadProductImage(ProductDTO product) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите изображение");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Изображения", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp")
        );

        File selectedFile = fileChooser.showOpenDialog(productsTable.getScene().getWindow());
        if (selectedFile != null) {
            System.out.println("📤 Загрузка изображения для товара: " + product.name);

            new Thread(() -> {
                try {
                    String fileName = SupabaseStorageService.generateFileName(product.id, selectedFile.getName());
                    String imageUrl = SupabaseStorageService.uploadImage(selectedFile, fileName);
                    AdminRepository.updateProductImage(product.id, imageUrl);

                    Platform.runLater(() -> {
                        product.imageUrl = imageUrl;
                        loadProducts();
                        showAlert("Успех", "✅ Изображение загружено!", Alert.AlertType.INFORMATION);
                        if (mainController != null) {
                            mainController.reloadProducts();  // Обновляет главную страницу
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert("Ошибка", "❌ " + e.getMessage(), Alert.AlertType.ERROR));
                    System.err.println("❌ Ошибка загрузки: " + e.getMessage());
                }
            }).start();
        }
    }

    // ============ ПОЛЬЗОВАТЕЛИ ============

    /**
     * Загружает пользователей из БД
     */
    private void loadUsers() {
        try {
            System.out.println("👥 Загрузка пользователей...");
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
                    System.out.println("✅ Таблица пользователей обновлена: " + users.size() + " пользователей");
                }
            });
        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки пользователей: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Настраивает таблицу пользователей
     */
    private void setupUsersTable() {
        System.out.println("⚙️ Настройка таблицы пользователей...");
        usersTable.getColumns().clear();

        TableColumn<UserDTO, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().email));
        emailCol.setPrefWidth(250);

        TableColumn<UserDTO, String> nameCol = new TableColumn<>("Имя");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name != null ? cellData.getValue().name : ""));
        nameCol.setPrefWidth(150);

        TableColumn<UserDTO, String> surnameCol = new TableColumn<>("Фамилия");
        surnameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().surname != null ? cellData.getValue().surname : ""));
        surnameCol.setPrefWidth(150);

        TableColumn<UserDTO, String> cityCol = new TableColumn<>("Город");
        cityCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().city != null ? cellData.getValue().city : ""));
        cityCol.setPrefWidth(150);

        usersTable.getColumns().addAll(emailCol, nameCol, surnameCol, cityCol);
        System.out.println("✅ Таблица пользователей настроена");
    }

    // ============ ПУБЛИЧНЫЕ МЕТОДЫ ============

    /**
     * Вызывается сервисом синхронизации для обновления товаров
     */
    public void refreshProductsList() {
        System.out.println("🔄 Синхронизация товаров...");
        loadProducts();
    }

    /**
     * Останавливает сервис при закрытии
     */
    public void stopRefreshService() {
        if (refreshService != null) {
            refreshService.stop();
            System.out.println("✅ Сервис остановлен");
        }
    }

    // ============ УТИЛИТЫ ============

    /**
     * Показывает диалоговое окно
     */
    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}