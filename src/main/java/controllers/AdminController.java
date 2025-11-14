package controllers;

import com.example.authapp.dto.ProductDTO;
import com.example.authapp.dto.UserDTO;
import com.example.authapp.repositories.AdminRepository;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class AdminController implements Initializable {

    @FXML private TabPane adminTabs;
    @FXML private Button exitAdminBtn;
    @FXML private TableView<ProductDTO> productsTable;
    @FXML private TableView<UserDTO> usersTable;

    private MainController mainController;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupUI();
        loadAdminData();
    }

    public void setMainController(MainController controller) {
        this.mainController = controller;
    }

    private void setupUI() {
        // Кнопка выхода
        if (exitAdminBtn != null) {
            exitAdminBtn.setOnAction(e -> {
                if (mainController != null) {
                    mainController.showMainContent();
                }
            });
        }

        // ✅ НАХОДИМ КНОПКУ "ДОБАВИТЬ ТОВАР" И ДАЁМ ЕЙ ДЕЙСТВИЕ
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

    // ✅ ЗАГРУЗКА ДАННЫХ ИЗ БД
    private void loadAdminData() {
        new Thread(() -> {
            try {
                loadProducts();
                loadUsers();
            } catch (Exception e) {
                System.err.println("❌ Ошибка загрузки данных: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // ============ ТОВАРЫ ============

    private void loadProducts() {
        try {
            List<ProductDTO> products = AdminRepository.getAllProducts();
            ObservableList<ProductDTO> observableProducts = FXCollections.observableArrayList(products);

            Platform.runLater(() -> {
                if (productsTable != null) {
                    setupProductsTable();
                    productsTable.setItems(observableProducts);
                } else {
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

        TableColumn<ProductDTO, Void> actionCol = new TableColumn<>("Действия");
        actionCol.setCellFactory(col -> new TableCell<ProductDTO, Void>() {
            private final Button deleteBtn = new Button("❌ Удалить");

            {
                deleteBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 5px;");
                deleteBtn.setOnAction(e -> {
                    ProductDTO product = getTableView().getItems().get(getIndex());
                    deleteProduct(product);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
            }
        });
        actionCol.setPrefWidth(120);

        productsTable.getColumns().addAll(idCol, nameCol, categoryCol, priceCol, stockCol, actionCol);
    }

    private void deleteProduct(ProductDTO product) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Подтверждение");
        confirm.setContentText("Удалить товар '" + product.name + "'?");

        if (confirm.showAndWait().get() == ButtonType.OK) {
            new Thread(() -> {
                try {
                    AdminRepository.deleteProduct(product.id);
                    Platform.runLater(this::loadProducts);
                } catch (Exception e) {
                    System.err.println("❌ Ошибка удаления: " + e.getMessage());
                }
            }).start();
        }
    }

    // ============ ПОЛЬЗОВАТЕЛИ ============

    private void loadUsers() {
        try {
            List<UserDTO> users = AdminRepository.getAllUsers();
            ObservableList<UserDTO> observableUsers = FXCollections.observableArrayList(users);

            Platform.runLater(() -> {
                if (usersTable != null) {
                    setupUsersTable();
                    usersTable.setItems(observableUsers);
                } else {
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupUsersTable() {
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
                    String name = nameField.getText();
                    String description = descriptionArea.getText();
                    double price = Double.parseDouble(priceField.getText());
                    int stock = Integer.parseInt(stockField.getText());
                    String category = categoryField.getText();
                    String manufacturer = manufacturerField.getText();

                    if (name.isEmpty() || category.isEmpty() || manufacturer.isEmpty()) {
                        showAlert("Ошибка", "Заполните все поля!");
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
                AdminRepository.addProduct(
                        product.name,
                        product.description,
                        product.price,
                        product.stock,
                        product.category,
                        product.manufacturer
                );

                Platform.runLater(() -> {
                    showAlert("Успех", "Товар добавлен успешно!");
                    loadProducts();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Ошибка", "Ошибка добавления: " + e.getMessage()));
                System.err.println("❌ Ошибка: " + e.getMessage());
            }
        }).start();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
