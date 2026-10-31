package com.example.authapp.ui;

import com.example.authapp.models.Product;
import com.example.authapp.services.BenchmarkPresentation;
import com.example.authapp.services.ChatBotService;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.net.URI;
import java.util.*;

/** Renders typed bot content; the controller owns navigation and conversation state. */
public final class ChatCardView {
    public interface Actions {
        void ask(String text);
        boolean canNavigate();
        void openProduct(Product product);
        void openBuild(ChatBotService.BuildContent build);
        void reflow(Node anchor);
        void sourceUnavailable();
    }

    private final Actions actions;

    public ChatCardView(Actions actions) {
        this.actions = Objects.requireNonNull(actions);
    }

    public List<Node> render(ChatBotService.Content content) {
        return switch (content) {
            case ChatBotService.TermContent term -> List.of(termCard(term));
            case ChatBotService.ProductsContent products -> List.of(productsCard(products));
            case ChatBotService.BuildContent build -> List.of(buildCard(build));
            case ChatBotService.BenchmarksContent benchmarks -> benchmarks.comparisons().stream()
                    .map(c -> comparisonCard(c, benchmarks.modelNames())).toList();
            case ChatBotService.SpecificationsContent specs -> List.of(specificationsCard(specs));
        };
    }

    private Node termCard(ChatBotService.TermContent content) {
        var term = content.term();
        VBox card = card(); card.getChildren().addAll(label(term.term(), "chat-card-title"), label(term.definition(), "chat-copy"));
        VBox simple = new VBox(5,label("Проще", "chat-caption"),label(term.simpleExplanation(), "chat-copy")); simple.getStyleClass().add("chat-simple"); card.getChildren().add(simple);
        card.getChildren().add(source(term.source(), "Источник: " + term.source().getHost()));
        if (!term.relatedTerms().isEmpty()) {
            FlowPane related = new ChatActions(6,6); for (String value : term.relatedTerms()) related.getChildren().add(chip(value, () -> actions.ask("Что такое " + value + "?"))); card.getChildren().add(related);
        }
        return card;
    }
    private Node productsCard(ChatBotService.ProductsContent content) {
        VBox card = card(); card.getChildren().add(label("Товары в каталоге", "chat-card-title"));
        for (var product : content.items()) {
            VBox details = new VBox(4,label(product.name(), "chat-product-name"),label(product.stock() > 0 ? "В наличии: " + product.stock() : "Нет в наличии", "chat-caption"));
            details.setMinWidth(0); HBox.setHgrow(details,Priority.ALWAYS);
            Button open = button("Открыть", "btn"); open.setOnAction(e -> actions.openProduct(product.toProduct())); open.setDisable(!actions.canNavigate());
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
        Button open=button("Открыть в конфигураторе", "btn", "btn-primary"); open.setGraphic(Ui.icon("arrow-right"));open.setContentDisplay(ContentDisplay.RIGHT);open.setMaxWidth(Double.MAX_VALUE);open.setDisable(!actions.canNavigate());
        open.setOnAction(e->actions.openBuild(build));
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
        String scope = BenchmarkPresentation.scopeNote(context);
        if (!scope.isBlank()) {
            Label note = label(scope, "chat-copy");
            note.getStyleClass().add("chat-benchmark-scope");
            card.getChildren().add(note);
        }
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
        TitledPane details=new TitledPane(title,content);details.setExpanded(false);details.setAnimated(false);details.setMinWidth(0);details.setMaxWidth(Double.MAX_VALUE);details.getStyleClass().add("chat-explanation");details.expandedProperty().addListener((o,a,b)->actions.reflow(details));return details;
    }
    private Hyperlink source(URI uri,String title){Hyperlink link=new Hyperlink(title);link.getStyleClass().add("chat-source");link.setWrapText(true);link.setMaxWidth(Double.MAX_VALUE);link.setOnAction(e->{try{Ui.openExternal(uri.toString());}catch(Exception ex){actions.sourceUnavailable();}});return link;}
    private static void alignCells(GridPane table) {
        table.getColumnConstraints().forEach(column->column.setMinWidth(0));
        table.getChildren().forEach(cell->{GridPane.setValignment(cell,VPos.TOP);GridPane.setFillWidth(cell,true);});
    }
    private static String categoryIcon(String category){String c=Objects.toString(category,"").toLowerCase(Locale.ROOT);if(c.contains("процессор"))return "cpu";if(c.contains("видеокарт"))return "gpu";if(c.contains("материн"))return "board";if(c.contains("памят"))return "memory";if(c.contains("питан"))return "power";if(c.contains("корпус"))return "case";if(c.contains("охлажд"))return "cooler";return "drive";}
    private static VBox card(){VBox card=new VBox(12);card.getStyleClass().add("chat-card");return card;}
    private static Label label(String text,String style){Label label=new Label(text);label.setWrapText(true);label.setMinWidth(0);label.setMaxWidth(Double.MAX_VALUE);label.getStyleClass().add(style);return label;}
    private static Button button(String text,String...styles){Button button=new Button(text);button.getStyleClass().addAll(styles);return button;}
    private static Button chip(String text,Runnable action){Button button=button(text,"chat-chip");button.setMinWidth(0);button.setWrapText(true);button.setOnAction(e->action.run());return button;}
    private static Region spring(){Region region=new Region();HBox.setHgrow(region,Priority.ALWAYS);return region;}
}
