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

public class AdminController implements Initializable {

    @FXML private TabPane adminTabs;
    @FXML private Button exitAdminBtn;
    @FXML private TableView<ProductDTO> productsTable;
    @FXML private TableView<UserDTO> usersTable;

    private MainController mainController;

    // ✅ REFRESH SERVICE
    private AdminRefreshService refreshService;

    private File selectedImageFile = null;

    // ✅ OBSERVABLE LISTS - для автоматического обновления UI
    private ObservableList<ProductDTO> productsData = FXCollections.observableArrayList();
    private ObservableList<UserDTO> usersData = FXCollections.observableArrayList();

    // ✅ FLAGS - чтобы таблицы не переинициализировались каждый раз
    private boolean productsTableSetup = false;
    private boolean usersTableSetup = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        System.out.println("📋 Инициализация AdminController...");
        setupUI();

        // ✅ ИНИЦИАЛИЗИРУЕМ СЕРВИС СИНХРОНИЗАЦИИ
        refreshService = new AdminRefreshService(this);

        loadAdminData();
    }

    public void setMainController(MainController controller) {
        this.mainController = controller;
    }

    private void setupUI() {
        if (exitAdminBtn != null) {
            exitAdminBtn.setOnAction(e -> {
                System.out.println("🚪 Выход из админ-панели");
                // ✅ ОСТАНАВЛИВАЕМ СЕРВИС ПЕРЕД ВЫХОДОМ
                if (refreshService != null) {
                    refreshService.stop();
                }
                if (mainController != null) {
                    mainController.showMainContent();
                }
            });
        }

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
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ============ ЗАГРУЗКА ДАННЫХ ============

    private void loadAdminData() {
        new Thread(() -> {
            try {
                System.out.println("📦 Инициальная загрузка данных...");
                loadProducts();
                loadUsers();

                // ✅ ЗАПУСКАЕМ АВТОМАТИЧЕСКОЕ ОБНОВЛЕНИЕ
                Platform.runLater(() -> {
                    if (refreshService != null) {
                        refreshService.start();
                    }
                });
            } catch (Exception e) {
                System.err.println("❌ Ошибка загрузки данных: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // ============ ЗАГРУЗКА ТОВАРОВ ============

    private void loadProducts() {
        try {
            System.out.println("📦 Загрузка товаров из БД...");
            List<ProductDTO> products = AdminRepository.getAllProducts();

            Platform.runLater(() -> {
                if (productsTable != null) {
                    // ✅ НАСТРАИВАЕМ ТАБЛИЦУ ТОЛЬКО ОДИН РАЗ
                    if (!productsTableSetup) {
                        setupProductsTable();
                        productsTableSetup = true;
                    }

                    // ✅ ОБНОВЛЯЕМ ДАННЫЕ БЕЗ ПЕРЕИНИЦИАЛИЗАЦИИ
                    productsData.clear();
                    productsData.addAll(products);
                    productsTable.setItems(productsData);

                    System.out.println("✅ Таблица товаров обновлена: " + products.size() + " товаров");
                } else {
                    System.err.println("⚠️ productsTable is null");
                }
            });

        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки товаров: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupProductsTable() {
        System.out.println("⚙️ Настройка таблицы товаров...");

        // ✅ ОЧИЩАЕМ ТОЛЬКО ПРИ ПЕРВОЙ НАСТРОЙКЕ
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

        // ✅ КОЛОНКА С ИЗОБРАЖЕНИЕМ И КНОПКОЙ ЗАГРУЗКИ
        TableColumn<ProductDTO, Void> imageCol = new TableColumn<>("Фото");
        imageCol.setCellFactory(col -> new TableCell<ProductDTO, Void>() {
            private final Button uploadBtn = new Button("📷 Загрузить");

            {
                uploadBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 5px;");
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
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    ProductDTO product = getTableView().getItems().get(getIndex());
                    if (product != null && product.imageUrl != null && !product.imageUrl.isEmpty()) {
                        try {
                            ImageView imageView = new ImageView();
                            Image image = new Image(product.imageUrl, 40, 40, true, true, true);
                            imageView.setImage(image);
                            imageView.setFitWidth(40);
                            imageView.setFitHeight(40);
                            HBox box = new HBox(5, imageView, uploadBtn);
                            setGraphic(box);
                        } catch (Exception ex) {
                            setGraphic(uploadBtn);
                        }
                    } else {
                        setGraphic(uploadBtn);
                    }
                }
            }
        });
        imageCol.setPrefWidth(140);

        TableColumn<ProductDTO, Void> actionCol = new TableColumn<>("Действия");
        actionCol.setCellFactory(col -> new TableCell<ProductDTO, Void>() {
            private final Button deleteBtn = new Button("❌ Удалить");

            {
                deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 5px;");
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
        actionCol.setPrefWidth(120);

        productsTable.getColumns().addAll(idCol, nameCol, categoryCol, priceCol, stockCol, imageCol, actionCol);
        System.out.println("✅ Таблица товаров настроена");
    }

    // ============ ЗАГРУЗКА ИЗОБРАЖЕНИЯ ============

    private void uploadProductImage(ProductDTO product) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите изображение товара");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Все изображения", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp"),
                new FileChooser.ExtensionFilter("JPEG", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("GIF", "*.gif"),
                new FileChooser.ExtensionFilter("WebP", "*.webp")
        );

        File selectedFile = fileChooser.showOpenDialog(productsTable.getScene().getWindow());

        if (selectedFile != null) {
            System.out.println("📤 Загрузка изображения для товара: " + product.name + " (ID: " + product.id + ")");

            Alert loadingAlert = new Alert(Alert.AlertType.INFORMATION);
            loadingAlert.setTitle("Загрузка");
            loadingAlert.setHeaderText("Загрузка изображения...");
            loadingAlert.setContentText("Пожалуйста, подождите");
            loadingAlert.show();

            new Thread(() -> {
                try {
                    String fileName = SupabaseStorageService.generateFileName(product.id, selectedFile.getName());
                    String imageUrl = SupabaseStorageService.uploadImage(selectedFile, fileName);

                    AdminRepository.updateProductImage(product.id, imageUrl);

                    Platform.runLater(() -> {
                        loadingAlert.close();
                        showAlert("Успех", "Изображение загружено успешно!");

                        // ✅ СИНХРОННО ОБНОВЛЯЕМ ТОВАР В ПАМЯТИ
                        product.imageUrl = imageUrl;

                        // ✅ ЗАНОВО ЗАГРУЖАЕМ ТОВАРЫ ИЗ БД
                        loadProducts();
                        if (mainController != null) {
                            mainController.reloadProducts();  // <<< теперь главная витрина обновится!
                        }
                        // ✅ ВКЛЮЧАЕМ СЕРВИС СИНХРОНИЗАЦИИ
                        if (refreshService != null) {
                            refreshService.start();
                        }
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        loadingAlert.close();
                        showAlert("Ошибка", "Ошибка загрузки изображения: " + e.getMessage());
                    });
                    System.err.println("❌ Ошибка: " + e.getMessage());
                }
            }).start();
        }
    }

    // ============ УДАЛЕНИЕ ТОВАРА ============

    private void deleteProduct(ProductDTO product) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Подтверждение");
        confirm.setContentText("Удалить товар '" + product.name + "'?");

        if (confirm.showAndWait().get() == ButtonType.OK) {
            new Thread(() -> {
                try {
                    System.out.println("🗑️ Удаление товара: " + product.name);
                    AdminRepository.deleteProduct(product.id);

                    Platform.runLater(() -> {
                        productsData.remove(product);
                        showAlert("Успех", "Товар удален!");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert("Ошибка", "Ошибка удаления: " + e.getMessage()));
                    System.err.println("❌ Ошибка удаления: " + e.getMessage());
                }
            }).start();
        }
    }

    // ============ ЗАГРУЗКА ПОЛЬЗОВАТЕЛЕЙ ============

    private void loadUsers() {
        try {
            System.out.println("👥 Загрузка пользователей из БД...");
            List<UserDTO> users = AdminRepository.getAllUsers();

            Platform.runLater(() -> {
                if (usersTable != null) {
                    // ✅ НАСТРАИВАЕМ ТАБЛИЦУ ТОЛЬКО ОДИН РАЗ
                    if (!usersTableSetup) {
                        setupUsersTable();
                        usersTableSetup = true;
                    }

                    // ✅ ОБНОВЛЯЕМ ДАННЫЕ БЕЗ ПЕРЕИНИЦИАЛИЗАЦИИ
                    usersData.clear();
                    usersData.addAll(users);
                    usersTable.setItems(usersData);

                    System.out.println("✅ Таблица пользователей обновлена: " + users.size() + " пользователей");
                } else {
                    System.err.println("⚠️ usersTable is null");
                }
            });

        } catch (Exception e) {
            System.err.println("❌ Ошибка загрузки пользователей: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupUsersTable() {
        System.out.println("⚙️ Настройка таблицы пользователей...");

        // ✅ ОЧИЩАЕМ ТОЛЬКО ПРИ ПЕРВОЙ НАСТРОЙКЕ
        usersTable.getColumns().clear();

        TableColumn<UserDTO, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().email));
        emailCol.setPrefWidth(250);

        TableColumn<UserDTO, String> nameCol = new TableColumn<>("Имя");
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name));
        nameCol.setPrefWidth(150);

        TableColumn<UserDTO, String> surnameCol = new TableColumn<>("Фамилия");
        surnameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().surname));
        surnameCol.setPrefWidth(150);

        TableColumn<UserDTO, String> cityCol = new TableColumn<>("Город");
        cityCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().city));
        cityCol.setPrefWidth(150);

        usersTable.getColumns().addAll(emailCol, nameCol, surnameCol, cityCol);
        System.out.println("✅ Таблица пользователей настроена");
    }

    // ============ ДОБАВЛЕНИЕ ТОВАРА ============

    private void showAddProductDialog() {
        Dialog<ProductDTO> dialog = new Dialog<>();
        dialog.setTitle("Добавить товар");
        dialog.setHeaderText("Введите данные нового товара");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("Название товара");

        TextArea descriptionArea = new TextArea();
        descriptionArea.setPromptText("Описание");
        descriptionArea.setPrefRowCount(4);
        descriptionArea.setWrapText(true);

        TextField priceField = new TextField();
        priceField.setPromptText("Цена");

        TextField stockField = new TextField();
        stockField.setPromptText("Количество на складе");

        TextField categoryField = new TextField();
        categoryField.setPromptText("Категория");

        TextField manufacturerField = new TextField();
        manufacturerField.setPromptText("Производитель");

        Button selectImageBtn = new Button("📷 Выбрать изображение");
        Label imageLabel = new Label("Изображение не выбрано");
        ImageView previewImageView = new ImageView();
        previewImageView.setFitWidth(100);
        previewImageView.setFitHeight(100);
        previewImageView.setPreserveRatio(true);

        selectImageBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выберите изображение");
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Изображения", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.webp")
            );

            File file = fileChooser.showOpenDialog(dialog.getOwner());
            if (file != null) {
                selectedImageFile = file;
                imageLabel.setText(file.getName());
                try {
                    Image image = new Image(file.toURI().toString());
                    previewImageView.setImage(image);
                } catch (Exception ex) {
                    System.err.println("Ошибка загрузки превью: " + ex.getMessage());
                }
            }
        });

        HBox imageBox = new HBox(10, selectImageBtn, imageLabel);

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
        grid.add(new Label("Изображение:"), 0, 6);
        grid.add(imageBox, 1, 6);
        grid.add(previewImageView, 1, 7);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    String name = nameField.getText();
                    String description = descriptionArea.getText();
                    double price = Double.parseDouble(priceField.getText());
                    int stock = Integer.parseInt(stockField.getText());
                    String category = categoryField.getText();
                    String manufacturer = manufacturerField.getText();

                    if (name.isEmpty() || category.isEmpty() || manufacturer.isEmpty()) {
                        showAlert("Ошибка", "Заполните все обязательные поля!");
                        return null;
                    }

                    return new ProductDTO(0, name, description, price, stock, "", category, manufacturer);

                } catch (NumberFormatException e) {
                    showAlert("Ошибка", "Проверьте формат цены и количества!");
                }
            }
            return null;
        });

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            ProductDTO product = result.get();
            saveProduct(product);
        }
    }

    private void saveProduct(ProductDTO product) {
        new Thread(() -> {
            try {
                System.out.println("➕ Добавление нового товара: " + product.name);
                String imageUrl = "";

                if (selectedImageFile != null) {
                    System.out.println("📤 Загрузка изображения...");
                    String fileName = "product_new_" + System.currentTimeMillis() + "_" + selectedImageFile.getName();
                    imageUrl = SupabaseStorageService.uploadImage(selectedImageFile, fileName);
                    selectedImageFile = null;
                }

                AdminRepository.addProduct(
                        product.name,
                        product.description,
                        product.price,
                        product.stock,
                        product.category,
                        product.manufacturer,
                        imageUrl
                );

                Platform.runLater(() -> {
                    showAlert("Успех", "Товар добавлен успешно!");

                    // ✅ ПЕРЕЗАГРУЖАЕМ ТАБЛИЦУ
                    loadProducts();

                    // ✅ ВКЛЮЧАЕМ СЕРВИС СИНХРОНИЗАЦИИ
                    if (refreshService != null) {
                        refreshService.start();
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Ошибка", "Ошибка добавления: " + e.getMessage()));
                System.err.println("❌ Ошибка: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // ============ ✅ ПУБЛИЧНЫЙ МЕТОД ДЛЯ СИНХРОНИЗАЦИИ ============

    /**
     * ✅ ПУБЛИЧНЫЙ метод для внешней синхронизации (вызывается AdminRefreshService)
     */
    public void refreshProductsList() {
        System.out.println("🔄 Синхронизация списка товаров...");
        loadProducts();
    }

    /**
     * ✅ ОСТАНОВКА СЕРВИСА ПРИ ЗАКРЫТИИ
     */
    public void stopRefreshService() {
        if (refreshService != null) {
            refreshService.stop();
        }
    }

    // ============ УТИЛИТЫ ============

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}