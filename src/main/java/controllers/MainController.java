package controllers;

import com.example.authapp.models.Cart;
import com.example.authapp.models.Product;
import com.example.authapp.domain.BuildSelection;
import com.example.authapp.network.SupabaseHttp;
import com.example.authapp.repositories.ProductRepository;
import com.example.authapp.services.CartService;
import com.example.authapp.services.LocalKnowledgeRepository;
import com.example.authapp.ui.CatalogQuery;
import com.example.authapp.ui.Ui;
import config.SessionManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

/** Persistent application shell. Navigation never removes the sidebar or header. */
public class MainController {
    @FXML private StackPane shellRoot;
    @FXML private BorderPane mainPane;
    @FXML private VBox categoryPane, staffNav, sidebarHelp;
    @FXML private HBox headerPane, searchBox;
    @FXML private Button catalogBtn,configuratorBtn,chatBtn,cartBtn,profileBtn,accountBtn,adminBtn,managerBtn;
    @FXML private Label sectionName,toastLabel;
    @FXML private TextField searchField;
    private final ExecutorService io=Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r,"store-io");t.setDaemon(true);return t;});
    private final Map<String,View> cache=new HashMap<>();
    private final Set<String> selectedManufacturers=new HashSet<>();
    private final PauseTransition notificationTimer=new PauseTransition(Duration.seconds(6));
    private final PauseTransition searchTimer=new PauseTransition(Duration.millis(140));
    private List<Product> allProducts=List.of();
    private List<Product> visibleProducts=List.of();
    private String category="Все",active="catalog",sessionOwner;
    private int visibleLimit=12,loadVersion;
    private boolean disposed,catalogLoaded,refreshing;
    private String catalogError;
    private ScrollPane catalogView;
    private TilePane productPane;
    private VBox brandsBox,stateBox,filterPane;
    private FlowPane categoryTabs;
    private Label resultCount;
    private Button showMore;
    private TextField minPrice,maxPrice;
    private CheckBox onlyInStock;
    private ComboBox<CatalogQuery.Sort> sort;
    private ChatController chatController;
    private ConfiguratorController configuratorController;
    private record View(Parent node,Object controller){}

    @FXML private void initialize(){
        sessionOwner=SessionManager.getUserId();
        shellRoot.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED,e->{if(e.getCode()==javafx.scene.input.KeyCode.TAB)shellRoot.pseudoClassStateChanged(PseudoClass.getPseudoClass("keyboard-navigation"),true);});
        shellRoot.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED,e->shellRoot.pseudoClassStateChanged(PseudoClass.getPseudoClass("keyboard-navigation"),false));
        catalogBtn.setGraphic(Ui.icon("grid"));configuratorBtn.setGraphic(Ui.icon("layers"));chatBtn.setGraphic(Ui.icon("chat"));
        cartBtn.setGraphic(Ui.icon("bag"));profileBtn.setGraphic(Ui.icon("user"));accountBtn.setGraphic(Ui.icon("user"));
        managerBtn.setGraphic(Ui.icon("board"));adminBtn.setGraphic(Ui.icon("layers"));searchBox.getChildren().add(0,Ui.icon("search"));
        catalogBtn.setOnAction(e->showMainContent());configuratorBtn.setOnAction(e->openConfigurator());chatBtn.setOnAction(e->openChat());
        cartBtn.setOnAction(e->openCart());profileBtn.setOnAction(e->openCabinetInMain());accountBtn.setOnAction(e->openCabinetInMain());
        adminBtn.setOnAction(e->openAdminPanel());managerBtn.setOnAction(e->openManagerPanel());
        notificationTimer.setOnFinished(e->{toastLabel.setVisible(false);toastLabel.setManaged(false);});
        searchTimer.setOnFinished(e->{if(!active.equals("catalog"))showMainContent();applyFilters();});
        searchField.textProperty().addListener((o,a,b)->searchTimer.playFromStart());
        searchField.setOnAction(e->{searchTimer.stop();showMainContent();applyFilters();});
        shellRoot.widthProperty().addListener((o,a,b)->resize(b.doubleValue()));
        catalogView=createCatalog();showMainContent();reloadProducts();
    }
    private void resize(double width){
        boolean compact=width>0&&width<1150;
        shellRoot.pseudoClassStateChanged(PseudoClass.getPseudoClass("compact"),compact);
        categoryPane.setMinWidth(compact?180:220);categoryPane.setPrefWidth(compact?180:220);categoryPane.setMaxWidth(compact?180:220);
        sidebarHelp.setVisible(!compact);sidebarHelp.setManaged(!compact);
    }
    private ScrollPane createCatalog(){
        VBox page=new VBox(22);page.getStyleClass().add("catalog-page");page.setMinWidth(0);
        Button assistant=new Button("Подобрать с помощником",Ui.icon("spark"));assistant.getStyleClass().addAll("btn","btn-primary");assistant.setOnAction(e->openChat());
        page.getChildren().add(pageHeading("ВСЁ ДЛЯ ВАШЕЙ СБОРКИ","Каталог комплектующих","Подберите детали, которые работают вместе.",assistant));
        categoryTabs=new FlowPane(7,7);categoryTabs.getStyleClass().add("category-tabs");page.getChildren().add(categoryTabs);
        filterPane=new VBox(17);filterPane.getStyleClass().add("catalog-filters");filterPane.setPrefWidth(164);filterPane.setMinWidth(140);filterPane.setMaxWidth(164);
        Label filtersTitle=Ui.label("Фильтры","section-title");filterPane.getChildren().add(filtersTitle);
        minPrice=new TextField();minPrice.setPromptText("От");minPrice.setId("priceMin");minPrice.setAccessibleText("Минимальная цена, рублей");
        maxPrice=new TextField();maxPrice.setPromptText("До");maxPrice.setId("priceMax");maxPrice.setAccessibleText("Максимальная цена, рублей");
        HBox priceInputs=new HBox(7,minPrice,maxPrice);minPrice.setMinWidth(0);maxPrice.setMinWidth(0);HBox.setHgrow(minPrice,Priority.ALWAYS);HBox.setHgrow(maxPrice,Priority.ALWAYS);
        minPrice.textProperty().addListener((o,a,b)->applyFilters());maxPrice.textProperty().addListener((o,a,b)->applyFilters());
        brandsBox=new VBox(10);ScrollPane brandScroll=new ScrollPane(brandsBox);brandScroll.setFitToWidth(true);brandScroll.setMaxHeight(240);brandScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        onlyInStock=new CheckBox("Только в наличии");onlyInStock.setId("onlyInStock");onlyInStock.setOnAction(e->applyFilters());
        Button reset=new Button("Сбросить фильтры");reset.getStyleClass().addAll("btn-ghost","btn-small");reset.setId("resetFilters");reset.setOnAction(e->resetFilters());
        filterPane.getChildren().addAll(Ui.label("Цена, ₽","filter-heading"),priceInputs,new Separator(),Ui.label("Производитель","filter-heading"),brandScroll,new Separator(),onlyInStock,reset);
        resultCount=Ui.label("Загружаем каталог…","text-muted");resultCount.setId("resultCount");
        sort=new ComboBox<>();sort.getItems().setAll(CatalogQuery.Sort.values());sort.setValue(CatalogQuery.Sort.DEFAULT);sort.setId("catalogSort");sort.getStyleClass().add("catalog-sort");sort.setOnAction(e->applyFilters());
        Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);HBox results=new HBox(12,resultCount,spacer,sort);results.setAlignment(Pos.CENTER_LEFT);
        productPane=new TilePane(16,16);productPane.setId("productPane");productPane.setPrefColumns(3);productPane.setMinWidth(0);productPane.getStyleClass().add("product-grid");
        productPane.widthProperty().addListener((o,a,b)->resizeGrid(b.doubleValue()));
        stateBox=new VBox(10);stateBox.setId("catalogState");stateBox.getStyleClass().add("catalog-state");stateBox.setAlignment(Pos.CENTER);
        showMore=new Button("Показать ещё");showMore.setId("showMore");showMore.setMaxWidth(Double.MAX_VALUE);showMore.setOnAction(e->{visibleLimit+=12;renderProducts();});
        VBox productsColumn=new VBox(16,results,stateBox,productPane,showMore);productsColumn.setMinWidth(0);HBox.setHgrow(productsColumn,Priority.ALWAYS);
        HBox body=new HBox(24,filterPane,productsColumn);body.setAlignment(Pos.TOP_LEFT);page.getChildren().add(body);
        ScrollPane scroll=new ScrollPane(page);scroll.setId("contentScroll");scroll.setFitToWidth(true);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);scroll.setMinWidth(0);return scroll;
    }
    private HBox pageHeading(String eyebrow,String title,String subtitle,Node action){
        VBox text=new VBox(7,Ui.label(eyebrow,"eyebrow"),Ui.label(title,"page-title"),Ui.label(subtitle,"page-subtitle"));
        text.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast).forEach(l->l.setWrapText(true));text.setMinWidth(0);HBox.setHgrow(text,Priority.ALWAYS);
        HBox heading=new HBox(18,text);heading.setAlignment(Pos.CENTER_LEFT);if(action!=null)heading.getChildren().add(action);heading.getStyleClass().add("page-heading");return heading;
    }
    private void updateCategories(){
        categoryTabs.getChildren().clear();LinkedHashSet<String> categories=new LinkedHashSet<>();categories.add("Все");
        for(String standard:List.of("Процессоры","Видеокарты","Материнские платы","Оперативная память","SSD-накопители","Блоки питания","Корпуса","Охлаждение"))
            if(allProducts.stream().anyMatch(p->standard.equalsIgnoreCase(Objects.toString(p.getCategory(),""))))categories.add(standard);
        allProducts.stream().map(Product::getCategory).filter(Objects::nonNull).filter(s->!s.isBlank()).sorted(String.CASE_INSENSITIVE_ORDER).forEach(value->{if(categories.stream().noneMatch(c->c.equalsIgnoreCase(value)))categories.add(value);});
        if(!categories.contains(category))category="Все";
        for(String value:categories){Button button=new Button(value.equals("Все")?"Все комплектующие":value);button.getStyleClass().add("category-tab");button.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"),value.equals(category));button.setOnAction(e->{category=value;visibleLimit=12;updateCategories();applyFilters();});categoryTabs.getChildren().add(button);}
        selectedManufacturers.retainAll(allProducts.stream().map(Product::getManufacturer).filter(Objects::nonNull).toList());
        brandsBox.getChildren().clear();
        allProducts.stream().map(Product::getManufacturer).filter(Objects::nonNull).filter(s->!s.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(value->{CheckBox check=new CheckBox(value);check.setSelected(selectedManufacturers.contains(value));check.setOnAction(e->{if(check.isSelected())selectedManufacturers.add(value);else selectedManufacturers.remove(value);applyFilters();});brandsBox.getChildren().add(check);});
        if(brandsBox.getChildren().isEmpty())brandsBox.getChildren().add(Ui.label("Не указаны","text-muted"));
    }
    private BigDecimal price(TextField field){
        String value=field.getText().replaceAll("[\\s\\u00a0\\u202f]","").replace(',','.');if(value.isBlank())return null;
        try{BigDecimal result=new BigDecimal(value);if(result.signum()<0)throw new NumberFormatException();field.pseudoClassStateChanged(PseudoClass.getPseudoClass("invalid"),false);return result;}
        catch(NumberFormatException e){field.pseudoClassStateChanged(PseudoClass.getPseudoClass("invalid"),true);throw new IllegalArgumentException("Цена должна быть положительным числом");}
    }
    private void applyFilters(){
        if(productPane==null)return;
        try{CatalogQuery query=new CatalogQuery(searchField.getText(),category,selectedManufacturers,price(minPrice),price(maxPrice),onlyInStock.isSelected(),sort.getValue());visibleProducts=query.apply(allProducts);renderProducts();}
        catch(IllegalArgumentException e){productPane.getChildren().clear();resultCount.setText("Проверьте фильтры");showState("Проверьте диапазон цены",e.getMessage(),null);showMore.setManaged(false);showMore.setVisible(false);}
    }
    private void resetFilters(){category="Все";selectedManufacturers.clear();minPrice.clear();maxPrice.clear();onlyInStock.setSelected(false);searchField.clear();sort.setValue(CatalogQuery.Sort.DEFAULT);visibleLimit=12;updateCategories();applyFilters();}
    private void renderProducts(){
        productPane.getChildren().clear();resultCount.setText(visibleProducts.size()+" товаров"+(refreshing?" · обновляем…":""));
        if(!catalogLoaded&&refreshing)showState("Загружаем каталог","Помощник и справочник уже доступны.",null);
        else if(catalogError!=null && allProducts.isEmpty()){Button retry=new Button("Повторить загрузку");retry.getStyleClass().add("btn-primary");retry.setOnAction(e->reloadProducts());showState("Каталог пока недоступен",catalogError,retry);}
        else if(visibleProducts.isEmpty())showState(allProducts.isEmpty()?"Каталог пока пуст":"По вашему запросу ничего не найдено",allProducts.isEmpty()?"Товары появятся после наполнения каталога.":"Попробуйте другую категорию или измените фильтры.",null);
        else{stateBox.setVisible(false);stateBox.setManaged(false);visibleProducts.stream().limit(visibleLimit).map(this::productCard).forEach(productPane.getChildren()::add);}
        showMore.setManaged(visibleProducts.size()>visibleLimit);showMore.setVisible(visibleProducts.size()>visibleLimit);showMore.setText("Показать ещё "+Math.min(12,Math.max(0,visibleProducts.size()-visibleLimit)));
        resizeGrid(productPane.getWidth());
    }
    private void showState(String title,String detail,Node action){stateBox.getChildren().setAll(Ui.icon(catalogError!=null?"warning":"search"),Ui.label(title,"section-title"));Label label=Ui.label(detail,"text-muted");label.setWrapText(true);label.setMaxWidth(430);stateBox.getChildren().add(label);if(action!=null)stateBox.getChildren().add(action);stateBox.setVisible(true);stateBox.setManaged(true);}
    private void resizeGrid(double width){if(width<=0)return;double minimum=width<600?215:265;int columns=Math.max(1,Math.min(4,(int)((width+16)/(minimum+16))));double tile=Math.max(160,Math.floor((width-16*(columns-1)-3)/columns));productPane.setPrefColumns(columns);productPane.setPrefTileWidth(tile);for(Node node:productPane.getChildren())if(node instanceof Region region){region.setPrefWidth(tile);region.setMaxWidth(tile);}}
    private VBox productCard(Product product){
        VBox card=new VBox(8);card.getStyleClass().add("product-card");card.setId("product-"+product.getId());card.setMinWidth(0);card.setPrefHeight(330);
        Button image=new Button();image.setGraphic(Ui.productImage(product,260,150));image.getStyleClass().add("product-art-button");image.setMaxWidth(Double.MAX_VALUE);image.setOnAction(e->openProduct(product));
        Label type=Ui.label(Objects.toString(product.getCategory(),"Комплектующие"),"product-category");
        Button name=new Button(Objects.toString(product.getName(),"Без названия"));name.setWrapText(true);name.setMaxWidth(Double.MAX_VALUE);name.setMinHeight(44);name.setAlignment(Pos.TOP_LEFT);name.getStyleClass().add("product-name");name.setOnAction(e->openProduct(product));
        Label manufacturer=Ui.label(Objects.toString(product.getManufacturer(),""),"product-maker");Label stock=Ui.label(product.getStock()>0?"В наличии":"Нет в наличии","product-stock");stock.pseudoClassStateChanged(PseudoClass.getPseudoClass("unavailable"),product.getStock()<=0);
        Region gap=new Region();VBox.setVgrow(gap,Priority.ALWAYS);Label price=Ui.label(Ui.money(product.getPrice()),"product-price");Region space=new Region();HBox.setHgrow(space,Priority.ALWAYS);
        Button add=new Button("",Ui.icon("bag"));add.setAccessibleText("Добавить "+product.getName()+" в корзину");add.getStyleClass().addAll("btn-primary","product-add");add.setDisable(product.getStock()<=0);add.setOnAction(e->addProductToCart(product,1));
        HBox bottom=new HBox(8,price,space,add);bottom.setAlignment(Pos.CENTER_LEFT);VBox copy=new VBox(7,type,name,manufacturer,stock,gap,bottom);copy.getStyleClass().add("product-copy");VBox.setVgrow(copy,Priority.ALWAYS);card.getChildren().addAll(image,copy);return card;
    }
    public void reloadProducts(){
        if(!Platform.isFxApplicationThread()){Platform.runLater(this::reloadProducts);return;}if(disposed)return;
        int version=++loadVersion;refreshing=true;applyFilters();
        io.submit(()->{try{List<Product> loaded=List.copyOf(ProductRepository.loadProductsFromSupabase());Platform.runLater(()->{if(disposed||version!=loadVersion)return;allProducts=loaded;refreshing=false;catalogLoaded=true;catalogError=null;updateCategories();applyFilters();if(chatController!=null)chatController.updateCatalog(loaded);if(configuratorController!=null)configuratorController.setProducts(loaded);});}
        catch(Exception e){Platform.runLater(()->{if(disposed||version!=loadVersion)return;refreshing=false;catalogLoaded=true;catalogError="Не удалось получить товары. Проверьте подключение и повторите попытку.";applyFilters();if(!allProducts.isEmpty())notifyUser(catalogError);});}});
    }
    private View loadView(String name) throws java.io.IOException{
        View existing=cache.get(name);if(existing!=null)return existing;
        FXMLLoader loader=new FXMLLoader(getClass().getResource("/views/"+name+".fxml"));Parent node=loader.load();Ui.theme(node);View view=new View(node,loader.getController());cache.put(name,view);return view;
    }
    private void switchView(Parent node,String title,String key){
        cache.forEach((name,view)->setActive(view.controller(),name.equals(key)));
        active=key;mainPane.setCenter(node);sectionName.setText(title);
        Map<Button,String> nav=new LinkedHashMap<>();nav.put(catalogBtn,"catalog");nav.put(configuratorBtn,"configurator");nav.put(chatBtn,"chat");nav.put(cartBtn,"cart");nav.put(profileBtn,"cabinet");nav.put(adminBtn,"admin");nav.put(managerBtn,"manager");
        nav.forEach((button,target)->button.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"),target.equals(key)));refreshSessionUi();
    }
    public void showMainContent(){if(!Platform.isFxApplicationThread()){Platform.runLater(this::showMainContent);return;}if(catalogView!=null){switchView(catalogView,"Каталог","catalog");applyFilters();}}
    public void openChat(){refreshSessionUi();try{View view=loadView("chat");if(chatController==null){chatController=(ChatController)view.controller();chatController.configure(this,allProducts);}switchView(view.node(),"Помощник","chat");}catch(Exception e){notifyUser("Не удалось открыть помощника. Попробуйте снова.");}}
    public void openConfigurator(){refreshSessionUi();try{View view=loadView("configurator");configuratorController=(ConfiguratorController)view.controller();configuratorController.setMainController(this);configuratorController.setProducts(allProducts);view.node().setUserData(configuratorController);switchView(view.node(),"Конфигуратор","configurator");}catch(Exception e){notifyUser("Не удалось открыть конфигуратор.");}}
    public void openSuggestedBuild(List<Product> products){openSuggestedBuild(products,null);}
    public void openSuggestedBuild(List<Product> products,BigDecimal budget){openSuggestedBuild(products,budget,Set.of());}
    public void openSuggestedBuild(List<Product> products,BigDecimal budget,Set<Integer> ownedIds){openConfigurator();if(configuratorController!=null && configuratorController.selectSuggestedBuild(products,ownedIds)){if(budget!=null)configuratorController.setBudget(budget);}}
    public void openCart(){refreshSessionUi();if(!authorized()){openAuth();notifyUser("Войдите в аккаунт, чтобы открыть корзину.");return;}try{View view=loadView("cart");((CartController)view.controller()).setMainController(this);switchView(view.node(),"Корзина","cart");}catch(Exception e){notifyUser("Не удалось открыть корзину.");}}
    private void openAuth(){refreshSessionUi();try{AuthController.setMainController(this);View view=loadView("auth");switchView(view.node(),"Вход в аккаунт","auth");}catch(Exception e){notifyUser("Не удалось открыть форму входа.");}}
    public void openCabinetInMain(){refreshSessionUi();if(!authorized()){openAuth();return;}try{CabinetController.setHostMainController(this);View view=loadView("cabinet");switchView(view.node(),"Личный кабинет","cabinet");}catch(Exception e){notifyUser("Не удалось открыть личный кабинет.");}}
    public void openAdminPanel(){refreshSessionUi();if(!SessionManager.isAdmin()){notifyUser("Для этого раздела нужны права администратора.");return;}try{View view=loadView("admin");((AdminController)view.controller()).setMainController(this);((AdminController)view.controller()).setCabinetController(null);switchView(view.node(),"Администрирование","admin");}catch(Exception e){notifyUser("Не удалось открыть управление.");}}
    public void openManagerPanel(){refreshSessionUi();if(!SessionManager.isManager()&&!SessionManager.isAdmin()){notifyUser("Для этого раздела нужны права менеджера.");return;}try{View view=loadView("manager");((ManagerController)view.controller()).setMainController(this);switchView(view.node(),"Управление товарами","manager");}catch(Exception e){notifyUser("Не удалось открыть управление товарами.");}}
    public void hideCategoriesAndSearch(){ /* Persistent shell intentionally remains visible. */ }
    public void showCategoriesAndSearch(){ /* Compatibility with existing child controllers. */ }
    private boolean authorized(){return SessionManager.getAccessToken()!=null&&SessionManager.getUserId()!=null;}
    public void refreshSessionUi(){
        if(!Platform.isFxApplicationThread()){Platform.runLater(this::refreshSessionUi);return;}
        String current=SessionManager.getUserId();
        if(!Objects.equals(current,sessionOwner)){
            clearPrivateViews();Cart.getInstance().clear();sessionOwner=current;
            if(!active.equals("catalog")&&!active.equals("configurator")){active="catalog";mainPane.setCenter(catalogView);sectionName.setText("Каталог");for(Button b:List.of(catalogBtn,configuratorBtn,chatBtn,cartBtn,profileBtn,adminBtn,managerBtn))b.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"),b==catalogBtn);}
        }
        String email=SessionManager.getUserEmail();accountBtn.setText(email==null?"Гость":email.split("@")[0]);
        boolean admin=SessionManager.isAdmin(),manager=SessionManager.isManager();staffNav.setVisible(admin||manager);staffNav.setManaged(admin||manager);adminBtn.setVisible(admin);adminBtn.setManaged(admin);managerBtn.setVisible(admin||manager);managerBtn.setManaged(admin||manager);
        cartBtn.setText("Корзина"+(Cart.getInstance().getTotalQuantity()>0?" · "+Cart.getInstance().getTotalQuantity():""));
    }
    private void setActive(Object controller,boolean value){
        if(controller instanceof ChatController c)c.setActive(value);
        else if(controller instanceof ConfiguratorController c)c.setActive(value);
        else if(controller instanceof CartController c)c.setActive(value);
        else if(controller instanceof CabinetController c)c.setActive(value);
        else if(controller instanceof AuthController c)c.setActive(value);
        else if(controller instanceof AdminController c)c.setActive(value);
        else if(controller instanceof ManagerController c)c.setActive(value);
    }
    private void disposeView(Object controller){
        if(controller instanceof ChatController c)c.dispose();
        else if(controller instanceof ConfiguratorController c)c.dispose();
        else if(controller instanceof CartController c)c.dispose();
        else if(controller instanceof CabinetController c)c.dispose();
        else if(controller instanceof AuthController c)c.dispose();
        else if(controller instanceof AdminController c)c.dispose();
        else if(controller instanceof ManagerController c)c.dispose();
    }
    private void clearPrivateViews(){
        for(String name:List.of("chat","cart","cabinet","admin","manager","auth")){View view=cache.remove(name);if(view!=null)disposeView(view.controller());}
        if(sessionOwner!=null){View configured=cache.remove("configurator");if(configured!=null)disposeView(configured.controller());configuratorController=null;}
        chatController=null;
    }
    public void onLogout(){SessionManager.clearSession();clearPrivateViews();Cart.getInstance().clear();sessionOwner=null;showMainContent();notifyUser("Вы вышли из аккаунта.");}
    public void notifyUser(String text){if(!Platform.isFxApplicationThread()){Platform.runLater(()->notifyUser(text));return;}toastLabel.setText(text);toastLabel.setVisible(true);toastLabel.setManaged(true);notificationTimer.playFromStart();refreshSessionUi();}
    public void addProductToCart(Product product,int quantity){
        if(!authorized()){openAuth();notifyUser("Войдите в аккаунт, затем добавьте выбранный товар.");return;}
        var lease=SessionManager.requireSession();notifyUser("Проверяем цену и наличие…");
        io.submit(()->{try(var scope=SupabaseHttp.forSession(lease)){Product fresh=ProductRepository.loadProductsFromSupabase().stream().filter(p->p.getId()==product.getId()).findFirst().orElseThrow(()->new IllegalStateException("Товар больше не доступен"));
            SessionManager.assertCurrent(lease);
            if(!BuildSelection.sameIdentity(product,fresh))throw new IllegalStateException("Данные товара изменились. Обновите каталог и повторно выберите нужную модель.");
            if(fresh.getStock()<quantity)throw new IllegalStateException("Недостаточно товара в наличии");
            if(fresh.getPrice().compareTo(product.getPrice())!=0){Platform.runLater(()->{if(disposed||!lease.equals(SessionManager.captureSession()))return;openProduct(fresh);notifyUser("Цена изменилась. Проверьте её и добавьте товар снова.");});return;}
            new CartService().addProductToCart(fresh,quantity);Platform.runLater(()->{if(!disposed&&lease.equals(SessionManager.captureSession()))notifyUser(fresh.getName()+" добавлен в корзину.");});
        }catch(Exception e){Platform.runLater(()->{if(!disposed&&lease.equals(SessionManager.captureSession()))notifyUser(Objects.requireNonNullElse(e.getMessage(),"Не удалось добавить товар."));});}});
    }
    public void openProduct(Product product){
        VBox page=new VBox(24);page.getStyleClass().add("catalog-page");Button back=new Button("К каталогу",Ui.icon("arrow-right"));back.getStyleClass().add("btn-ghost");back.setOnAction(e->showMainContent());page.getChildren().add(back);
        Node image=Ui.productImage(product,300,260);VBox imageColumn=new VBox(image);imageColumn.setMinWidth(180);imageColumn.setPrefWidth(300);imageColumn.setMaxWidth(340);
        Label title=Ui.label(Objects.toString(product.getName(),"Без названия"),"page-title");title.setWrapText(true);title.setMaxWidth(Double.MAX_VALUE);
        Label categoryLabel=Ui.label(Objects.toString(product.getCategory(),"Комплектующие"),"eyebrow");Label price=Ui.label(Ui.money(product.getPrice()),"detail-price");Label stock=Ui.label(product.getStock()>0?"В наличии: "+product.getStock():"Нет в наличии","product-stock");
        Label description=new Label(product.getDescription()==null||product.getDescription().isBlank()?"Описание пока не добавлено.":product.getDescription());description.setWrapText(true);description.getStyleClass().add("detail-description");
        Button add=new Button("В корзину",Ui.icon("bag"));add.getStyleClass().add("btn-primary");add.setDisable(product.getStock()<=0);add.setOnAction(e->addProductToCart(product,1));
        VBox info=new VBox(16,categoryLabel,title,Ui.label(Objects.toString(product.getManufacturer(),"Производитель не указан"),"text-muted"),price,stock,add,new Separator(),Ui.label("Описание","section-title"),description);info.setMinWidth(0);HBox.setHgrow(info,Priority.ALWAYS);
        HBox detail=new HBox(30,imageColumn,info);detail.getStyleClass().add("detail-surface");page.getChildren().add(detail);
        Label note=Ui.label("Для проверки совместимости откройте конфигуратор. Отсутствующие характеристики не подтверждают совместимость.","feedback");note.setWrapText(true);page.getChildren().add(note);
        ScrollPane scroll=new ScrollPane(page);scroll.setFitToWidth(true);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);switchView(scroll,"Карточка товара","catalog");
    }
    public List<Product> getCatalogSnapshot(){return List.copyOf(allProducts);}
    public boolean isCatalogLoaded(){return catalogLoaded;}
    public void dispose(){disposed=true;++loadVersion;io.shutdownNow();searchTimer.stop();notificationTimer.stop();clearPrivateViews();cache.values().forEach(view->disposeView(view.controller()));cache.clear();}
}
