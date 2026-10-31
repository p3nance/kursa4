package controllers;

import com.example.authapp.models.Product;
import com.example.authapp.services.ChatBotService;
import com.example.authapp.services.BuildRecommendationService;
import com.example.authapp.ui.ChatCardView;
import com.example.authapp.ui.Ui;
import com.example.authapp.ui.ChatViewport;
import com.example.authapp.ui.ChatActions;
import javafx.css.PseudoClass;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.util.*;
import java.util.concurrent.*;

/** Persistent local conversation; the shell owns this controller's lifecycle. */
public final class ChatController {
    @FXML private BorderPane chatRoot;
    @FXML private VBox messages;
    @FXML private ScrollPane conversation;
    @FXML private TextArea input;
    @FXML private Label status, budgetValue, taskValue, catalogValue, constraintsValue, ownedValue;
    @FXML private Button sendButton, cancelButton, retryButton, newMessagesButton;
    @FXML private VBox contextPane;
    @FXML private HBox contentRow;
    private MainController host;
    private ChatBotService bot;
    private List<Product> catalog = List.of();
    private ChatBotService.RequestSummary summary = new ChatBotService.RequestSummary(null, null);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "local-chat"); thread.setDaemon(true); return thread;
    });
    private Future<?> pending;
    private long generation;
    private String lastRequest;
    private boolean disposed;
    private ChatViewport viewport;
    private ChatCardView cards;

    @FXML private void initialize() {
        sendButton.setGraphic(Ui.icon("send"));
        cancelButton.setGraphic(Ui.icon("close"));
        retryButton.setGraphic(Ui.icon("refresh"));
        input.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                event.consume();
                if (event.isShiftDown()) input.replaceSelection("\n"); else send();
            }
        });
        input.textProperty().addListener((obs, old, text) -> {
            if (text.length() > 2000) input.setText(text.substring(0, 2000));
        });
        viewport=new ChatViewport(chatRoot,conversation,messages,this::showNewMessages);
        cards = new ChatCardView(new ChatCardView.Actions() {
            @Override public void ask(String text) { ChatController.this.ask(text); }
            @Override public boolean canNavigate() { return host != null; }
            @Override public void openProduct(Product product) { if (host != null) host.openProduct(product); }
            @Override public void openBuild(ChatBotService.BuildContent build) {
                if (host != null) host.openSuggestedBuild(build.parts().stream().map(ChatBotService.ProductInfo::toProduct).toList(), build.budget(), build.ownedProductIds());
            }
            @Override public void reflow(Node anchor) { viewport.reflow(anchor); }
            @Override public void sourceUnavailable() { status.setText("Не удалось открыть источник."); }
        });
        chatRoot.widthProperty().addListener((obs,old,value)->updateLayout());
        chatRoot.heightProperty().addListener((obs,old,value)->updateLayout());
        updateLayout();
        welcome(); setBusy(false); updateSummary(summary);
    }

    public void configure(MainController host, List<Product> catalog) {
        this.host = host;
        if (bot == null) {
            this.catalog = snapshot(catalog); bot = new ChatBotService(this.catalog);
            catalogValue.setText(catalog.isEmpty() ? "Каталог пока пуст" : "Товаров в каталоге: " + catalog.size());
        } else updateCatalog(catalog);
    }

    /** Refreshes data on the same worker, preserving the budget, intent and existing cards. */
    public void updateCatalog(List<Product> products) {
        catalog = snapshot(products);
        if (disposed) return;
        if (bot == null) { bot = new ChatBotService(catalog); return; }
        ChatBotService current = bot;
        List<Product> latest = catalog;
        worker.submit(() -> {
            current.updateCatalog(latest);
            Platform.runLater(() -> {
                if (!disposed && current == bot) {catalogValue.setText(latest.isEmpty() ? "Каталог пока пуст" : "Товаров в каталоге: " + latest.size());updateSummary(current.summary());}
            });
        });
    }

    public void setActive(boolean active) { if(viewport!=null)viewport.setActive(active); }
    private void updateLayout() {
        boolean compact=chatRoot.getWidth()<1000 || chatRoot.getHeight()<720;
        chatRoot.pseudoClassStateChanged(PseudoClass.getPseudoClass("compact"),compact);
        boolean side=chatRoot.getWidth()>=1080 && chatRoot.getHeight()>=680;
        contextPane.setVisible(side);contextPane.setManaged(side);
    }

    @FXML private void send() {
        if (disposed || bot == null || pending != null && !pending.isDone()) return;
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        input.clear(); submit(text, true);
    }

    private void ask(String text) { input.setText(text); send(); }

    private void submit(String text, boolean showUser) {
        if (disposed || bot == null || pending != null && !pending.isDone()) return;
        if (text.length() > 2000) { status.setText("Сократите сообщение до 2000 символов."); return; }
        if (messages.getChildren().size() == 1 && "welcome".equals(messages.getChildren().getFirst().getId())) messages.getChildren().clear();
        if (showUser) addUser(text);
        lastRequest = text;
        scrollToBottom();
        long request = ++generation;
        ChatBotService current = bot;
        setBusy(true); setRetry(false);
        pending = worker.submit(() -> {
            try {
                ChatBotService draft = current.fork();
                ChatBotService.Reply reply = draft.reply(text);
                Platform.runLater(() -> {
                    if (disposed || generation != request || bot != current) return;
                    current.commitConversation(draft);
                    addReply(reply); updateSummary(reply.request()); setBusy(false);
                });
            } catch (CancellationException ignored) {
                // cancel() owns the visible state and invalidates this request.
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (disposed || generation != request) return;
                    setBusy(false); status.setText("Не удалось обработать запрос. Можно повторить."); setRetry(true);
                });
            }
        });
    }

    @FXML private void retry() { if (lastRequest != null) submit(lastRequest, false); }
    @FXML private void cancel() {
        generation++;
        if (pending != null) pending.cancel(true);
        setBusy(false); status.setText("Запрос отменён."); setRetry(lastRequest != null);
    }
    @FXML private void clear() {
        cancel(); bot = new ChatBotService(catalog); summary = bot.summary();
        messages.getChildren().clear(); welcome(); updateSummary(summary); lastRequest = null;
        setRetry(false); status.setText("Помощник готов"); scrollToBottom();
    }
    @FXML private void changeRequest() {
        if (disposed || pending != null && !pending.isDone()) return;
        com.example.authapp.ui.BuildRequestDialog.show(
                chatRoot.getScene() == null ? null : chatRoot.getScene().getWindow(), summary)
                .ifPresent(this::ask);
    }

    public void dispose() { disposed = true; if(viewport!=null)viewport.dispose(); generation++; if (pending != null) pending.cancel(true); worker.shutdownNow(); }

    private void welcome() {
        VBox card = new VBox(14); card.setId("welcome"); card.getStyleClass().add("chat-welcome");
        StackPane mark = new StackPane(Ui.icon("chat")); mark.getStyleClass().add("chat-welcome-mark"); mark.setMaxSize(48,48); mark.setMinSize(48,48);
        Label title = label("С чего начнём?", "chat-welcome-title");
        Label description = label("Объясню характеристики, найду товары в каталоге и помогу подобрать системный блок под ваши задачи.", "chat-copy"); description.setMaxWidth(480);
        FlowPane examples = new ChatActions(8,8); examples.setAlignment(Pos.CENTER);
        examples.getChildren().addAll(chip("Подобрать сборку", () -> ask("Собери ПК")), chip("Объяснить термин", () -> ask("Что такое сокет?")), chip("Сравнить модели", () -> ask("Как сравнить комплектующие по бенчмаркам?")));
        card.getChildren().addAll(mark,title,description,examples);
        messages.getChildren().add(card);
    }
    private void addUser(String text) {
        Label bubble = label(text, "chat-user-bubble"); bubble.setMaxWidth(580);
        HBox row = new HBox(bubble); row.setAlignment(Pos.TOP_RIGHT); row.getStyleClass().add("chat-user-row");
        addMessage(row);
    }
    private void addReply(ChatBotService.Reply reply) {
        StackPane avatar = new StackPane(Ui.icon("chat")); avatar.getStyleClass().add("chat-bot-avatar"); avatar.setMinSize(30,30); avatar.setMaxSize(30,30);
        VBox body = new VBox(12); body.setMinWidth(0); body.setPrefWidth(0); HBox.setHgrow(body, Priority.ALWAYS);
        body.getChildren().add(label("Помощник TechStore", "chat-author"));
        body.getChildren().add(label(reply.lead(), "chat-copy"));
        int buildIndex=0;
        boolean multipleBuilds=reply.content().stream().filter(ChatBotService.BuildContent.class::isInstance).count()>1;
        for (ChatBotService.Content content : reply.content()) {
            if (multipleBuilds && content instanceof ChatBotService.BuildContent)
                body.getChildren().add(label("Вариант " + (++buildIndex), "chat-card-title"));
            body.getChildren().addAll(cards.render(content));
        }
        if (!reply.suggestions().isEmpty()) {
            FlowPane suggestions = new ChatActions(8,8);
            reply.suggestions().forEach(s -> suggestions.getChildren().add(chip(s, () -> ask(s)))); body.getChildren().add(suggestions);
        }
        HBox row = new HBox(12,avatar,body); row.setAlignment(Pos.TOP_LEFT); row.getStyleClass().add("chat-bot-row"); addMessage(row);
    }
    private void addMessage(Node message){viewport.addMessage(message);}
    @FXML private void scrollToBottom(){viewport.scrollToBottom();}
    private void showNewMessages(boolean show){newMessagesButton.setVisible(show);newMessagesButton.setManaged(show);}
    private void setBusy(boolean busy){sendButton.setDisable(busy);input.setDisable(busy);cancelButton.setVisible(busy);cancelButton.setManaged(busy);status.setText(busy?"Подбираю ответ…":"Помощник готов");}
    private void setRetry(boolean show){retryButton.setVisible(show);retryButton.setManaged(show);}
    private void updateSummary(ChatBotService.RequestSummary request){if(request==null)return;summary=request;budgetValue.setText(request.budget()==null?"Не указан":Ui.money(request.budget()));taskValue.setText(usageLabel(request.usage()));
        var details=new ArrayList<String>();
        if(request.minRamGb()!=null)details.add("ОЗУ: не меньше "+request.minRamGb()+" ГБ");
        if(request.minStorageGb()!=null)details.add("SSD: не меньше "+request.minStorageGb()+" ГБ");
        if(request.requireVerified())details.add("Только проверенная совместимость");
        constraintsValue.setText(String.join("\n",details));constraintsValue.setVisible(!details.isEmpty());constraintsValue.setManaged(!details.isEmpty());
        var owned=request.ownedParts();
        String names=owned.stream().limit(2).map(ChatBotService.ProductInfo::name).collect(java.util.stream.Collectors.joining("\n"));
        ownedValue.setText(owned.isEmpty()?"":"Уже есть ("+owned.size()+"):\n"+names+(owned.size()>2?"\nИ ещё "+(owned.size()-2)+"":"")+"\nНе входят в стоимость покупки.");
        ownedValue.setVisible(!owned.isEmpty());ownedValue.setManaged(!owned.isEmpty());
        ownedValue.setTooltip(owned.isEmpty()?null:new Tooltip(owned.stream().map(ChatBotService.ProductInfo::name).collect(java.util.stream.Collectors.joining("\n"))));
    }
    private static String usageLabel(BuildRecommendationService.Usage usage){if(usage==null)return "Не выбраны";return switch(usage){case GAMING->"Игры";case OFFICE->"Учёба и офис";case WORKSTATION->"Монтаж и рабочие задачи";};}
    private static Label label(String text,String style){Label label=new Label(text);label.setWrapText(true);label.setMinWidth(0);label.setMaxWidth(Double.MAX_VALUE);label.getStyleClass().add(style);return label;}
    private static Button button(String text,String...styles){Button button=new Button(text);button.getStyleClass().addAll(styles);return button;}
    private static Button chip(String text,Runnable action){Button button=button(text,"chat-chip");button.setMinWidth(0);button.setWrapText(true);button.setOnAction(e->action.run());return button;}
    private static List<Product> snapshot(Collection<Product> products){return products.stream().map(ChatBotService.ProductInfo::from).map(ChatBotService.ProductInfo::toProduct).toList();}
}
