package com.example.authapp.ui;

import com.example.authapp.domain.ComponentCategory;
import com.example.authapp.models.Product;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

import java.math.BigDecimal;
import java.net.URI;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Consumer;

/** Shared, local JavaFX presentation primitives. No browser or remote renderer. */
public final class Ui {
    private Ui() {}
    private static Consumer<String> urlOpener = value -> {};
    private static final Map<String, Image> IMAGES = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Image> entry) { return size() > 128; }
    };
    private static final Map<String,String> PATHS = Map.ofEntries(
            Map.entry("grid", "M3 3H10V10H3ZM14 3H21V10H14ZM3 14H10V21H3ZM14 14H21V21H14Z"),
            Map.entry("layers", "M12 3 22 8 12 13 2 8ZM3 13 12 18 21 13M3 18 12 23 21 18"),
            Map.entry("chat", "M21 11.5A8.5 8.5 0 0 1 9 19L3 21 5 15A8.5 8.5 0 1 1 21 11.5ZM8 10H16M8 14H13"),
            Map.entry("bag", "M4 7H20V21H4ZM8 8V6A4 4 0 0 1 16 6V8"),
            Map.entry("user", "M15.5 8A3.5 3.5 0 1 1 8.5 8A3.5 3.5 0 1 1 15.5 8ZM5 21V19A7 7 0 0 1 19 19V21"),
            Map.entry("search", "M17 10.5A6.5 6.5 0 1 1 4 10.5A6.5 6.5 0 1 1 17 10.5ZM16 16 21 21"),
            Map.entry("arrow-right", "M4 12H20M14 6 20 12 14 18"),
            Map.entry("chevron-down", "M5 9 12 16 19 9"),
            Map.entry("cpu", "M6 6H18V18H6ZM9 9H15V15H9ZM9 2V6M15 2V6M9 18V22M15 18V22M2 9H6M2 15H6M18 9H22M18 15H22"),
            Map.entry("gpu", "M2 5H21V18H2ZM11 11.5A3 3 0 1 1 5 11.5A3 3 0 1 1 11 11.5ZM19 11.5A3 3 0 1 1 13 11.5A3 3 0 1 1 19 11.5ZM5 18V21M9 18V21M13 18V21"),
            Map.entry("memory", "M2 7H22V17H2ZM5 10H8V14H5ZM11 10H14V14H11ZM17 10H19V14H17ZM5 17V20M9 17V20M13 17V20M17 17V20"),
            Map.entry("drive", "M3 5H21V19H3ZM3 15H21M16 17H18"),
            Map.entry("board", "M3 3H21V21H3ZM7 6H14V13H7ZM17 6V14M7 17H17"),
            Map.entry("power", "M12 2V11M5.6 5.6A9 9 0 1 0 18.4 5.6"),
            Map.entry("case", "M6 2H19V22H6ZM6 8H19M10 5H14M10 12H15M10 16H15M10 19H15"),
            Map.entry("cooler", "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12ZM14 12A2 2 0 1 1 10 12A2 2 0 1 1 14 12ZM12 10C17 3 20 9 14 12M12 14C7 21 4 15 10 12M10 12C3 7 9 4 12 10M14 12C21 17 15 20 12 14"),
            Map.entry("info", "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12ZM12 11V17M12 7V7.2"),
            Map.entry("warning", "M12 3 22 21H2ZM12 9V14M12 17V17.2"),
            Map.entry("check", "M5 12 9 16 20 5"),
            Map.entry("edit", "M15 4 20 9M4 20 8 19 21 6 17 2 4 15 3 21Z"),
            Map.entry("send", "M12 20V4M6 10 12 4 18 10"),
            Map.entry("refresh", "M20 7V12H15M4 17V12H9M5 8A8 8 0 0 1 18 5L20 8M4 16 6 19A8 8 0 0 0 19 16"),
            Map.entry("trash", "M3 6H21M9 6V3H15V6M5 6 6 21H18L19 6M10 10V17M14 10V17"),
            Map.entry("close", "M6 6 18 18M6 18 18 6"),
            Map.entry("plus", "M12 4V20M4 12H20"),
            Map.entry("clock", "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12ZM12 7V12L15 14"),
            Map.entry("spark", "M12 3 14.5 9.5 21 12 14.5 14.5 12 21 9.5 14.5 3 12 9.5 9.5Z")
    );
    public static Node icon(String name) {
        SVGPath path = new SVGPath(); path.setContent(PATHS.getOrDefault(name, PATHS.get("info")));
        path.getStyleClass().add("icon-shape"); path.setScaleX(.77); path.setScaleY(.77);
        StackPane frame = new StackPane(path); frame.setMinSize(20,20);frame.setPrefSize(20,20);frame.setMaxSize(20,20);
        frame.getStyleClass().add("ui-icon"); frame.setMouseTransparent(true); return frame;
    }
    public static String categoryIcon(String category) {
        return ComponentCategory.fromDisplayName(category).map(c -> switch(c) {
            case CPU -> "cpu";case GPU -> "gpu";case MOTHERBOARD -> "board";case RAM -> "memory";
            case STORAGE -> "drive";case PSU -> "power";case CASE -> "case";case COOLER -> "cooler";
        }).orElse("grid");
    }
    public static Node productImage(Product product, double width, double height) {
        String category = categoryIcon(product.getCategory());
        StackPane frame = new StackPane(); frame.setMinSize(0,0); frame.setPrefSize(width,height);frame.setMaxSize(Double.MAX_VALUE,height);
        frame.getStyleClass().add("product-image");
        Node placeholder;
        var resource = Ui.class.getResource("/images/components/"+category+".png");
        if (resource != null) {
            ImageView illustration = new ImageView(new Image(resource.toExternalForm()));illustration.setPreserveRatio(true);
            illustration.fitWidthProperty().bind(frame.widthProperty().multiply(.84));illustration.setFitHeight(height*(height>=100?.75:.86));
            placeholder=illustration;
        } else { placeholder=icon(category);placeholder.setScaleX(3);placeholder.setScaleY(3); }
        if(height>=100){
            Label caption=new Label("Иллюстрация категории");caption.getStyleClass().add("image-caption");
            StackPane group=new StackPane(placeholder,caption);StackPane.setAlignment(caption,javafx.geometry.Pos.BOTTOM_CENTER);placeholder=group;
        }
        frame.getChildren().add(placeholder);
        frame.setAccessibleText("Изображение отсутствует. Условная иллюстрация категории: "+Objects.toString(product.getCategory(),"Комплектующие"));
        String url=product.getImageUrl();
        if(url==null || url.isBlank())return frame;
        try {
            URI uri=URI.create(url);
            if(!Set.of("https","http").contains(uri.getScheme()) || uri.getHost()==null)return frame;
            Image image=IMAGES.computeIfAbsent(url,key->new Image(key,width*2,height*2,true,true,true));
            if(image.isError())return frame;
            ImageView view=new ImageView(image);view.setPreserveRatio(true);view.fitWidthProperty().bind(frame.widthProperty().multiply(.88));view.setFitHeight(height*.92);
            Node fallback=placeholder;
            Runnable show=()->{if(!image.isError() && image.getProgress()>=1){frame.getChildren().setAll(view);frame.setAccessibleText("Изображение: "+product.getName());}else frame.getChildren().setAll(fallback);};
            ChangeListener<Number> listener=(o,a,b)->show.run();
            image.progressProperty().addListener(new WeakChangeListener<>(listener));frame.getProperties().put("image-listener",listener);show.run();
        }catch(IllegalArgumentException ignored){ /* Invalid public image URL retains a local placeholder. */ }
        return frame;
    }
    public static Label label(String text,String style) { Label label=new Label(text);label.getStyleClass().add(style);return label; }
    public static String money(double value) { return Double.isFinite(value) ? money(BigDecimal.valueOf(value)) : "Цена не указана"; }
    public static String money(BigDecimal value) {
        NumberFormat format=NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));format.setMaximumFractionDigits(2);
        return format.format(value)+" ₽";
    }
    public static void theme(Parent root) {
        String url=Objects.requireNonNull(Ui.class.getResource("/views/styles/theme.css")).toExternalForm();
        if(!root.getStylesheets().contains(url))root.getStylesheets().add(0,url);
    }
    public static void theme(Dialog<?> dialog) { theme(dialog.getDialogPane());dialog.getDialogPane().getStyleClass().add("app-dialog"); }
    public static void setUrlOpener(Consumer<String> opener) { urlOpener=Objects.requireNonNull(opener); }
    public static void openExternal(String url) {
        try { URI uri=URI.create(url);if(Set.of("https","http").contains(uri.getScheme()) && uri.getHost()!=null)urlOpener.accept(uri.toASCIIString()); }
        catch(IllegalArgumentException ignored){ /* Do not open non-web or malformed source links. */ }
    }
}
