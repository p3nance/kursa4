package com.example.authapp.ui;

import com.example.authapp.dto.ProductDTO;
import com.example.authapp.network.Money;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.*;

/** One client-side editor for both management screens. No repository or source DTO is modified here. */
public final class ProductEditDialog {
    private ProductEditDialog() {}
    public static Optional<ProductDTO> show(Window owner,ProductDTO source) {
        Dialog<ProductDTO> dialog=new Dialog<>();Ui.theme(dialog);
        if(owner!=null)dialog.initOwner(owner);
        dialog.setTitle(source==null?"Добавить товар":"Редактировать товар");
        dialog.setHeaderText(source==null?"Новый товар":"Товар №"+source.id);
        dialog.getDialogPane().getStylesheets().add(ProductEditDialog.class.getResource("/views/styles/dialogs.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("product-editor");dialog.getDialogPane().setMinWidth(440);dialog.getDialogPane().setPrefWidth(480);
        TextField name=field(source==null?"":source.name,"productName");
        TextField price=field(source==null||source.price==null?"":source.price.toPlainString(),"productPrice");
        TextField stock=field(source==null?"0":Integer.toString(source.stock),"productStock");
        TextField category=field(source==null?"":source.category,"productCategory");
        TextField manufacturer=field(source==null?"":source.manufacturer,"productManufacturer");
        TextArea description=new TextArea(source==null?"":Objects.toString(source.description,""));description.setId("productDescription");description.setWrapText(true);description.setPrefRowCount(3);description.setMinWidth(0);
        GridPane form=new GridPane();form.getStyleClass().add("product-editor-form");form.setMinWidth(0);
        ColumnConstraints captions=new ColumnConstraints();captions.setMinWidth(120);captions.setPrefWidth(120);captions.setMaxWidth(120);
        ColumnConstraints fields=new ColumnConstraints();fields.setMinWidth(0);fields.setHgrow(Priority.ALWAYS);form.getColumnConstraints().setAll(captions,fields);
        add(form,0,"Название *",name);add(form,1,"Цена, ₽ *",price);add(form,2,"Остаток, шт. *",stock);
        add(form,3,"Категория *",category);add(form,4,"Производитель",manufacturer);add(form,5,"Описание",description);
        Label error=new Label();error.setId("productError");error.setWrapText(true);error.setMinWidth(0);error.setMaxWidth(420);error.getStyleClass().add("product-editor-error");error.setVisible(false);error.setManaged(false);
        VBox content=new VBox(form,error);content.setMinWidth(0);dialog.getDialogPane().setContent(content);
        for(TextField input:List.of(name,price,stock,category,manufacturer))input.textProperty().addListener((o,a,b)->{error.setVisible(false);error.setManaged(false);});
        ButtonType save=new ButtonType("Сохранить",ButtonBar.ButtonData.OK_DONE),cancel=new ButtonType("Отмена",ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(cancel,save);
        Button submit=(Button)dialog.getDialogPane().lookupButton(save);submit.setMinWidth(132);ButtonBar.setButtonUniformSize(submit,false);
        ProductDTO[] accepted=new ProductDTO[1];
        submit.addEventFilter(ActionEvent.ACTION,event->{
            try {
                if(name.getText().isBlank()||category.getText().isBlank())throw new IllegalArgumentException("Заполните название и категорию товара.");
                var amount=Money.parsePrice(price.getText());
                String quantity=stock.getText().strip();
                if(!quantity.matches("[0-9]+"))throw new IllegalArgumentException("Остаток должен быть целым неотрицательным числом.");
                int count;
                try{count=Integer.parseInt(quantity);}catch(NumberFormatException ex){throw new IllegalArgumentException("Указан слишком большой остаток.");}
                accepted[0]=new ProductDTO(source==null?0:source.id,name.getText().strip(),description.getText().strip(),amount,count,
                        source==null?"":Objects.toString(source.imageUrl,""),category.getText().strip(),manufacturer.getText().strip());
            }catch(IllegalArgumentException ex){error.setText(ex.getMessage());error.setVisible(true);error.setManaged(true);event.consume();}
        });
        dialog.setResultConverter(button->button==save?accepted[0]:null);
        return dialog.showAndWait();
    }
    private static TextField field(String value,String id) {var result=new TextField(Objects.toString(value,""));result.setId(id);result.setMinWidth(0);return result;}
    private static void add(GridPane grid,int row,String caption,javafx.scene.control.Control input) {
        Label label=new Label(caption);label.setLabelFor(input);label.setWrapText(true);grid.add(label,0,row);grid.add(input,1,row);GridPane.setHgrow(input,Priority.ALWAYS);
    }
}
