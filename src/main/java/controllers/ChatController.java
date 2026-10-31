package controllers;

import com.example.authapp.models.Product;
import com.example.authapp.services.ChatBotService;
import com.example.authapp.services.BuildRecommendationService;
import com.example.authapp.services.BenchmarkPresentation;
import com.example.authapp.ui.Ui;
import com.example.authapp.ui.ChatViewport;
import com.example.authapp.ui.ChatActions;
import javafx.css.PseudoClass;
import javafx.geometry.VPos;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.net.URI;
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
            if (content instanceof ChatBotService.TermContent term) body.getChildren().add(termCard(term));
            else if (content instanceof ChatBotService.ProductsContent products) body.getChildren().add(productsCard(products));
            else if (content instanceof ChatBotService.BuildContent build) {
                if(multipleBuilds)body.getChildren().add(label("Вариант "+(++buildIndex),"chat-card-title"));
                body.getChildren().add(buildCard(build));
            }
            else if (content instanceof ChatBotService.BenchmarksContent comparisons) comparisons.comparisons().forEach(c -> body.getChildren().add(comparisonCard(c,comparisons.modelNames())));
            else if (content instanceof ChatBotService.SpecificationsContent specs) body.getChildren().add(specificationsCard(specs));
        }
        if (!reply.suggestions().isEmpty()) {
            FlowPane suggestions = new ChatActions(8,8);
            reply.suggestions().forEach(s -> suggestions.getChildren().add(chip(s, () -> ask(s)))); body.getChildren().add(suggestions);
        }
        HBox row = new HBox(12,avatar,body); row.setAlignment(Pos.TOP_LEFT); row.getStyleClass().add("chat-bot-row"); addMessage(row);
    }
    private Node termCard(ChatBotService.TermContent content) {
        var term = content.term();
        VBox card = card(); card.getChildren().addAll(label(term.term(), "chat-card-title"), label(term.definition(), "chat-copy"));
        VBox simple = new VBox(5,label("Проще", "chat-caption"),label(term.simpleExplanation(), "chat-copy")); simple.getStyleClass().add("chat-simple"); card.getChildren().add(simple);
        card.getChildren().add(source(term.source(), "Источник: " + term.source().getHost()));
        if (!term.relatedTerms().isEmpty()) {
            FlowPane related = new ChatActions(6,6); for (String value : term.relatedTerms()) related.getChildren().add(chip(value, () -> ask("Что такое " + value + "?"))); card.getChildren().add(related);
        }
        return card;
    }
    private Node productsCard(ChatBotService.ProductsContent content) {
        VBox card = card(); card.getChildren().add(label("Товары в каталоге", "chat-card-title"));
        for (var product : content.items()) {
            VBox details = new VBox(4,label(product.name(), "chat-product-name"),label(product.stock() > 0 ? "В наличии: " + product.stock() : "Нет в наличии", "chat-caption"));
            details.setMinWidth(0); HBox.setHgrow(details,Priority.ALWAYS);
            Button open = button("Открыть", "btn"); open.setOnAction(e -> { if(host!=null)host.openProduct(product.toProduct()); }); open.setDisable(host==null);
            VBox right = new VBox(6,label(Ui.money(product.price()), "chat-row-price"),open); right.setAlignment(Pos.CENTER_RIGHT);
            HBox row = new HBox(12,Ui.productImage(product.toProduct(),52,44),details,right); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("chat-product-row"); card.getChildren().add(row);
        }
        if (content.total() > content.items().size()) card.getChildren().add(label("Показано " + content.items().size() + " из " + content.total() + ". Уточните запрос, чтобы сузить выбор.", "chat-caption"));
        return card;
    }
    private Node buildCard(ChatBotService.BuildContent build) {
        VBox card = card(); card.getStyleClass().add("chat-build-card");
        Label count = label(build.parts().size() + " деталей", "chat-count");
        HBox heading = new HBox(12,label("Вариант системного блока", "chat-card-title"),spring(),count); heading.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(label("ВАРИАНТ СБОРКИ", "chat-eyebrow"),heading);
        VBox parts = new VBox();
        for (int i=0;i<build.parts().size();i++) {
            var product=build.parts().get(i);
            VBox name = new VBox(3,label(product.category(), "chat-caption"),label(product.name(), "chat-product-name")); name.setMinWidth(0); HBox.setHgrow(name,Priority.ALWAYS);
            HBox row = new HBox(12,Ui.icon(categoryIcon(product.category())),name,label(build.ownedProductIds().contains(product.id())?"Уже есть":Ui.money(product.price()), build.ownedProductIds().contains(product.id())?"chat-owned-label":"chat-row-price")); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("chat-build-row");
            if(i>=4) {row.setVisible(false);row.setManaged(false);} parts.getChildren().add(row);
        }
        card.getChildren().add(parts);
        if(build.parts().size()>4){
            Button expand=button("Ещё " +(build.parts().size()-4)+" компонента", "chat-expand"); expand.setMaxWidth(Double.MAX_VALUE);
            expand.setOnAction(e->{boolean show=!parts.getChildren().get(4).isVisible();for(int i=4;i<parts.getChildren().size();i++){parts.getChildren().get(i).setVisible(show);parts.getChildren().get(i).setManaged(show);}expand.setText(show?"Свернуть":"Ещё "+(build.parts().size()-4)+" компонента");});card.getChildren().add(expand);
        }
        HBox total = new HBox(12,label(build.ownedProductIds().isEmpty()?"Стоимость комплектующих":"Стоимость покупки", "chat-caption"),spring(),label(Ui.money(build.total()), "chat-build-total")); total.setAlignment(Pos.CENTER_LEFT); total.getStyleClass().add("chat-build-total-row");
        Button open=button("Открыть в конфигураторе", "btn", "btn-primary"); open.setGraphic(Ui.icon("arrow-right"));open.setContentDisplay(ContentDisplay.RIGHT);open.setMaxWidth(Double.MAX_VALUE);open.setDisable(host==null);
        open.setOnAction(e->{if(host!=null)host.openSuggestedBuild(build.parts().stream().map(ChatBotService.ProductInfo::toProduct).toList(),build.budget(),build.ownedProductIds());});
        Label check=label(build.verified()?"Основные проверки пройдены. Проверьте спецификации перед покупкой.":"Нужна проверка характеристик. Совместимость не подтверждена.",build.verified()?"chat-verified":"chat-unverified");check.setGraphic(Ui.icon(build.verified()?"check":"info"));check.setGraphicTextGap(7);
        card.getChildren().addAll(total,open,check);
        List<String> notes = new ArrayList<>(build.findings()); notes.addAll(build.limitations());
        if(!notes.isEmpty()){
            VBox explanation=new VBox(6);notes.stream().distinct().forEach(note->explanation.getChildren().add(label("• "+note,"chat-caption")));
            card.getChildren().add(details("Почему такой вариант и что проверить",explanation));
        }
        return card;
    }
    private Node comparisonCard(com.example.authapp.services.BenchmarkService.Comparison comparison,Map<String,String> modelNames){
        var left=comparison.left();var right=comparison.right();var context=left.context();VBox card=card();
        card.getStyleClass().add("chat-benchmark-card");
        String leftName=BenchmarkPresentation.modelName(left.modelId(),modelNames),rightName=BenchmarkPresentation.modelName(right.modelId(),modelNames);
        card.getChildren().addAll(label(BenchmarkPresentation.title(context),"chat-card-title"),label(BenchmarkPresentation.workload(context),"chat-caption"));
        GridPane table=new GridPane();table.setMinWidth(0);table.getStyleClass().add("chat-table");table.setHgap(16);table.setVgap(10);ColumnConstraints names=new ColumnConstraints();names.setPercentWidth(65);ColumnConstraints values=new ColumnConstraints();values.setPercentWidth(35);table.getColumnConstraints().addAll(names,values);
        table.add(label("Модель","chat-caption"),0,0);table.add(label("Результат","chat-caption"),1,0);
        table.add(label(leftName,"chat-product-name"),0,1);table.add(label(left.value()+" "+BenchmarkPresentation.unit(context.unit()),"chat-row-price"),1,1);
        table.add(label(rightName,"chat-product-name"),0,2);table.add(label(right.value()+" "+BenchmarkPresentation.unit(context.unit()),"chat-row-price"),1,2);alignCells(table);card.getChildren().add(table);
        card.getChildren().addAll(label(comparison.betterModelId().isEmpty()?"Значения равны.":"Лучший результат в этом тесте: "+BenchmarkPresentation.modelName(comparison.betterModelId(),modelNames),"chat-copy"),
                label(BenchmarkPresentation.difference(comparison),"chat-copy"),
                label("Результат относится только к этому тесту и стенду. Он не определяет производительность в других задачах или скорость всей сборки.","chat-caption"));
        LinkedHashSet<String> dates=new LinkedHashSet<>();dates.add(left.dateDescription());dates.add(right.dateDescription());
        card.getChildren().add(label(String.join(" · ",dates),"chat-caption"));
        VBox explanation=new VBox(7);explanation.setMinWidth(0);
        for(String line:BenchmarkPresentation.conditionLines(context)) {
            Label condition=label(line,"chat-caption");condition.getStyleClass().add("chat-condition");explanation.getChildren().add(condition);
        }
        explanation.getChildren().addAll(label(comparison.explanation(),"chat-caption"),
                source(left.source(),"Источник: "+leftName+" · "+left.dateDescription()),source(right.source(),"Источник: "+rightName+" · "+right.dateDescription()));
        card.getChildren().add(details("Условия и источники",explanation));return card;
    }
    private Node specificationsCard(ChatBotService.SpecificationsContent specs){
        VBox card=card();card.getStyleClass().add("chat-specifications-card");
        card.getChildren().addAll(label("Сравнение характеристик","chat-card-title"),
                label("Показаны проверенные сведения. Неизвестное поле требует уточнения и не означает отсутствие функции.","chat-caption"));
        GridPane table=new GridPane();table.setMinWidth(0);table.getStyleClass().add("chat-table");table.setHgap(12);table.setVgap(12);table.setMaxWidth(Double.MAX_VALUE);
        for(double width:new double[]{28,36,36}){ColumnConstraints column=new ColumnConstraints();column.setPercentWidth(width);table.getColumnConstraints().add(column);}
        table.add(label("Характеристика","chat-caption"),0,0);table.add(label(specs.left().name(),"chat-product-name"),1,0);table.add(label(specs.right().name(),"chat-product-name"),2,0);
        int row=1;for(var fact:specs.rows()) {
            table.add(label(fact.label(),"chat-caption"),0,row);table.add(label(fact.left(),"chat-copy"),1,row);table.add(label(fact.right(),"chat-copy"),2,row);row++;
        }
        alignCells(table);card.getChildren().add(table);
        card.getChildren().add(label("Цена и наличие из каталога","chat-field-label"));
        HBox offers=new HBox(14);offers.getChildren().addAll(catalogOffer(specs.left()),catalogOffer(specs.right()));card.getChildren().add(offers);
        card.getChildren().add(label("Характеристики и цена не дают процента превосходства и не предсказывают FPS.","chat-caption"));
        VBox explanation=new VBox(7);explanation.setMinWidth(0);
        specs.limitations().forEach(note->explanation.getChildren().add(label(note,"chat-caption")));
        specs.sources().forEach(fact->explanation.getChildren().add(source(fact.uri(),fact.title())));
        if(!explanation.getChildren().isEmpty())card.getChildren().add(details("Источники и ограничения",explanation));
        return card;
    }
    private VBox catalogOffer(ChatBotService.ProductInfo product){
        VBox offer=new VBox(5,label(product.name(),"chat-product-name"),label(Ui.money(product.price()),"chat-row-price"),
                label(product.stock()>0?"В наличии: "+product.stock():"Нет в наличии","chat-caption"));
        offer.setMinWidth(0);offer.setPrefWidth(0);HBox.setHgrow(offer,Priority.ALWAYS);return offer;
    }
    private TitledPane details(String title,VBox content){
        TitledPane details=new TitledPane(title,content);details.setExpanded(false);details.setAnimated(false);details.setMinWidth(0);details.setMaxWidth(Double.MAX_VALUE);details.getStyleClass().add("chat-explanation");details.expandedProperty().addListener((o,a,b)->viewport.reflow(details));return details;
    }
    private Hyperlink source(URI uri,String title){Hyperlink link=new Hyperlink(title);link.getStyleClass().add("chat-source");link.setWrapText(true);link.setMaxWidth(Double.MAX_VALUE);link.setOnAction(e->{try{Ui.openExternal(uri.toString());}catch(Exception ex){status.setText("Не удалось открыть источник.");}});return link;}
    private void addMessage(Node message){viewport.addMessage(message);}
    @FXML private void scrollToBottom(){viewport.scrollToBottom();}
    private static void alignCells(GridPane table) {
        table.getColumnConstraints().forEach(column->column.setMinWidth(0));
        table.getChildren().forEach(cell->{GridPane.setValignment(cell,VPos.TOP);GridPane.setFillWidth(cell,true);});
    }
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
    private static String categoryIcon(String category){String c=Objects.toString(category,"").toLowerCase(Locale.ROOT);if(c.contains("процессор"))return "cpu";if(c.contains("видеокарт"))return "gpu";if(c.contains("материн"))return "board";if(c.contains("памят"))return "memory";if(c.contains("питан"))return "power";if(c.contains("корпус"))return "case";if(c.contains("охлажд"))return "cooler";return "drive";}
    private static VBox card(){VBox card=new VBox(12);card.getStyleClass().add("chat-card");return card;}
    private static Label label(String text,String style){Label label=new Label(text);label.setWrapText(true);label.setMinWidth(0);label.setMaxWidth(Double.MAX_VALUE);label.getStyleClass().add(style);return label;}
    private static Button button(String text,String...styles){Button button=new Button(text);button.getStyleClass().addAll(styles);return button;}
    private static Button chip(String text,Runnable action){Button button=button(text,"chat-chip");button.setMinWidth(0);button.setWrapText(true);button.setOnAction(e->action.run());return button;}
    private static Region spring(){Region region=new Region();HBox.setHgrow(region,Priority.ALWAYS);return region;}
    private static List<Product> snapshot(Collection<Product> products){return products.stream().map(ChatBotService.ProductInfo::from).map(ChatBotService.ProductInfo::toProduct).toList();}
}
