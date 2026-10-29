package controllers;

import com.example.authapp.dto.*;
import com.example.authapp.repositories.UserRepository;
import com.example.authapp.services.*;
import com.example.authapp.ui.Ui;
import com.example.authapp.network.SupabaseHttp;
import com.example.authapp.utils.PhoneFormatter;
import config.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.concurrent.*;

/** Account forms and order history inside the persistent application shell. */
public final class CabinetController {
    private static MainController hostMainController;
    @FXML private Label userEmailLabel, profileStatus, ordersStatus;
    @FXML private TextField emailField,nameField,surnameField,phoneField,cityField,addressField;
    @FXML private Button saveButton,changePasswordButton,logoutButton;
    @FXML private VBox mainContentVBox,ordersContainer;
    @FXML private BorderPane rootBorderPane;
    @FXML private ScrollPane scrollPane;
    private boolean disposed,loading;
    private long generation;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"account-profile");t.setDaemon(true);return t;});
    public static void setHostMainController(MainController controller){hostMainController=controller;}
    @FXML private void initialize(){
        profileStatus.managedProperty().bind(profileStatus.visibleProperty());
        saveButton.setOnAction(e->saveProfile());changePasswordButton.setOnAction(e->changePassword());
        logoutButton.setOnAction(e->{if(hostMainController!=null)hostMainController.onLogout();else SessionManager.clearSession();});
        PhoneFormatter.setupPhoneField(phoneField);
        String email=SessionManager.getUserEmail();userEmailLabel.setText(email==null?"Войдите, чтобы увидеть профиль и заказы":email);emailField.setText(Objects.toString(email,""));
        if(email==null){mainContentVBox.setDisable(true);message("Для просмотра профиля нужен вход в аккаунт.","neutral");ordersStatus.setText("История доступна после входа.");return;}
        reload(true);
    }
    private void reload(boolean profile){
        if(loading||disposed||SessionManager.getUserId()==null)return;loading=true;long request=++generation;
        String email=SessionManager.getUserEmail(), userId=SessionManager.getUserId();ordersStatus.setText("Загружаем историю заказов…");
        var lease=SessionManager.requireSession();
        worker.submit(()->{
            UserDTO user=null;List<OrderDTO> orders=List.of();String failure=null;
            try(var scope=SupabaseHttp.forSession(lease)){if(profile)user=UserRepository.getUserProfileByEmail(email);orders=new OrderService(new CartService(),new ProductService()).getUserOrderHistory();}
            catch(Exception ex){failure="Не удалось загрузить данные. Попробуйте обновить позже.";}
            UserDTO loaded=user;List<OrderDTO> history=orders;String error=failure;
            Platform.runLater(()->{if(disposed||request!=generation||!Objects.equals(userId,SessionManager.getUserId()))return;loading=false;
                if(loaded!=null){nameField.setText(safe(loaded.name));surnameField.setText(safe(loaded.surname));phoneField.setText(safe(loaded.phone));cityField.setText(safe(loaded.city));addressField.setText(safe(loaded.address));}
                ordersContainer.getChildren().clear();
                if(error!=null){ordersStatus.setText(error);return;}
                ordersStatus.setText(history==null||history.isEmpty()?"Заказов пока нет. После оформления они появятся здесь.":"Последние заказы и их итоговые суммы.");
                if(history!=null)history.forEach(order->ordersContainer.getChildren().add(orderCard(order)));
            });
        });
    }
    private VBox orderCard(OrderDTO order){
        Label title=new Label("Заказ №"+order.orderId);title.getStyleClass().add("secondary-section-title");
        Label date=new Label(order.orderDate==null?"Дата не указана":order.orderDate);date.getStyleClass().add("secondary-muted");
        String status=switch(safe(order.status).toLowerCase(Locale.ROOT)){case "pending"->"В обработке";case "completed"->"Завершён";case "cancelled"->"Отменён";default->"Статус уточняется";};
        Label badge=new Label(status);badge.getStyleClass().add("badge");Region gap=new Region();HBox.setHgrow(gap,Priority.ALWAYS);HBox head=new HBox(12,title,gap,badge);head.setAlignment(Pos.CENTER_LEFT);
        VBox items=new VBox(9);
        if(order.items!=null)for(OrderItemDTO item:order.items){Label name=new Label(safe(item.productName)+" × "+item.quantity);name.setWrapText(true);name.setMinWidth(0);name.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(name,Priority.ALWAYS);Label amount=new Label(Ui.money(item.subtotal));amount.getStyleClass().add("secondary-amount");HBox row=new HBox(14,name,amount);items.getChildren().add(row);}
        if(items.getChildren().isEmpty()){Label empty=new Label("Состав заказа не загружен.");empty.getStyleClass().add("secondary-muted");items.getChildren().add(empty);}
        HBox total=new HBox(12);Label label=new Label("Итого");Region space=new Region();HBox.setHgrow(space,Priority.ALWAYS);Label value=new Label(Ui.money(order.finalAmount));value.getStyleClass().add("secondary-section-title");total.getChildren().addAll(label,space,value);
        VBox card=new VBox(12,head,date,new Separator(),items,new Separator(),total);card.getStyleClass().addAll("surface","order-card");return card;
    }
    private void saveProfile(){
        if(saveButton.isDisabled()||disposed)return;
        String name=nameField.getText().trim(),surname=surnameField.getText().trim(),phone=phoneField.getText().trim(),city=cityField.getText().trim(),address=addressField.getText().trim(),email=SessionManager.getUserEmail();
        if(name.isEmpty()||surname.isEmpty()){message("Заполните имя и фамилию.","error");return;}
        if(!phone.isBlank()&&!PhoneFormatter.isCompletePhone(phone)){message("Укажите телефон в формате +7XXXXXXXXXX.","error");return;}
        saveButton.setDisable(true);message("Сохраняем профиль…","neutral");
        var lease=SessionManager.requireSession();
        worker.submit(()->{try(var scope=SupabaseHttp.forSession(lease)){UserRepository.updateUserProfile(email,name,surname,phone,city,address);Platform.runLater(()->{if(!disposed&&lease.equals(SessionManager.captureSession())){saveButton.setDisable(false);message("Изменения сохранены.","success");}});}catch(Exception ex){Platform.runLater(()->{if(!disposed){saveButton.setDisable(false);message("Не удалось сохранить профиль. Проверьте соединение и попробуйте снова.","error");}});}});
    }
    private void changePassword(){
        Dialog<Void> dialog=new Dialog<>();dialog.setTitle("Изменить пароль");Ui.theme(dialog);
        PasswordField oldPass=new PasswordField(),newPass=new PasswordField(),confirm=new PasswordField();oldPass.setPromptText("Текущий пароль");newPass.setPromptText("Новый пароль, минимум 6 символов");confirm.setPromptText("Повторите новый пароль");
        Label error=new Label();error.setWrapText(true);error.getStyleClass().addAll("secondary-status","error");error.setVisible(false);error.setManaged(false);
        VBox fields=new VBox(12,new Label("Текущий пароль"),oldPass,new Label("Новый пароль"),newPass,new Label("Подтверждение"),confirm,error);fields.setPrefWidth(380);dialog.getDialogPane().setContent(fields);
        ButtonType submit=new ButtonType("Сохранить",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL,submit);
        Button send=(Button)dialog.getDialogPane().lookupButton(submit);send.getStyleClass().add("btn-primary");
        send.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();String old=oldPass.getText(),next=newPass.getText(),confirmation=confirm.getText();
            if(old.isBlank()||next.length()<6||!next.equals(confirmation)){error.setText("Заполните текущий пароль, укажите новый от 6 символов и повторите его.");error.setVisible(true);error.setManaged(true);return;}
            var lease=SessionManager.requireSession();String id=lease.userId(),email=SessionManager.getUserEmail();send.setDisable(true);
            worker.submit(()->{try(var scope=SupabaseHttp.forSession(lease)){UserRepository.verifyPassword(email,old);UserRepository.updatePassword(id,next);Platform.runLater(()->{if(!disposed&&lease.equals(SessionManager.captureSession())){dialog.close();message("Пароль изменён.","success");}});}catch(Exception ex){Platform.runLater(()->{if(!disposed){send.setDisable(false);error.setText("Не удалось изменить пароль. Проверьте текущий пароль и повторите запрос.");error.setVisible(true);error.setManaged(true);}});}});
        });dialog.show();
    }
    private void message(String text,String tone){profileStatus.setText(text);profileStatus.getStyleClass().setAll("secondary-status",tone);profileStatus.setVisible(true);}
    private static String safe(String text){return Objects.toString(text,"");}
    public void returnFromAdminPanel(){reload(false);}
    public void setActive(boolean active){if(active&&!loading&&ordersContainer.getChildren().isEmpty()&&SessionManager.getUserId()!=null)reload(false);}
    public void dispose(){disposed=true;generation++;worker.shutdownNow();}
}
