package controllers;

import com.example.authapp.repositories.UserRepository;
import com.example.authapp.ui.Ui;
import config.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.concurrent.*;

/** A local form; the Auth service remains the authority for account state. */
public final class AuthController {
    private static MainController mainController;
    @FXML private Button authSubmitBtn, authSwitchBtn, authBackBtn;
    @FXML private TextField authEmail;
    @FXML private PasswordField authPassword, authConfirmPassword;
    @FXML private Label authSubTitle, authErrorLabel, authTitle, confirmLabel;
    private boolean registerMode, disposed;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r,"account-auth"); t.setDaemon(true); return t; });
    public static void setMainController(MainController controller) { mainController=controller; }
    @FXML private void initialize() {
        authConfirmPassword.managedProperty().bind(authConfirmPassword.visibleProperty());
        confirmLabel.managedProperty().bind(confirmLabel.visibleProperty());
        authErrorLabel.managedProperty().bind(authErrorLabel.visibleProperty());
        authSubmitBtn.setOnAction(e -> submit());
        authPassword.setOnAction(e -> submit()); authConfirmPassword.setOnAction(e -> submit());
        authSwitchBtn.setOnAction(e -> { registerMode=!registerMode; updateMode(); });
        authBackBtn.setOnAction(e -> { if(mainController!=null)mainController.showMainContent(); });
        updateMode();
    }
    private void updateMode() {
        authTitle.setText(registerMode ? "Создать аккаунт" : "Вход в TechStore");
        authSubTitle.setText(registerMode ? "Сохраняйте профиль и следите за заказами в одном месте." : "Войдите, чтобы управлять корзиной и заказами.");
        authSubmitBtn.setText(registerMode ? "Создать аккаунт" : "Войти");
        authSwitchBtn.setText(registerMode ? "Уже есть аккаунт? Войти" : "Нет аккаунта? Зарегистрироваться");
        authConfirmPassword.setVisible(registerMode);confirmLabel.setVisible(registerMode);
        authErrorLabel.setVisible(false);authPassword.clear();authConfirmPassword.clear();
    }
    private void submit() {
        if(authSubmitBtn.isDisabled()||disposed)return;
        String email=authEmail.getText().trim(),password=authPassword.getText(); boolean registering=registerMode;
        if(email.isBlank()||password.isBlank()){message("Заполните email и пароль.","error");return;}
        if(registering && (!password.equals(authConfirmPassword.getText())||password.length()<6)){
            message(password.length()<6 ? "Пароль должен содержать не менее 6 символов." : "Пароли не совпадают.","error");return;
        }
        busy(true);message(registering ? "Создаём аккаунт…" : "Входим в аккаунт…","neutral");
        worker.submit(() -> {
            try {
                boolean success=registering ? SessionManager.register(email,password) : SessionManager.login(email,password);
                boolean confirmation=success && SessionManager.isRegistrationAwaitingConfirmation();
                var completedSession=SessionManager.captureSession();
                if(success && !confirmation){
                    try(var scope=com.example.authapp.network.SupabaseHttp.forSession(completedSession)){
                        if(!java.util.Objects.equals(email,SessionManager.getUserEmail()))throw new IllegalStateException("Сессия изменилась");
                        if(registering)UserRepository.createUserProfile(completedSession.userId(),email,"","");
                    }
                }
                Platform.runLater(() -> {
                    if(disposed)return;busy(false);
                    if(!completedSession.equals(SessionManager.captureSession())){message("Сессия изменилась. Повторите вход.","neutral");return;}
                    if(!success){message(registering ? "Не удалось создать аккаунт. Проверьте данные и попробуйте снова." : "Не удалось войти. Проверьте данные и доступ к аккаунту.","error");return;}
                    authPassword.clear();authConfirmPassword.clear();
                    if(confirmation){message("Проверьте почту и подтвердите адрес, затем войдите в аккаунт.","success");return;}
                    message(registering ? "Аккаунт создан." : "Вы вошли в аккаунт.","success");
                    if(mainController!=null){mainController.refreshSessionUi();mainController.showMainContent();}
                });
            } catch(Exception exception){Platform.runLater(() -> {if(!disposed){busy(false);message("Не удалось завершить запрос. Проверьте соединение и попробуйте снова.","error");}});}
        });
    }
    private void busy(boolean value){authSubmitBtn.setDisable(value);authSwitchBtn.setDisable(value);authEmail.setDisable(value);authPassword.setDisable(value);authConfirmPassword.setDisable(value);authSubmitBtn.setText(value ? "Подождите…" : registerMode ? "Создать аккаунт" : "Войти");}
    private void message(String text,String tone){authErrorLabel.setText(text);authErrorLabel.getStyleClass().setAll("secondary-status",tone);authErrorLabel.setVisible(true);}
    public void setActive(boolean active){if(active)authEmail.requestFocus();}
    public void dispose(){disposed=true;worker.shutdownNow();}
}
