package controllers;

import com.example.authapp.domain.ComponentCategory;
import com.example.authapp.domain.BuildSelection;
import com.example.authapp.domain.CatalogComponent;
import com.example.authapp.services.BuildRecommendationService;
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
    @FXML private Button applyBudgetButton, removeBudgetButton, completeBuildButton;
    @FXML private ComboBox<String> buildUsage;
    @FXML private TextField budgetField, pickerSearch;
    @FXML private CheckBox onlyAvailable;
    @FXML private StackPane overlayPane;
    @FXML private TilePane overlayProductsGrid;

    private MainController host;
    private List<Product> catalog = List.of();
    private Map<Integer, Product> currentProducts = Map.of();
    private final BuildSelection selection=new BuildSelection();
    private final Map<ComponentCategory,Product> selected=selection.products();
    private LocalKnowledgeRepository.Snapshot knowledge;
    private CompatibilityService.Result assessment;
    private ComponentCategory editingCategory;
    private BigDecimal budget;
    private boolean catalogLoaded, adding, completing, disposed;
    private long operation;
    private Future<?> pendingTask;
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
        buildUsage.getItems().setAll("Игры","Офис / учёба","Рабочие задачи");buildUsage.getSelectionModel().selectFirst();
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
        cancelCompletion();
        catalog = products.stream().filter(p -> p.getPrice().signum() >= 0).toList();
        Map<Integer, Product> fresh = new LinkedHashMap<>();
        catalog.forEach(product -> fresh.put(product.getId(), product));
        currentProducts = Map.copyOf(fresh);
        catalogLoaded = true;
        var removed=selection.refresh(fresh);
        refreshKnowledge();
        if(!removed.isEmpty())showStatus("Модель товара изменилась в каталоге. Повторно выберите: "+removed.stream().map(ComponentCategory::displayName).collect(Collectors.joining(", ")),false);
        recalculate();
        if (editingCategory != null) renderPicker();
    }

    public void selectSuggestedBuild(List<Product> products) { selectSuggestedBuild(products,Set.of()); }

    /** Apply a card atomically; its owned flags cannot move to a new model under a reused ID. */
    public boolean selectSuggestedBuild(List<Product> products,Set<Integer> ownedIds) {
        if(busy()){showStatus("Дождитесь окончания текущего действия со сборкой.",false);return false;}
        var prepared=new EnumMap<ComponentCategory,Product>(ComponentCategory.class);
        var ids=new HashSet<Integer>();
        for(var product:products) {
            var category=ComponentCategory.fromDisplayName(product.getCategory());
            if(category.isEmpty() || prepared.containsKey(category.get()) || !ids.add(product.getId())) {
                showStatus("Состав карточки неоднозначен. Выберите компоненты заново.",false);return false;
            }
            var current=currentProducts.get(product.getId());
            if(current!=null && !BuildSelection.sameIdentity(product,current)) {
                showStatus("Карточка устарела: модель в каталоге изменилась. Запросите новую сборку.",false);return false;
            }
            prepared.put(category.get(),current==null?product:current);
        }
        if(!ids.containsAll(ownedIds)){showStatus("Имеющаяся деталь отсутствует в составе карточки.",false);return false;}
        selection.clear();prepared.forEach(selection::select);
        prepared.forEach((category,product)->{if(ownedIds.contains(product.getId()))selection.setOwned(category,true);});
        refreshKnowledge();hideStatus();recalculate();return true;
    }

    public void setBudget(BigDecimal value) {
        cancelCompletion();
        if (value != null && (value.signum() <= 0 || value.compareTo(new BigDecimal("100000000")) > 0))
            throw new IllegalArgumentException("Бюджет должен быть больше нуля и не более 100 млн рублей");
        budget = value; updateBudget(total());
    }
    public BigDecimal getBudget() { return budget; }
    public List<Product> getSelectedProducts() { return selection.allProducts(); }
    public Set<ComponentCategory> getOwnedCategories() { return selection.ownedCategories(); }
    public void setActive(boolean active) { if (!active) {cancelCompletion();closePicker();} }
    public void dispose() { disposed = true; operation++; if (pendingTask != null) pendingTask.cancel(true); worker.shutdownNow(); }

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
    @FXML private void clearBuild() { if (!busy()) { selection.clear(); hideStatus(); recalculate(); } }
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

    private boolean busy() { return adding || completing; }
    private void cancelCompletion() {
        if(!completing)return;
        operation++;if(pendingTask!=null)pendingTask.cancel(true);completing=false;recalculate();
    }
    @FXML private void completeBuild() {
        if(busy() || disposed)return;
        if(budget==null){showStatus("Сначала укажите бюджет покупки. Имеющиеся детали не входят в эту сумму.",false);return;}
        if(selection.purchases().stream().anyMatch(this::unavailable)){showStatus("Сначала замените недоступные покупаемые детали или отметьте их как имеющиеся.",false);return;}
        var owned=new EnumMap<ComponentCategory,CatalogComponent>(ComponentCategory.class);
        var fixed=new EnumMap<ComponentCategory,Integer>(ComponentCategory.class);selected.forEach((category,p)->fixed.put(category,p.getId()));
        for(var c:knowledge.components())if(selection.isOwned(c.category()) && Objects.equals(fixed.get(c.category()),c.productId()))owned.put(c.category(),c);
        var candidates=knowledge.components().stream().filter(c->!fixed.containsKey(c.category()) || fixed.get(c.category())==c.productId()).toList();
        var usage=switch(buildUsage.getSelectionModel().getSelectedIndex()){case 1->BuildRecommendationService.Usage.OFFICE;case 2->BuildRecommendationService.Usage.WORKSTATION;default->BuildRecommendationService.Usage.GAMING;};
        var base=BuildRecommendationService.Request.forUsage(budget,usage);
        var request=new BuildRecommendationService.Request(budget,usage,base.minRamGb(),base.minStorageGb(),owned,false);
        var models=new HashMap<Integer,Product>(currentProducts);selection.allProducts().forEach(p->models.putIfAbsent(p.getId(),p));
        long id=++operation;completing=true;hideStatus();recalculate();
        pendingTask=worker.submit(()->{
            try {
                var result=new BuildRecommendationService().recommend(candidates,request);
                update(id,()->{
                    if(result.components().isEmpty()){showStatus(String.join(" ",result.explanations()),false);return;}
                    for(var component:result.components())if(!selected.containsKey(component.category()))selection.select(component.category(),models.get(component.productId()));
                    refreshKnowledge();recalculate();
                    showStatus(result.status()==BuildRecommendationService.Status.READY?"Недостающие детали подобраны. Имеющиеся детали не включены в покупку.":"Подобран черновой состав. Проверьте неизвестные характеристики перед покупкой.",result.status()==BuildRecommendationService.Status.READY);
                });
            }catch(Exception ex){update(id,()->showStatus("Не удалось дополнить сборку. "+Objects.toString(ex.getMessage(),"Попробуйте снова."),false));}
            finally{update(id,()->{completing=false;recalculate();});}
        });
    }
    private void refreshKnowledge() {
        var combined=new LinkedHashMap<Integer,Product>();catalog.forEach(p->combined.put(p.getId(),p));
        selection.allProducts().forEach(p->combined.putIfAbsent(p.getId(),p));
        knowledge=new LocalKnowledgeRepository().load(combined.values());
    }
    private BigDecimal total() { return selection.purchaseTotal(); }
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
        boolean unavailable = selection.purchases().stream().anyMatch(this::unavailable);
        addBuildToCart.setDisable(busy() || selection.purchases().isEmpty() || unavailable || assessment.hasConflict());
        addBuildToCart.setText(adding ? "Добавляем товары…" : "Добавить товары в корзину");
        addProgress.setVisible(busy()); addProgress.setManaged(busy());
        completeBuildButton.setDisable(busy() || selected.size()==8 || catalog.isEmpty());
        completeBuildButton.setText(completing?"Подбираем…":"Подобрать остальные");buildUsage.setDisable(busy());
        categoriesContainer.setDisable(busy()); clearBuildButton.setDisable(busy() || selected.isEmpty());
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
        if (selection.purchases().stream().anyMatch(this::unavailable)) compatStatusText.setText(compatStatusText.getText() + " Некоторые товары отсутствуют в наличии.");
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
            if (product != null && !selection.isOwned(category) && unavailable(product)) { Label issue = new Label("Нет в наличии"); issue.getStyleClass().add("config-row-conflict"); copy.getChildren().add(issue); }
            else if (product != null) {
                assessment.findings().stream().filter(f -> f.status() == CompatibilityService.Status.INCOMPATIBLE && relatesTo(f.code(), category))
                        .findFirst().ifPresent(f -> { Label issue = new Label(f.message()); issue.setWrapText(true); issue.getStyleClass().add("config-row-conflict"); copy.getChildren().add(issue); });
            }
            if(product!=null){
                CheckBox owned=new CheckBox("У меня уже есть");owned.setId("owned-"+category.name());owned.setWrapText(true);owned.setMinWidth(0);owned.setMaxWidth(Double.MAX_VALUE);owned.setSelected(selection.isOwned(category));
                owned.setAccessibleText("У меня уже есть: "+product.getName());owned.getStyleClass().add("config-owned-toggle");
                owned.setOnAction(event->{selection.setOwned(category,owned.isSelected());refreshKnowledge();recalculate();});copy.getChildren().add(owned);
            }
            Label price = new Label(product == null ? "" : selection.isOwned(category)?"Не покупаем":Ui.money(product.getPrice())); price.getStyleClass().add("config-component-price"); price.setMinWidth(Region.USE_PREF_SIZE);
            Button edit = new Button(product == null ? "Выбрать" : ""); edit.setGraphic(Ui.icon(product == null ? "plus" : "edit"));
            edit.setAccessibleText("Изменить: " + singular(category)); edit.setTooltip(new Tooltip("Выбрать: " + category.displayName()));
            edit.getStyleClass().addAll("btn", "config-component-edit"); edit.setOnAction(event -> openPicker(category)); edit.setMinWidth(Region.USE_PREF_SIZE);
            HBox row = new HBox(13, thumbnail, copy, price, edit); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("config-component-row");
            if (product == null) row.getStyleClass().add("config-component-empty");
            categoriesContainer.getChildren().add(row);
        }
    }

    private void openPicker(ComponentCategory category) {
        if (busy()) return;
        editingCategory = category; overlayTitle.setText(category.displayName()); pickerSearch.clear(); onlyAvailable.setSelected(true);
        renderPicker(); overlayPane.setVisible(true); overlayPane.setManaged(true); mainContent.setDisable(true); hideStatus(); pickerSearch.requestFocus();
    }
    @FXML private void closePicker() { overlayPane.setVisible(false); overlayPane.setManaged(false); mainContent.setDisable(false); editingCategory = null; }
    @FXML private void clearSelection() { if (editingCategory != null) selection.remove(editingCategory); closePicker(); recalculate(); }
    private void renderPicker() {
        if (editingCategory == null) return;
        String query = pickerSearch.getText().trim().toLowerCase(Locale.ROOT);
        var matching = catalog.stream().filter(p -> ComponentCategory.fromDisplayName(p.getCategory()).orElse(null) == editingCategory)
                .filter(p -> !onlyAvailable.isSelected() || p.getStock() > 0)
                .filter(p -> (p.getName() + " " + p.getManufacturer()).toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(Product::getPrice).thenComparingInt(Product::getId)).toList();
        overlayProductsGrid.getChildren().clear(); pickerCount.setText("Найдено: " + matching.size());
        clearSelectionBtn.setDisable(!selected.containsKey(editingCategory));
        for (Product product : matching) {
            Label name = new Label(product.getName()); name.setWrapText(true); name.getStyleClass().add("config-picker-name");
            Label price = new Label(Ui.money(product.getPrice())); price.getStyleClass().add("config-picker-price");
            Label stock = new Label(product.getStock() > 0 ? "В наличии: " + product.getStock() : "Нет в наличии"); stock.getStyleClass().add("config-picker-stock");
            Button choose = new Button("Выбрать компонент"); choose.getStyleClass().addAll("btn", "btn-primary"); choose.setMaxWidth(Double.MAX_VALUE);
            choose.setOnAction(event -> { selection.select(editingCategory, product); refreshKnowledge();closePicker(); recalculate(); });
            VBox card = new VBox(10, new StackPane(Ui.productImage(product, 170, 105)), name, stock, price, choose);
            card.getStyleClass().add("config-picker-card"); card.setPrefWidth(235); card.setMaxWidth(235); overlayProductsGrid.getChildren().add(card);
        }
        if (matching.isEmpty()) { Label empty = new Label(query.isEmpty() ? "В этой категории пока нет доступных товаров.\nПопробуйте снять фильтр наличия." : "Ничего не найдено. Уточните название или очистите поиск."); empty.setWrapText(true); empty.getStyleClass().add("config-picker-empty"); overlayProductsGrid.getChildren().add(empty); }
    }

    @FXML private void addBuildToCart() {
        if (busy() || selection.purchases().isEmpty() || assessment.hasConflict()) return;
        String account = SessionManager.getUserId();
        if (account == null || SessionManager.getAccessToken() == null) { showStatus("Войдите в аккаунт, чтобы добавить выбранные товары в корзину.", false); return; }
        var lease = SessionManager.requireSession();
        List<Product> requested = selection.purchases();List<Product> owned=selection.ownedProducts(); long id = ++operation; adding = true; recalculate(); hideStatus();
        pendingTask = worker.submit(() -> {
            int added = 0;
            try (var scope = com.example.authapp.network.SupabaseHttp.forSession(lease)) {
                List<Product> freshCatalog = ProductRepository.loadProductsFromSupabase();
                Map<Integer, Product> fresh = freshCatalog.stream().collect(Collectors.toMap(Product::getId, p -> p));
                List<Product> checked = new ArrayList<>(); boolean changedPrice = false;
                for (Product previous : requested) {
                    Product current = fresh.get(previous.getId());
                    if (current == null || !BuildSelection.sameIdentity(previous,current) || current.getStock() <= 0) throw new IllegalStateException("Товар больше не доступен: " + previous.getName());
                    changedPrice |= current.getPrice().compareTo(previous.getPrice()) != 0; checked.add(current);
                }
                var complete=new ArrayList<>(owned);complete.addAll(checked);
                var snapshot = new LocalKnowledgeRepository().load(complete);
                if (new CompatibilityService().assess(snapshot.components()).hasConflict()) throw new IllegalStateException("Обновлённые данные содержат конфликт. Проверьте сборку.");
                if (changedPrice) {
                    update(id, () -> { setProducts(freshCatalog); recalculate(); showStatus("Цены изменились. Проверьте новую сумму и нажмите добавление ещё раз.", false); });
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
