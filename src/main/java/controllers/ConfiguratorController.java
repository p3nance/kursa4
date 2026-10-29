package controllers;

import com.example.authapp.domain.ComponentCategory;
import com.example.authapp.models.Product;
import com.example.authapp.repositories.ProductRepository;
import com.example.authapp.services.CartService;
import com.example.authapp.services.CompatibilityService;
import com.example.authapp.services.LocalKnowledgeRepository;
import com.example.authapp.ui.Ui;
import config.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/** Stateful workspace: the shell may hide it without discarding the selected build. */
public final class ConfiguratorController {
    @FXML private StackPane configuratorRoot;
    @FXML private VBox mainContent, listColumn, summaryColumn, categoriesContainer, findingsBox;
    @FXML private GridPane configColumns;
    @FXML private Label totalPrice, selectedCount, budgetValue, remainingBudget, budgetRemainderLabel;
    @FXML private Label compatStatusText, compatTitle, cartStatusText, overlayTitle, pickerCount;
    @FXML private HBox compatStatusBox, cartStatusBox, budgetEditor;
    @FXML private ProgressBar budgetProgress;
    @FXML private ProgressIndicator addProgress;
    @FXML private ListView<String> componentsList;
    @FXML private Button addBuildToCart, clearBuildButton, closeOverlayBtn, clearSelectionBtn;
    @FXML private Button applyBudgetButton, removeBudgetButton;
    @FXML private TextField budgetField, pickerSearch;
    @FXML private CheckBox onlyAvailable;
    @FXML private StackPane overlayPane;
    @FXML private TilePane overlayProductsGrid;

    private MainController host;
    private List<Product> catalog = List.of();
    private Map<Integer, Product> currentProducts = Map.of();
    private final EnumMap<ComponentCategory, Product> selected = new EnumMap<>(ComponentCategory.class);
    private LocalKnowledgeRepository.Snapshot knowledge;
    private CompatibilityService.Result assessment;
    private ComponentCategory editingCategory;
    private BigDecimal budget;
    private boolean catalogLoaded, adding, disposed;
    private long operation;
    private Future<?> addTask;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "configured-build-cart"); thread.setDaemon(true); return thread;
    });
    private static final Map<String, String> CHECK_NAMES = Map.ofEntries(
            Map.entry("socket", "Сокет процессора и платы"), Map.entry("cpu_support", "Поддержка процессора и BIOS"),
            Map.entry("memory_type", "Поколение оперативной памяти"), Map.entry("memory_capacity", "Объём памяти"),
            Map.entry("memory_slots", "Количество модулей памяти"), Map.entry("board_case", "Плата и корпус"),
            Map.entry("storage_interface", "Интерфейс накопителя"), Map.entry("gpu_length", "Длина видеокарты"),
            Map.entry("cooler_height", "Высота охлаждения"), Map.entry("cooler_socket", "Крепление охлаждения"),
            Map.entry("cooler_power", "Мощность охлаждения"), Map.entry("psu_case", "Блок питания и корпус"),
            Map.entry("gpu_min_psu", "Мощность блока питания для GPU"), Map.entry("gpu_connectors", "Разъёмы питания GPU"),
            Map.entry("board_connectors", "Разъёмы питания платы"), Map.entry("power_budget", "Расчёт питания с запасом"));

    @FXML private void initialize() {
        addBuildToCart.setGraphic(Ui.icon("bag"));
        clearBuildButton.setGraphic(Ui.icon("trash"));
        closeOverlayBtn.setGraphic(Ui.icon("close"));
        pickerSearch.textProperty().addListener((o, before, after) -> renderPicker());
        onlyAvailable.selectedProperty().addListener((o, before, after) -> renderPicker());
        budgetField.setOnAction(event -> saveBudget());
        configuratorRoot.widthProperty().addListener((o, before, after) -> layoutColumns(after.doubleValue()));
        configuratorRoot.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ESCAPE && overlayPane.isVisible()) { closePicker(); event.consume(); } });
        layoutColumns(1100);
        recalculate();
    }

    public void setMainController(MainController mainController) { host = mainController; }

    /** Updates prices and stock without losing selections or a user-entered budget. */
    public void setProducts(List<Product> products) {
        catalog = products.stream().filter(p -> Double.isFinite(p.getPrice()) && p.getPrice() >= 0).toList();
        Map<Integer, Product> fresh = new LinkedHashMap<>();
        catalog.forEach(product -> fresh.put(product.getId(), product));
        currentProducts = Map.copyOf(fresh);
        catalogLoaded = true;
        selected.replaceAll((category, previous) -> fresh.getOrDefault(previous.getId(), previous));
        knowledge = new LocalKnowledgeRepository().load(catalog);
        recalculate();
        if (editingCategory != null) renderPicker();
    }

    public void selectSuggestedBuild(List<Product> products) {
        if (adding) return;
        selected.clear();
        for (Product product : products) if(Double.isFinite(product.getPrice()) && product.getPrice() >= 0) ComponentCategory.fromDisplayName(product.getCategory()).ifPresent(category ->
                selected.put(category, currentProducts.getOrDefault(product.getId(), product)));
        hideStatus(); recalculate();
    }

    public void setBudget(BigDecimal value) {
        if (value != null && (value.signum() <= 0 || value.compareTo(new BigDecimal("100000000")) > 0))
            throw new IllegalArgumentException("Бюджет должен быть больше нуля и не более 100 млн рублей");
        budget = value; updateBudget(total());
    }
    public BigDecimal getBudget() { return budget; }
    public List<Product> getSelectedProducts() { return List.copyOf(selected.values()); }
    public void setActive(boolean active) { if (!active) closePicker(); }
    public void dispose() { disposed = true; operation++; if (addTask != null) addTask.cancel(true); worker.shutdownNow(); }

    private void layoutColumns(double width) {
        boolean stacked = width > 0 && width < 650;
        GridPane.setColumnIndex(summaryColumn, stacked ? 0 : 1);
        GridPane.setRowIndex(summaryColumn, stacked ? 1 : 0);
        ColumnConstraints primary = new ColumnConstraints(); primary.setMinWidth(0); primary.setHgrow(Priority.ALWAYS);
        if (stacked) configColumns.getColumnConstraints().setAll(primary);
        else {
            ColumnConstraints secondary = new ColumnConstraints(width < 930 ? 255 : 300);
            secondary.setMinWidth(width < 930 ? 255 : 300); secondary.setMaxWidth(width < 930 ? 255 : 300);
            configColumns.getColumnConstraints().setAll(primary, secondary);
        }
    }

    @FXML private void askForHelp() { if (host != null) host.openChat(); }
    @FXML private void clearBuild() { if (!adding) { selected.clear(); hideStatus(); recalculate(); } }
    @FXML private void toggleBudgetEditor() {
        budgetEditor.setVisible(!budgetEditor.isVisible()); budgetEditor.setManaged(budgetEditor.isVisible());
        if (budgetEditor.isVisible()) { budgetField.setText(budget == null ? "" : budget.toPlainString()); budgetField.requestFocus(); budgetField.selectAll(); }
    }
    @FXML private void saveBudget() {
        try {
            String input = budgetField.getText().replaceAll("[\\s\\u00a0\\u202f]", "").replace(',', '.');
            setBudget(input.isBlank() ? null : new BigDecimal(input));
            budgetEditor.setVisible(false); budgetEditor.setManaged(false); hideStatus();
        } catch (IllegalArgumentException ex) { showStatus("Укажите положительный бюджет в рублях, не более 100 млн.", false); }
    }
    @FXML private void removeBudget() { setBudget(null); budgetEditor.setVisible(false); budgetEditor.setManaged(false); }

    private BigDecimal total() { return selected.values().stream().map(p -> BigDecimal.valueOf(p.getPrice())).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private boolean unavailable(Product p) { return p.getStock() <= 0 || catalogLoaded && !currentProducts.containsKey(p.getId()); }

    private void recalculate() {
        BigDecimal sum = total();
        totalPrice.setText(Ui.money(sum)); selectedCount.setText(selected.size() + " из 8 компонентов");
        componentsList.getItems().setAll(selected.values().stream().map(p -> p.getName() + " — " + Ui.money(p.getPrice())).toList());
        Set<Integer> ids = selected.values().stream().map(Product::getId).collect(Collectors.toSet());
        var specs = knowledge == null ? List.<com.example.authapp.domain.CatalogComponent>of()
                : knowledge.components().stream().filter(c -> ids.contains(c.productId())).toList();
        assessment = new CompatibilityService().assess(specs);
        updateCompatibility(); renderRows(); updateBudget(sum);
        boolean unavailable = selected.values().stream().anyMatch(this::unavailable);
        addBuildToCart.setDisable(adding || selected.isEmpty() || unavailable || assessment.hasConflict());
        addBuildToCart.setText(adding ? "Добавляем товары…" : "Добавить товары в корзину");
        addProgress.setVisible(adding); addProgress.setManaged(adding);
        categoriesContainer.setDisable(adding); clearBuildButton.setDisable(adding || selected.isEmpty());
    }

    private void updateBudget(BigDecimal sum) {
        budgetValue.setText(budget == null ? "Не задан" : Ui.money(budget));
        budgetProgress.setVisible(budget != null); budgetProgress.setManaged(budget != null);
        budgetRemainderLabel.setText(budget == null ? "Укажите бюджет для сравнения" : sum.compareTo(budget) > 0 ? "Превышение бюджета" : "Остаток бюджета");
        remainingBudget.setText(budget == null ? "—" : Ui.money(budget.subtract(sum).abs()));
        remainingBudget.getStyleClass().remove("over-budget");
        if (budget != null) {
            budgetProgress.setProgress(Math.min(1, sum.divide(budget, 6, java.math.RoundingMode.HALF_UP).doubleValue()));
            if (sum.compareTo(budget) > 0) remainingBudget.getStyleClass().add("over-budget");
        }
        removeBudgetButton.setVisible(budget != null); removeBudgetButton.setManaged(budget != null);
    }

    private void updateCompatibility() {
        compatStatusBox.getStyleClass().removeAll("config-status-unknown", "config-status-conflict", "config-status-ok");
        String state;
        if (selected.isEmpty()) { state = "unknown"; compatTitle.setText("Начните выбор компонентов"); compatStatusText.setText("Выберите детали или откройте подбор в помощнике."); }
        else if (assessment.hasConflict()) { state = "conflict"; compatTitle.setText("Обнаружена несовместимость"); compatStatusText.setText("Замените конфликтующие детали. Причины указаны в проверках ниже."); }
        else if (assessment.status() == CompatibilityService.Status.COMPATIBLE) { state = "ok"; compatTitle.setText("Основные проверки пройдены"); compatStatusText.setText("Проверены доступные характеристики. Перед покупкой сверьте спецификации и установленную версию BIOS."); }
        else { state = "unknown"; compatTitle.setText("Нужна проверка характеристик"); compatStatusText.setText("Недостаточно данных для подтверждения совместимости. Проверьте BIOS, размеры и разъёмы питания."); }
        compatStatusBox.getStyleClass().add("config-status-" + state);
        if (selected.values().stream().anyMatch(this::unavailable)) compatStatusText.setText(compatStatusText.getText() + " Некоторые товары отсутствуют в наличии.");
        if (knowledge != null && !knowledge.warnings().isEmpty()) compatStatusText.setText(compatStatusText.getText() + "\n" + String.join("\n", knowledge.warnings()));
        findingsBox.getChildren().clear();
        var findings = assessment.findings().stream().filter(f -> !f.code().startsWith("missing_") && !f.code().equals("empty"))
                .sorted(Comparator.comparingInt(f -> f.status() == CompatibilityService.Status.INCOMPATIBLE ? 0 : 1)).toList();
        if (findings.isEmpty()) { Label label = new Label("Проверки появятся после выбора связанных компонентов."); label.setWrapText(true); label.getStyleClass().add("config-check-note"); findingsBox.getChildren().add(label); }
        for (var finding : findings) {
            boolean conflict = finding.status() == CompatibilityService.Status.INCOMPATIBLE;
            Label title = new Label(CHECK_NAMES.getOrDefault(finding.code(), "Проверка компонентов")); title.setWrapText(true); title.getStyleClass().add("config-check-title");
            String detail = conflict ? finding.message() : finding.status() == CompatibilityService.Status.COMPATIBLE ? "Проверка пройдена" : "Недостаточно проверенных данных";
            Label description = new Label(detail); description.setWrapText(true); description.getStyleClass().add("config-check-description");
            VBox text = new VBox(4, title, description); text.setMinWidth(0); HBox.setHgrow(text, Priority.ALWAYS);
            HBox line = new HBox(9, Ui.icon(conflict ? "warning" : finding.status() == CompatibilityService.Status.COMPATIBLE ? "check" : "clock"), text);
            line.getStyleClass().addAll("config-check", conflict ? "config-check-conflict" : "config-check-muted"); findingsBox.getChildren().add(line);
        }
    }

    private void renderRows() {
        categoriesContainer.getChildren().clear();
        for (ComponentCategory category : ComponentCategory.values()) {
            Product product = selected.get(category);
            StackPane thumbnail = new StackPane(product == null ? Ui.icon(iconFor(category)) : Ui.productImage(product, 52, 44));
            thumbnail.getStyleClass().add("config-component-thumb"); thumbnail.setMinSize(58, 52); thumbnail.setPrefSize(58, 52); thumbnail.setMaxSize(58, 52);
            Label kind = new Label(singular(category)); kind.getStyleClass().add("config-component-kind");
            Label name = new Label(product == null ? "Компонент не выбран" : product.getName()); name.setWrapText(true); name.getStyleClass().add("config-component-name");
            VBox copy = new VBox(5, kind, name); copy.setMinWidth(0); HBox.setHgrow(copy, Priority.ALWAYS);
            if (product != null && unavailable(product)) { Label issue = new Label("Нет в наличии"); issue.getStyleClass().add("config-row-conflict"); copy.getChildren().add(issue); }
            else if (product != null) {
                assessment.findings().stream().filter(f -> f.status() == CompatibilityService.Status.INCOMPATIBLE && relatesTo(f.code(), category))
                        .findFirst().ifPresent(f -> { Label issue = new Label(f.message()); issue.setWrapText(true); issue.getStyleClass().add("config-row-conflict"); copy.getChildren().add(issue); });
            }
            Label price = new Label(product == null ? "" : Ui.money(product.getPrice())); price.getStyleClass().add("config-component-price"); price.setMinWidth(Region.USE_PREF_SIZE);
            Button edit = new Button(product == null ? "Выбрать" : ""); edit.setGraphic(Ui.icon(product == null ? "plus" : "edit"));
            edit.setAccessibleText("Изменить: " + singular(category)); edit.setTooltip(new Tooltip("Выбрать: " + category.displayName()));
            edit.getStyleClass().addAll("btn", "config-component-edit"); edit.setOnAction(event -> openPicker(category)); edit.setMinWidth(Region.USE_PREF_SIZE);
            HBox row = new HBox(13, thumbnail, copy, price, edit); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("config-component-row");
            if (product == null) row.getStyleClass().add("config-component-empty");
            categoriesContainer.getChildren().add(row);
        }
    }

    private void openPicker(ComponentCategory category) {
        if (adding) return;
        editingCategory = category; overlayTitle.setText(category.displayName()); pickerSearch.clear(); onlyAvailable.setSelected(true);
        renderPicker(); overlayPane.setVisible(true); overlayPane.setManaged(true); mainContent.setDisable(true); hideStatus(); pickerSearch.requestFocus();
    }
    @FXML private void closePicker() { overlayPane.setVisible(false); overlayPane.setManaged(false); mainContent.setDisable(false); editingCategory = null; }
    @FXML private void clearSelection() { if (editingCategory != null) selected.remove(editingCategory); closePicker(); recalculate(); }
    private void renderPicker() {
        if (editingCategory == null) return;
        String query = pickerSearch.getText().trim().toLowerCase(Locale.ROOT);
        var matching = catalog.stream().filter(p -> ComponentCategory.fromDisplayName(p.getCategory()).orElse(null) == editingCategory)
                .filter(p -> !onlyAvailable.isSelected() || p.getStock() > 0)
                .filter(p -> (p.getName() + " " + p.getManufacturer()).toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparingDouble(Product::getPrice).thenComparingInt(Product::getId)).toList();
        overlayProductsGrid.getChildren().clear(); pickerCount.setText("Найдено: " + matching.size());
        clearSelectionBtn.setDisable(!selected.containsKey(editingCategory));
        for (Product product : matching) {
            Label name = new Label(product.getName()); name.setWrapText(true); name.getStyleClass().add("config-picker-name");
            Label price = new Label(Ui.money(product.getPrice())); price.getStyleClass().add("config-picker-price");
            Label stock = new Label(product.getStock() > 0 ? "В наличии: " + product.getStock() : "Нет в наличии"); stock.getStyleClass().add("config-picker-stock");
            Button choose = new Button("Выбрать компонент"); choose.getStyleClass().addAll("btn", "btn-primary"); choose.setMaxWidth(Double.MAX_VALUE); choose.setDisable(product.getStock() <= 0);
            choose.setOnAction(event -> { selected.put(editingCategory, product); closePicker(); recalculate(); });
            VBox card = new VBox(10, new StackPane(Ui.productImage(product, 170, 105)), name, stock, price, choose);
            card.getStyleClass().add("config-picker-card"); card.setPrefWidth(235); card.setMaxWidth(235); overlayProductsGrid.getChildren().add(card);
        }
        if (matching.isEmpty()) { Label empty = new Label(query.isEmpty() ? "В этой категории пока нет доступных товаров.\nПопробуйте снять фильтр наличия." : "Ничего не найдено. Уточните название или очистите поиск."); empty.setWrapText(true); empty.getStyleClass().add("config-picker-empty"); overlayProductsGrid.getChildren().add(empty); }
    }

    @FXML private void addBuildToCart() {
        if (adding || selected.isEmpty() || assessment.hasConflict()) return;
        String account = SessionManager.getUserId();
        if (account == null || SessionManager.getAccessToken() == null) { showStatus("Войдите в аккаунт, чтобы добавить выбранные товары в корзину.", false); return; }
        var lease = SessionManager.requireSession();
        List<Product> requested = List.copyOf(selected.values()); long id = ++operation; adding = true; recalculate(); hideStatus();
        addTask = worker.submit(() -> {
            int added = 0;
            try (var scope = com.example.authapp.network.SupabaseHttp.forSession(lease)) {
                List<Product> freshCatalog = ProductRepository.loadProductsFromSupabase();
                Map<Integer, Product> fresh = freshCatalog.stream().collect(Collectors.toMap(Product::getId, p -> p));
                List<Product> checked = new ArrayList<>(); boolean changedPrice = false;
                for (Product previous : requested) {
                    Product current = fresh.get(previous.getId());
                    if (current == null || current.getStock() <= 0) throw new IllegalStateException("Товар больше не доступен: " + previous.getName());
                    changedPrice |= BigDecimal.valueOf(current.getPrice()).compareTo(BigDecimal.valueOf(previous.getPrice())) != 0; checked.add(current);
                }
                var snapshot = new LocalKnowledgeRepository().load(checked);
                if (new CompatibilityService().assess(snapshot.components()).hasConflict()) throw new IllegalStateException("Обновлённые данные содержат конфликт. Проверьте сборку.");
                if (changedPrice) {
                    update(id, () -> { setProducts(freshCatalog); selected.clear(); checked.forEach(p -> ComponentCategory.fromDisplayName(p.getCategory()).ifPresent(c -> selected.put(c, p))); recalculate(); showStatus("Цены изменились. Проверьте новую сумму и нажмите добавление ещё раз.", false); });
                    return;
                }
                CartService service = new CartService();
                for (Product product : checked) {
                    if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Действие отменено. Проверьте корзину перед повтором.");
                    SessionManager.assertCurrent(lease);
                    service.addProductToCart(product, 1); added++;
                }
                update(id, () -> { showStatus("Выбранные товары добавлены в корзину.", true); if (host != null) host.refreshSessionUi(); });
            } catch (Exception ex) {
                int completed = added;
                update(id, () -> showStatus((completed > 0 ? "Добавлено " + completed + " из " + requested.size() + ". Проверьте корзину перед повтором. " : "") + Objects.requireNonNullElse(ex.getMessage(), "Не удалось добавить товары."), false));
            } finally { update(id, () -> { adding = false; recalculate(); }); }
        });
    }
    private void update(long id, Runnable action) { Platform.runLater(() -> { if (!disposed && id == operation) action.run(); }); }
    private void hideStatus() { cartStatusBox.setVisible(false); cartStatusBox.setManaged(false); }
    private void showStatus(String text, boolean success) {
        cartStatusText.setText(text); cartStatusBox.getStyleClass().removeAll("config-cart-success", "config-cart-error");
        cartStatusBox.getStyleClass().add(success ? "config-cart-success" : "config-cart-error"); cartStatusBox.setVisible(true); cartStatusBox.setManaged(true);
    }
    private static String singular(ComponentCategory category) { return switch (category) {
        case CPU -> "Процессор"; case MOTHERBOARD -> "Материнская плата"; case GPU -> "Видеокарта"; case RAM -> "Оперативная память";
        case STORAGE -> "SSD-накопитель"; case PSU -> "Блок питания"; case CASE -> "Корпус"; case COOLER -> "Охлаждение"; }; }
    private static String iconFor(ComponentCategory category) { return switch (category) {
        case CPU -> "cpu"; case MOTHERBOARD -> "board"; case GPU -> "gpu"; case RAM -> "memory"; case STORAGE -> "drive";
        case PSU -> "power"; case CASE -> "case"; case COOLER -> "cooler"; }; }
    private static boolean relatesTo(String code, ComponentCategory category) { return switch (category) {
        case CPU -> Set.of("socket", "cpu_support", "cooler_socket", "cooler_power", "power_budget").contains(code);
        case MOTHERBOARD -> Set.of("socket", "cpu_support", "memory_type", "memory_capacity", "memory_slots", "board_case", "storage_interface", "board_connectors").contains(code);
        case GPU -> Set.of("gpu_length", "gpu_min_psu", "gpu_connectors", "power_budget").contains(code);
        case RAM -> code.startsWith("memory_"); case STORAGE -> code.equals("storage_interface");
        case PSU -> Set.of("psu_case", "gpu_min_psu", "gpu_connectors", "board_connectors", "power_budget").contains(code);
        case CASE -> Set.of("board_case", "gpu_length", "cooler_height", "psu_case").contains(code);
        case COOLER -> code.startsWith("cooler_"); }; }
}
