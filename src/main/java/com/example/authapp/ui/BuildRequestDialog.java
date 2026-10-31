package com.example.authapp.ui;

import com.example.authapp.network.Money;
import com.example.authapp.services.BuildRecommendationService;
import com.example.authapp.services.ChatBotService;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.math.BigDecimal;
import java.util.*;

/** Edits a build request without changing conversation state until the user applies it. */
public final class BuildRequestDialog {
    private BuildRequestDialog() {}

    public static Optional<String> show(Window owner, ChatBotService.RequestSummary summary) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Ваш запрос"); dialog.setHeaderText("Бюджет, задачи и память");
        if (owner != null) dialog.initOwner(owner);
        dialog.getDialogPane().getStylesheets().add(BuildRequestDialog.class.getResource("/views/styles/theme.css").toExternalForm());
        dialog.getDialogPane().getStylesheets().add(BuildRequestDialog.class.getResource("/views/styles/chat.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("chat-request-dialog");
        TextField budget = new TextField(summary.budget() == null ? "" : summary.budget().toPlainString());
        budget.setPromptText("Например, 100000"); budget.getStyleClass().add("field");
        ComboBox<String> usage = new ComboBox<>(); usage.getItems().addAll("Игры", "Учёба и офис", "Монтаж и рабочие задачи");
        usage.setValue(usageLabel(summary.usage())); if (summary.usage() == null) usage.getSelectionModel().selectFirst();
        usage.setMaxWidth(Double.MAX_VALUE);
        TextField ram=new TextField(summary.minRamGb()==null?"":summary.minRamGb().toString());ram.setId("requestRam");ram.setPromptText("По сценарию");
        TextField storage=new TextField(summary.minStorageGb()==null?"":summary.minStorageGb().toString());storage.setId("requestStorage");storage.setPromptText("По сценарию");
        CheckBox verified=new CheckBox("Только проверенная совместимость");verified.setId("requestVerified");verified.setSelected(summary.requireVerified());verified.setWrapText(true);
        CheckBox clearOwned=new CheckBox("Сбросить список имеющихся деталей");clearOwned.setId("requestClearOwned");clearOwned.setWrapText(true);clearOwned.setVisible(!summary.ownedParts().isEmpty());clearOwned.setManaged(!summary.ownedParts().isEmpty());
        Label error = label("", "chat-error"); error.setMinHeight(Region.USE_PREF_SIZE); error.setVisible(false); error.setManaged(false);
        VBox form = new VBox(10, label("Бюджет покупки, ₽", "chat-field-label"), budget, label("Основные задачи", "chat-field-label"), usage,label("Минимум ОЗУ, ГБ", "chat-field-label"),ram,label("Минимум SSD, ГБ", "chat-field-label"),storage,verified,clearOwned,error);
        form.setMinWidth(0);form.setPrefWidth(400);error.setMaxWidth(400);dialog.getDialogPane().setMinWidth(440);dialog.getDialogPane().setContent(form);
        for(TextField field:List.of(budget,ram,storage))field.textProperty().addListener((o,a,b)->{error.setVisible(false);error.setManaged(false);});
        ButtonType apply = new ButtonType("Подобрать", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Отмена",ButtonBar.ButtonData.CANCEL_CLOSE), apply);
        Node applyButton = dialog.getDialogPane().lookupButton(apply);
        ((Button)applyButton).setMinWidth(140);((Button)applyButton).setPrefWidth(140);ButtonBar.setButtonUniformSize(applyButton,false);
        applyButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                BigDecimal amount = requestBudget(budget.getText());
                validateCapacity(ram.getText());validateCapacity(storage.getText());
                if (amount.compareTo(BigDecimal.ONE) < 0 || amount.compareTo(new BigDecimal("100000000")) > 0) throw new NumberFormatException();
            } catch (IllegalArgumentException ex) {
                error.setText("Бюджет: от 1 до 100 млн ₽.\nОбъёмы: целые ГБ от 1 до 1 000 000 или пустое поле."); error.setVisible(true); error.setManaged(true);
                dialog.getDialogPane().applyCss();
                if (dialog.getDialogPane().getScene().getWindow() instanceof Stage stage) stage.sizeToScene();
                event.consume();
            }
        });
        return dialog.showAndWait().filter(apply::equals).map(result -> (clearOwned.isSelected()?"Сбросить имеющиеся детали; ":"")+"Собери ПК до " + requestBudget(budget.getText()).toPlainString() + " рублей для " + usage.getValue().toLowerCase(Locale.ROOT)
                + ", сбрось ограничения,"+(ram.getText().isBlank()?"":" ОЗУ "+ram.getText().trim()+" ГБ,")
                +(storage.getText().isBlank()?"":" SSD "+storage.getText().trim()+" ГБ,")
                +(verified.isSelected()?" только проверенная совместимость":" можно черновую сборку"));
    }

    private static BigDecimal requestBudget(String value) {
        return Money.parsePrice(value.replaceAll("[\\s\\u00a0\\u202f]",""));
    }
    private static void validateCapacity(String value) {
        if(value.isBlank()) return;
        if(!value.trim().matches("[0-9]+")) throw new NumberFormatException();
        int amount=Integer.parseInt(value.trim());if(amount<1 || amount>1_000_000) throw new NumberFormatException();
    }
    private static String usageLabel(BuildRecommendationService.Usage usage) {
        if (usage == null) return "Не выбраны";
        return switch (usage) {
            case GAMING -> "Игры";
            case OFFICE -> "Учёба и офис";
            case WORKSTATION -> "Монтаж и рабочие задачи";
        };
    }
    private static Label label(String text, String style) {
        Label result = new Label(text);
        result.setWrapText(true); result.setMinWidth(0); result.setMaxWidth(Double.MAX_VALUE);
        result.getStyleClass().add(style);
        return result;
    }
}
