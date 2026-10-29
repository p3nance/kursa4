package controllers;

import com.example.authapp.models.*;
import com.example.authapp.services.*;
import com.example.authapp.ui.Ui;
import com.example.authapp.network.SupabaseHttp;
import config.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.concurrent.*;

/** Cart presentation. All repository calls run outside the JavaFX thread. */
public final class CartController {
    @FXML private VBox cartItemsContainer,cartSummary;
    @FXML private GridPane cartGrid;
    @FXML private TextField promoCodeField;
    @FXML private Button applyPromoButton,continueShopping,checkoutButton;
    @FXML private Label discountLabel,totalLabel,cartStatus,cartCountLabel;
    private MainController mainController;
    private final CartService cartService=new CartService();
    private final PromoCodeService promoCodeService=new PromoCodeService();
    private final OrderService orderService=new OrderService(cartService,new ProductService());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cart-requests");t.setDaemon(true);return t;});
    private boolean busy,disposed;
    private double appliedDiscount;
    private String appliedPromoCode;
    @FXML private void initialize(){
        cartGrid.setMinWidth(0);cartItemsContainer.setMinWidth(0);cartSummary.setMinWidth(0);promoCodeField.setMinWidth(0);
        cartGrid.widthProperty().addListener((o,a,b)->resizeCart(b.doubleValue()));
        resizeCart(cartGrid.getWidth());
        cartStatus.managedProperty().bind(cartStatus.visibleProperty());
        discountLabel.managedProperty().bind(discountLabel.visibleProperty());
        applyPromoButton.setOnAction(e->applyPromo());promoCodeField.setOnAction(e->applyPromo());
        continueShopping.setOnAction(e->{if(mainController!=null)mainController.showMainContent();});
        checkoutButton.setOnAction(e->checkout());loadCartItems();refresh();
    }
    private void resizeCart(double width){
        boolean wide=width>=880;
        cartGrid.getColumnConstraints().clear();
        ColumnConstraints first=new ColumnConstraints();first.setHgrow(Priority.ALWAYS);first.setPercentWidth(wide?64:100);cartGrid.getColumnConstraints().add(first);
        if(wide){ColumnConstraints second=new ColumnConstraints();second.setPercentWidth(36);cartGrid.getColumnConstraints().add(second);}
        GridPane.setColumnIndex(cartSummary,wide?1:0);GridPane.setRowIndex(cartSummary,wide?0:1);
        cartSummary.setMaxWidth(wide?Double.MAX_VALUE:480);
    }
    public void setMainController(MainController controller){mainController=controller;}
    public void refresh(){
        if(busy||disposed)return;
        if(SessionManager.getUserId()==null){loadCartItems();message("Для работы с корзиной войдите в аккаунт.","neutral");return;}
        setBusy(true);message("Обновляем корзину…","neutral");String user=SessionManager.getUserId();
        submitForSession(()->{try{cartService.loadUserCart();Platform.runLater(()->{if(disposed||!Objects.equals(user,SessionManager.getUserId()))return;setBusy(false);cartStatus.setVisible(false);loadCartItems();});}catch(Exception ex){Platform.runLater(()->{if(!disposed){setBusy(false);loadCartItems();message("Не удалось обновить корзину. Проверьте соединение и откройте раздел снова.","error");}});}});
    }
    public void loadCartItems(){
        cartItemsContainer.getChildren().clear();
        List<Cart.CartItem> items=List.copyOf(Cart.getInstance().getItems());
        if(items.isEmpty()){
            VBox empty=new VBox(12);empty.setAlignment(Pos.CENTER);empty.getStyleClass().addAll("surface","cart-empty");
            Label heading=new Label("Корзина пока пуста");heading.getStyleClass().add("secondary-section-title");Label detail=new Label("Добавьте товары из каталога или выберите сборку с помощником.");detail.setWrapText(true);detail.getStyleClass().add("secondary-muted");
            Button catalog=new Button("Открыть каталог");catalog.getStyleClass().addAll("btn","btn-soft");catalog.setOnAction(e->{if(mainController!=null)mainController.showMainContent();});empty.getChildren().addAll(Ui.icon("bag"),heading,detail,catalog);cartItemsContainer.getChildren().add(empty);
        }else for(Cart.CartItem item:items)cartItemsContainer.getChildren().add(itemCard(item));
        updateTotal();
    }
    private HBox itemCard(Cart.CartItem item){
        HBox row=new HBox(14);row.setAlignment(Pos.CENTER_LEFT);row.getStyleClass().addAll("surface","cart-item");
        Label name=new Label(item.getProduct().getName());name.setWrapText(true);name.setMinWidth(0);name.getStyleClass().add("cart-item-name");
        Label price=new Label(Ui.money(item.getProduct().getPrice())+" за шт.");price.getStyleClass().add("secondary-muted");VBox copy=new VBox(7,name,price);copy.setMinWidth(0);HBox.setHgrow(copy,Priority.ALWAYS);
        Spinner<Integer> quantity=new Spinner<>(1,Math.max(99,item.getQuantity()),item.getQuantity());quantity.setEditable(false);quantity.setPrefWidth(78);quantity.setMaxWidth(78);quantity.setAccessibleText("Количество товара «"+item.getProduct().getName()+"»");
        quantity.valueProperty().addListener((o,old,next)->{if(busy||Objects.equals(old,next))return;setBusy(true);submitForSession(()->{try{cartService.updateCartItemQuantity(item.getProduct(),next);Platform.runLater(()->{if(!disposed){setBusy(false);loadCartItems();}});}catch(Exception ex){Platform.runLater(()->{if(!disposed){setBusy(false);loadCartItems();message("Не удалось изменить количество. Обновите корзину и попробуйте снова.","error");}});}});});
        Button remove=new Button();remove.setGraphic(Ui.icon("trash"));remove.setAccessibleText("Удалить «"+item.getProduct().getName()+"»");remove.getStyleClass().addAll("btn","cart-remove");remove.setOnAction(e->{setBusy(true);submitForSession(()->{try{cartService.removeFromCart(item.getProduct());Platform.runLater(()->{if(!disposed){setBusy(false);loadCartItems();}});}catch(Exception ex){Platform.runLater(()->{if(!disposed){setBusy(false);message("Не удалось удалить товар. Попробуйте снова.","error");}});}});});
        row.getChildren().addAll(Ui.productImage(item.getProduct(),70,60),copy,quantity,remove);return row;
    }
    private void applyPromo(){
        if(busy||disposed)return;String code=promoCodeField.getText().trim();if(code.isEmpty()){message("Введите промокод.","error");return;}
        setBusy(true);submitForSession(()->{try{PromoCode promo=promoCodeService.validateAndGetPromoCode(code);Platform.runLater(()->{if(disposed)return;cartService.setAppliedPromoCode(promo);appliedPromoCode=promo.getCode();setBusy(false);promoCodeField.setDisable(true);applyPromoButton.setDisable(true);updateTotal();message("Промокод применён. Итоговая скидка проверяется при оформлении.","success");});}catch(Exception ex){Platform.runLater(()->{if(!disposed){setBusy(false);message("Промокод недоступен или не удалось проверить его. Проверьте код и попробуйте снова.","error");}});}});
    }
    private void updateTotal(){
        double total=Cart.getInstance().getTotal();PromoCode promo=cartService.getAppliedPromoCode();appliedDiscount=promo==null?0:promoCodeService.calculateDiscount(total,promo);
        totalLabel.setText(Ui.money(Math.max(0,total-appliedDiscount)));discountLabel.setText("Скидка: −"+Ui.money(appliedDiscount));discountLabel.setVisible(appliedDiscount>0);
        int quantity=Cart.getInstance().getTotalQuantity();cartCountLabel.setText(quantity==0?"Выбранные товары появятся здесь":quantity+" шт. в корзине");checkoutButton.setDisable(busy||quantity==0||SessionManager.getUserId()==null);
    }
    private void checkout(){
        if(busy||disposed||Cart.getInstance().getItems().isEmpty())return;
        Alert confirm=new Alert(Alert.AlertType.CONFIRMATION);Ui.theme(confirm);confirm.setTitle("Оформить заказ");confirm.setHeaderText("Проверить и оформить заказ?");confirm.setContentText("Предварительная сумма: "+totalLabel.getText()+".\nЦены, наличие и скидка будут проверены сервером.");
        if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        setBusy(true);message("Проверяем и оформляем заказ…","neutral");
        submitForSession(()->{try{int id=orderService.createOrderFromCart(appliedPromoCode);Platform.runLater(()->{if(disposed)return;appliedPromoCode=null;appliedDiscount=0;promoCodeField.clear();promoCodeField.setDisable(false);applyPromoButton.setDisable(false);setBusy(false);loadCartItems();message("Заказ №"+id+" создан. Итоговые суммы доступны в истории заказов.","success");});}catch(Exception ex){Platform.runLater(()->{if(!disposed){setBusy(false);message("Не удалось подтвердить оформление. "+Objects.toString(ex.getMessage(),"Повторите позже.")+" При сетевом сбое сначала проверьте историю заказов.","error");}});}});
    }
    private void setBusy(boolean value){busy=value;cartItemsContainer.setDisable(value);promoCodeField.setDisable(value||appliedPromoCode!=null);applyPromoButton.setDisable(value||appliedPromoCode!=null);updateTotal();}
    private void message(String text,String tone){cartStatus.setText(text);cartStatus.getStyleClass().setAll("secondary-status",tone);cartStatus.setVisible(true);}
    public void setActive(boolean active){if(active)refresh();}
    private void submitForSession(Runnable task){
        var lease=SessionManager.captureSession();
        worker.submit(()->{
            try(var scope=SupabaseHttp.forSession(lease)){task.run();}
            catch(RuntimeException ex){Platform.runLater(()->{if(!disposed){setBusy(false);message("Аккаунт изменился. Откройте корзину текущего аккаунта снова.","error");}});}
        });
    }
    public void dispose(){disposed=true;worker.shutdownNow();}
}
