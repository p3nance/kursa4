package controllers;

import com.example.authapp.ui.Ui;
import com.example.authapp.ui.ProductEditDialog;
import java.util.concurrent.*;

import com.example.authapp.dto.ProductDTO;
import com.example.authapp.repositories.AdminRepository;
import com.example.authapp.services.SupabaseStorageService;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import java.math.BigDecimal;
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
    @FXML private Label screenStatus;
    private volatile boolean disposed, active=true, loading;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"secondary-data");t.setDaemon(true);return t;});
    private void runAsync(Runnable job){
        if(disposed)return;var lease=config.SessionManager.captureSession();
        worker.submit(()->{try(var scope=com.example.authapp.network.SupabaseHttp.forSession(lease)){if(!disposed)job.run();}
            catch(RuntimeException ex){loading=false;report("Сессия изменилась. Откройте раздел текущего аккаунта снова.");}});
    }
    private void report(String text){Platform.runLater(()->{if(!disposed&&screenStatus!=null)screenStatus.setText(text);});}


    // ============ ШАПКА ============
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
            System.out.println(" ManagerController инициализирован");
            setupUI();
            loadProducts();
        } catch (Exception e) {
            System.err.println(" Ошибка инициализации ManagerController: " + e.getMessage());
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
        if(loading||disposed)return;loading=true;report("Обновляем товары…");
        runAsync(() -> {
            try {
                List<ProductDTO> products = AdminRepository.getAllProducts();
                loading=false;report("Каталог товаров");
                Platform.runLater(() -> { if (disposed) return;
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
                loading=false;report("Не удалось загрузить данные. Проверьте соединение и доступ к разделу.");
                System.err.println(" Ошибка загрузки товаров: " + e.getMessage());
            }
        });
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

        TableColumn<ProductDTO, BigDecimal> priceCol = new TableColumn<>("Цена (₽)");
        priceCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue().price));
        priceCol.setPrefWidth(100);
        priceCol.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal value,boolean empty){super.updateItem(value,empty);setText(empty||value==null?null:Ui.money(value));}
        });

        // ✅ Остаток с цветовой индикацией
        TableColumn<ProductDTO, Integer> stockCol = new TableColumn<>("Остаток");
        stockCol.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().stock).asObject());
        stockCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer stock, boolean empty) {
                super.updateItem(stock, empty);
                getStyleClass().removeAll("stock-empty", "stock-low", "stock-available");
                if (empty || stock == null) { setText(null); return; }
                setText(stock + " шт.");
                getStyleClass().add(stock == 0 ? "stock-empty" : stock <= 5 ? "stock-low" : "stock-available");
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

                Button editBtn = new Button("Изменить");
                editBtn.setTooltip(new Tooltip("Редактировать"));
                editBtn.getStyleClass().addAll("manager-action", "manager-action-edit");
                editBtn.setOnAction(e -> showEditProductDialog(p));

                Button restockBtn = new Button("Склад");
                restockBtn.setTooltip(new Tooltip("Пополнить склад"));
                restockBtn.getStyleClass().addAll("manager-action", "manager-action-restock");
                restockBtn.setOnAction(e -> showRestockDialog(p));

                Button photoBtn = new Button("Фото");
                photoBtn.setTooltip(new Tooltip("Загрузить фото"));
                photoBtn.getStyleClass().addAll("manager-action", "manager-action-photo");
                photoBtn.setOnAction(e -> uploadProductImage(p));

                Button deleteBtn = new Button("Архив");
                deleteBtn.setTooltip(new Tooltip("Удалить товар"));
                deleteBtn.getStyleClass().addAll("manager-action", "manager-action-delete");
                deleteBtn.setOnAction(e -> deleteProduct(p));

                actions.getChildren().addAll(editBtn, restockBtn, photoBtn, deleteBtn);
                setGraphic(actions);
            }
        });
        actionCol.setPrefWidth(310);

        productsTable.getColumns().addAll(idCol, nameCol, categoryCol, manufacturerCol, priceCol, stockCol, actionCol);
        System.out.println(" Таблица товаров (менеджер) настроена");
    }

    // ============================================
    // ✅ ДОБАВЛЕНИЕ ТОВАРА
    // ============================================

    private void showAddProductDialog() {
        ProductEditDialog.show(productsTable.getScene()==null?null:productsTable.getScene().getWindow(),null).ifPresent(this::saveNewProduct);
    }

    private void saveNewProduct(ProductDTO product) {
        runAsync(() -> {
            try {
                AdminRepository.addProduct(product.name, product.description, product.price,
                        product.stock, product.category, product.manufacturer, "");
                Platform.runLater(() -> { if (disposed) return;
                    showAlert(" Успех", "Товар «" + product.name + "» добавлен!");
                    loadProducts();
                    if (mainController != null) mainController.reloadProducts();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(" Ошибка", e.getMessage()));
            }
        });
    }

    // ============================================
    // ✅ РЕДАКТИРОВАНИЕ ТОВАРА
    // ============================================

    private void showEditProductDialog(ProductDTO product) {
        ProductEditDialog.show(productsTable.getScene()==null?null:productsTable.getScene().getWindow(),product).ifPresent(this::updateProduct);
    }

    private void updateProduct(ProductDTO product) {
        runAsync(() -> {
            try {
                AdminRepository.updateProduct(product.id, product.name, product.description,
                        product.price, product.stock, product.category, product.manufacturer);
                Platform.runLater(() -> { if (disposed) return;
                    showAlert(" Успех", "Товар «" + product.name + "» обновлён!");
                    loadProducts();
                    if (mainController != null) mainController.reloadProducts();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(" Ошибка", e.getMessage()));
            }
        });
    }

    // ============================================
    // ✅ ПОПОЛНЕНИЕ СКЛАДА
    // ============================================

    private void showRestockDialog(ProductDTO product) {
        Dialog<Integer> dialog = new Dialog<>();
        Ui.theme(dialog);
        dialog.setTitle(" Пополнение склада");
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
                    if (add <= 0) { showAlert(" Ошибка", "Количество должно быть > 0!"); return null; }
                    return product.stock + add;
                } catch (IllegalArgumentException e) {
                    showAlert(" Ошибка", "Введите целое число!");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newStock -> {
            if (newStock != null) {
                runAsync(() -> {
                    try {
                        AdminRepository.updateProductStock(product.id, newStock);
                        Platform.runLater(() -> { if (disposed) return;
                            showAlert(" Успешно",
                                    "Остаток «" + product.name + "»:\n" +
                                            product.stock + " → " + newStock + " шт.");
                            loadProducts();
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert(" Ошибка", e.getMessage()));
                    }
                });
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
            runAsync(() -> {
                try {
                    String fileName = SupabaseStorageService.generateFileName(product.id, file.getName());
                    String imageUrl = SupabaseStorageService.uploadImage(file, fileName);
                    AdminRepository.updateProductImage(product.id, imageUrl);
                    Platform.runLater(() -> { if (disposed) return;
                        product.imageUrl = imageUrl;
                        showAlert(" Успех", "Фото товара загружено!");
                        loadProducts();
                        if (mainController != null) mainController.reloadProducts();
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert(" Ошибка", e.getMessage()));
                }
            });
        }
    }

    // ============================================
    // ✅ УДАЛЕНИЕ ТОВАРА
    // ============================================

    private void deleteProduct(ProductDTO product) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        Ui.theme(confirm);
        confirm.setTitle("Убрать товар из продажи");
        confirm.setHeaderText("Товар: " + product.name);
        confirm.setContentText("Остаток товара станет нулевым, он будет недоступен для покупки. История заказов сохранится.");

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                runAsync(() -> {
                    try {
                        AdminRepository.deleteProduct(product.id);
                        Platform.runLater(() -> { if (disposed) return;
                            productsData.remove(product);
                            showAlert(" Успех", "Товар «" + product.name + "» убран из продажи.");
                            if (mainController != null) mainController.reloadProducts();
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert(" Ошибка", e.getMessage()));
                    }
                });
            }
        });
    }

    // ============================================
    // ✅ ВСПОМОГАТЕЛЬНЫЙ МЕТОД: форма товара
    // ============================================



    // ============================================
    // ✅ УТИЛИТЫ
    // ============================================

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        Ui.theme(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        if (!disposed) alert.showAndWait();
    }

    public void setActive(boolean value){active=value;if(value&&!disposed)loadProducts();}
    public void dispose(){disposed=true;active=false;worker.shutdownNow();}
}
