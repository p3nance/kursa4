package com.example.authapp.services;

import controllers.ChatController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import org.json.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in Windows Glass review. Per-process scale only; no OS display settings or real account. */
public final class NativeChatReview {
    private static Stage stage;
    private static Parent root;
    private static ChatController controller;
    private static double maxHeartbeatMs;
    public static void main(String[] args) throws Exception {
        var ready=new CountDownLatch(1);Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});
        if(!ready.await(10,TimeUnit.SECONDS))throw new IllegalStateException("JavaFX startup timeout");
        Path output=Path.of(args[0]);Files.createDirectories(output);
        var report=new JSONObject().put("backend","Windows Glass").put("os",System.getProperty("os.name"))
                .put("requestedScale",System.getProperty("glass.win.uiScale")).put("scenes",new JSONArray());
        try {
            var catalog=ChatQualityProbe.loadCatalog();
            fx(()->{
                var loader=new FXMLLoader(NativeChatReview.class.getResource("/views/chat.fxml"));root=loader.load();controller=loader.getController();
                controller.configure(null,catalog);stage=new Stage();stage.setTitle("TechStore — R32 local review");stage.setScene(new Scene(root,1200,700));stage.show();
                report.put("outputScaleX",stage.getOutputScaleX()).put("outputScaleY",stage.getOutputScaleY());return null;
            });
            send("Собери ПК до 100000 рублей для игр");awaitAnswer();
            send("Теперь могу выделить 150 тысяч, вместо игр буду монтировать видео");awaitAnswer();
            var runtime=Runtime.getRuntime();
            var memory=new JSONObject().put("sampleContext","Qwen loaded and JavaFX stage visible after free follow-up")
                    .put("jvmHeapUsedBytes",runtime.totalMemory()-runtime.freeMemory()).put("jvmHeapCommittedBytes",runtime.totalMemory()).put("jvmHeapMaxBytes",runtime.maxMemory());
            var gpu=new ProcessBuilder("C:\\Windows\\System32\\nvidia-smi.exe","--query-gpu=name,memory.total,memory.used,utilization.gpu","--format=csv,noheader").redirectErrorStream(true).start();
            if(!gpu.waitFor(5,TimeUnit.SECONDS)){gpu.destroyForcibly();throw new AssertionError("GPU sample timeout");}
            if(gpu.exitValue()!=0)throw new AssertionError("GPU sample failed");
            memory.put("gpuGlobalCsv",new String(gpu.getInputStream().readAllBytes(),StandardCharsets.UTF_8).strip());
            report.put("memory",memory);
            fx(()->{
                if(!((Label)root.lookup("#budgetValue")).getText().contains("150") || !((Label)root.lookup("#taskValue")).getText().contains("Монтаж"))
                    throw new AssertionError("Native view did not reflect free follow-up");return null;
            });
            for(int[] size:List.of(new int[]{1200,700},new int[]{900,600})) {
                fx(()->{stage.setWidth(size[0]);stage.setHeight(size[1]);return null;});Thread.sleep(150);
                fx(()->{capture(output,"dialogue-"+size[0],report);return null;});
            }
            send("Почему именно такой состав?");awaitAnswer();
            fx(()->{
                if(!((Label)root.lookup("#status")).getText().contains("объяснение"))throw new AssertionError("Grounded explanation missing");
                capture(output,"explanation-900",report);return null;
            });
            send("Найди Ryzen");awaitAnswer();send("2");awaitAnswer();send("Сколько он стоит?");awaitAnswer();
            fx(()->{
                var messages=(javafx.scene.layout.VBox)root.lookup("#messages");var last=messages.getChildren().getLast();
                boolean chosen=last.lookupAll("*").stream().filter(Label.class::isInstance).map(Label.class::cast).anyMatch(l->l.getText().contains("Ryzen 5 7600X"));
                if(!chosen || !((Label)root.lookup("#budgetValue")).getText().contains("150"))throw new AssertionError("Product reference lost identity/budget");
                capture(output,"context-product-900",report);return null;
            });
            send("Увеличь бюджет на 10%");awaitAnswer();
            fx(()->{
                if(!((Label)root.lookup("#status")).getText().contains("рублях"))throw new AssertionError("Input problem was shown as model failure");
                capture(output,"input-clarification-900",report);return null;
            });
            send("Найди Ryzen");awaitAnswer();send("Насколько второй дороже первого?");awaitAnswer();Thread.sleep(150);
            fx(()->{
                var messages=(javafx.scene.layout.VBox)root.lookup("#messages");var last=messages.getChildren().getLast();
                if(last.lookupAll("*").stream().filter(Label.class::isInstance).map(Label.class::cast).noneMatch(l->l.getText().contains("Разница цен:")))
                    throw new AssertionError("Computed price difference missing");
                capture(output,"group-price-difference-900",report);
                var amount=last.lookup(".chat-price-difference");
                var viewport=((ScrollPane)root.lookup("#conversation")).lookup(".viewport");
                var amountBounds=amount.localToScene(amount.getLayoutBounds());var visible=viewport.localToScene(viewport.getLayoutBounds());
                if(amountBounds.getMinY()<visible.getMinY() || amountBounds.getMaxY()>visible.getMaxY())throw new AssertionError("Price difference is outside the visible viewport");
                report.put("priceDifferenceVisible",true);return null;
            });
            send("Найди Ryzen до 30 тысяч");awaitAnswer();Thread.sleep(150);
            fx(()->{
                var messages=(javafx.scene.layout.VBox)root.lookup("#messages");var last=messages.getChildren().getLast();
                var labels=last.lookupAll("*").stream().filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).toList();
                if(labels.stream().noneMatch(t->t.contains("7600X")) || labels.stream().anyMatch(t->t.contains("7900X"))
                        || !((Label)root.lookup("#budgetValue")).getText().contains("150"))throw new AssertionError("Item filter lost price scope or build budget");
                capture(output,"catalog-price-filter-900",report);return null;
            });
            send("У меня 20 тысяч на процессор");awaitAnswer();Thread.sleep(150);
            fx(()->{
                if(!((Label)root.lookup("#budgetValue")).getText().contains("150") || !((Label)root.lookup("#status")).getText().contains("цены"))
                    throw new AssertionError("Part money changed whole-build budget");
                capture(output,"part-money-clarification-900",report);return null;
            });
            send("Сравни RTX4060 и RX7600 для игр в Black Myth: Wukong1440p");awaitAnswer();Thread.sleep(150);
            fx(()->{
                var messages=(javafx.scene.layout.VBox)root.lookup("#messages");var last=messages.getChildren().getLast();
                var scope=(Label)last.lookup(".chat-benchmark-scope");
                if(scope==null||!scope.getText().contains("не совпадает")||!scope.isManaged())throw new AssertionError("Source conflict hidden from benchmark card");
                capture(output,"source-platform-conflict-900",report);
                if(scope.getHeight()+1<scope.prefHeight(scope.getWidth()))throw new AssertionError("Source conflict note clipped");
                var viewport=((ScrollPane)root.lookup("#conversation")).lookup(".viewport");
                var bounds=scope.localToScene(scope.getLayoutBounds());var visible=viewport.localToScene(viewport.getLayoutBounds());
                if(bounds.getMinY()<visible.getMinY() || bounds.getMaxY()>visible.getMaxY())throw new AssertionError("Source conflict is outside the visible viewport");
                report.put("sourceConflictVisible",true);
                report.put("sourceConflictExpanded",true);return null;
            });
            send("Сравни Ryzen5 7600 и Ryzen7 7800X3D для игр в F1 25 1080p");awaitAnswer();Thread.sleep(150);
            fx(()->{
                var messages=(javafx.scene.layout.VBox)root.lookup("#messages");var last=messages.getChildren().getLast();
                var labels=last.lookupAll("*").stream().filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).toList();
                for(String value:List.of("F1 25","217.0","302.3"))if(labels.stream().noneMatch(t->t.contains(value)))throw new AssertionError("F1 25 value missing: "+value);
                if(labels.stream().anyMatch(t->t.contains("438.3")))throw new AssertionError("F1 24 score leaked into25");
                capture(output,"f1-25-900",report);return null;
            });
            send("Поменяй местами");awaitAnswer();
            clickLastChip("Сравнить характеристики");awaitAnswer();
            fx(()->{
                var messages=(javafx.scene.layout.VBox)root.lookup("#messages");var last=messages.getChildren().getLast();
                if(last.lookup(".chat-specifications-card")==null)throw new AssertionError("Comparison view button lost selected pair");return null;
            });
            clickLastChip("Сравнить по бенчмаркам");awaitAnswer();Thread.sleep(150);
            fx(()->{
                var messages=(javafx.scene.layout.VBox)root.lookup("#messages");var last=messages.getChildren().getLast();
                var labels=last.lookupAll("*").stream().filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).toList();
                for(String value:List.of("F1 25","39.31"))if(labels.stream().noneMatch(t->t.contains(value)))throw new AssertionError("Reversed pair/view lost: "+value);
                if(!((Label)root.lookup("#budgetValue")).getText().contains("150"))throw new AssertionError("Order/view changed build budget");
                capture(output,"comparison-order-900",report);return null;
            });
            send("Сравни Intel Core i3-14100F и AMD Ryzen 5 7600X");awaitAnswer();Thread.sleep(150);
            fx(()->{reviewRenderingScope(output,"r40-cpu-rendering-900",report,"двух тестовых платформ",List.of("486","781"));return null;});
            send("Сравни NVIDIA GeForce RTX 4060 Ti 16GB и NVIDIA GeForce RTX 4070 12GB");awaitAnswer();Thread.sleep(150);
            fx(()->{reviewRenderingScope(output,"r40-gpu-rendering-900",report,"Образцы одного обзора",List.of("1831","2481"));return null;});
            send("Собери ПК до 100000 рублей для игр");awaitAnswer();
            send("замени видеокарту на 4080");awaitAnswer();
            clickLastChip("NVIDIA GeForce RTX 4080 SUPER 16GB");awaitAnswer();Thread.sleep(150);
            fx(()->{
                var messages=(javafx.scene.layout.VBox)root.lookup("#messages");var last=messages.getChildren().getLast();
                var labels=last.lookupAll("*").stream().filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).toList();
                for(String value:List.of("Заменил","4080 SUPER","Бюджет превышен"))if(labels.stream().noneMatch(t->t.contains(value)))throw new AssertionError("Build edit missing: "+value);
                if(!((Label)root.lookup("#budgetValue")).getText().contains("100"))throw new AssertionError("Build edit changed requested budget");
                capture(output,"r41-build-edit-900",report);report.put("buildEditConfirmed",true);return null;
            });
            send("поменяй процессор на амд");awaitAnswer();Thread.sleep(150);
            fx(()->{
                var messages=(javafx.scene.layout.VBox)root.lookup("#messages");var last=messages.getChildren().getLast();
                var choices=last.lookupAll(".chat-chip").stream().filter(Button.class::isInstance).map(Button.class::cast).map(Button::getText).toList();
                if(choices.size()<2 || choices.stream().anyMatch(t->!t.startsWith("AMD Ryzen")))throw new AssertionError("Brand request did not offer AMD CPUs");
                capture(output,"r42-brand-choice-900",report);report.put("amdChoicesVisible",true);return null;
            });
            send("АМД Райзен 9 7950Икс");awaitAnswer();Thread.sleep(150);
            fx(()->{
                var messages=(javafx.scene.layout.VBox)root.lookup("#messages");var last=messages.getChildren().getLast();
                var labels=last.lookupAll("*").stream().filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).toList();
                for(String value:List.of("Заменил","AMD Ryzen 9 7950X","4080 SUPER"))if(labels.stream().noneMatch(t->t.contains(value)))throw new AssertionError("Russian model selection lost build identity: "+value);
                capture(output,"r42-russian-cpu-900",report);report.put("russianCpuSelected",true);return null;
            });
            send("Найди Лоджитек Джи Про Икс TKL");awaitAnswer();Thread.sleep(150);
            fx(()->{capture(output,"r42-russian-product-900",report);return null;});
            fx(()->{root.lookupAll(".chat-reset").stream().map(Button.class::cast).findFirst().orElseThrow().fire();return null;});
            clickLastChip("Подобрать сборку");awaitAnswer();
            clickLastChip("До 150 000 рублей");awaitAnswer();
            clickLastChip("Для монтажа и рабочих задач");awaitAnswer();Thread.sleep(150);
            fx(()->{
                var messages=(javafx.scene.layout.VBox)root.lookup("#messages");var last=messages.getChildren().getLast();
                if(last.lookup(".chat-build-row")==null || !((Label)root.lookup("#taskValue")).getText().contains("Монтаж")
                        || !((Label)root.lookup("#budgetValue")).getText().contains("150"))throw new AssertionError("Workstation purpose button did not produce the requested build");
                capture(output,"r43-workstation-button-900",report);report.put("workstationButtonApplied",true);return null;
            });
            send("поменяй проц на амд");awaitAnswer();
            fx(()->{
                var last=((javafx.scene.layout.VBox)root.lookup("#messages")).getChildren().getLast();
                if(last.lookup(".chat-build-row")!=null || last.lookupAll(".chat-chip").size()<2)throw new AssertionError("Short CPU brand request regenerated the build");
                return null;
            });
            send("поменяй проц на амд 7800");awaitAnswer();Thread.sleep(150);
            fx(()->{
                var last=((javafx.scene.layout.VBox)root.lookup("#messages")).getChildren().getLast();
                var choices=last.lookupAll(".chat-chip").stream().map(Button.class::cast).map(Button::getText).toList();
                if(!choices.equals(List.of("AMD Ryzen 7 7800X3D")) || last.lookup(".chat-build-row")!=null)throw new AssertionError("Partial CPU name lost confirmation or category");
                capture(output,"r44-short-cpu-confirmation-900",report);return null;
            });
            clickLastChip("AMD Ryzen 7 7800X3D");awaitAnswer();Thread.sleep(150);
            fx(()->{
                var last=((javafx.scene.layout.VBox)root.lookup("#messages")).getChildren().getLast();
                var labels=last.lookupAll("*").stream().filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).toList();
                for(String value:List.of("Заменил","AMD Ryzen 7 7800X3D","конфликты"))if(labels.stream().noneMatch(t->t.contains(value)))throw new AssertionError("Short CPU replacement missing: "+value);
                if(!((Label)root.lookup("#budgetValue")).getText().contains("150") || !((Label)root.lookup("#taskValue")).getText().contains("Монтаж"))throw new AssertionError("Short CPU replacement changed request parameters");
                capture(output,"r44-short-cpu-replaced-900",report);report.put("shortCpuReplacementApplied",true);return null;
            });
            System.setProperty("techstore.chat.model","r32-model-that-is-not-installed");
            fx(()->{root.lookupAll(".chat-reset").stream().map(Button.class::cast).findFirst().orElseThrow().fire();return null;});
            send("Очень интересует наличие Ryzen 5 7600");awaitAnswer();
            fx(()->{
                if(!((Label)root.lookup("#status")).getText().contains("недоступна"))throw new AssertionError("Missing model status absent");
                capture(output,"fallback-900",report);return null;
            });
            report.put("maxFxHeartbeatMs",maxHeartbeatMs).put("result","PASS");
            if(maxHeartbeatMs>1000)throw new AssertionError("UI worker blocked the FX event loop");
        } finally {
            Files.writeString(output.resolve("review.json"),report.toString(2)+"\n",StandardCharsets.UTF_8);
            fx(()->{if(controller!=null)controller.dispose();if(stage!=null)stage.close();return null;});Platform.exit();
        }
        System.out.println(report.toString(2));
    }
    private static <T>T fx(Callable<T> call) throws Exception {var task=new FutureTask<>(call);Platform.runLater(task);return task.get(10,TimeUnit.SECONDS);}
    private static void send(String text) throws Exception {fx(()->{((TextArea)root.lookup("#input")).setText(text);((Button)root.lookup("#sendButton")).fire();return null;});}
    private static void clickLastChip(String text) throws Exception {
        fx(()->{
            var messages=(javafx.scene.layout.VBox)root.lookup("#messages");
            messages.getChildren().getLast().lookupAll(".chat-chip").stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b->b.getText().equals(text)).findFirst().orElseThrow().fire();return null;
        });
    }
    private static void awaitAnswer() throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(65);
        while(System.nanoTime()<deadline) {
            long start=System.nanoTime();boolean ready=fx(()->!((Button)root.lookup("#sendButton")).isDisabled());
            maxHeartbeatMs=Math.max(maxHeartbeatMs,(System.nanoTime()-start)/1_000_000.0);
            if(ready)return;Thread.sleep(25);
        }throw new AssertionError("Native answer timeout");
    }
    private static void reviewRenderingScope(Path output,String name,JSONObject report,String scopeText,List<String> values) throws Exception {
        var messages=(javafx.scene.layout.VBox)root.lookup("#messages");var last=messages.getChildren().getLast();
        var scope=(Label)last.lookup(".chat-benchmark-scope");
        if(scope==null||!scope.getText().contains(scopeText))throw new AssertionError("Published rendering sample limit absent: "+name);
        var labels=last.lookupAll("*").stream().filter(Label.class::isInstance).map(Label.class::cast).map(Label::getText).toList();
        for(String value:values)if(labels.stream().noneMatch(t->t.contains(value)))throw new AssertionError("Rendering measurement absent: "+value);
        capture(output,name,report);
        var viewport=((ScrollPane)root.lookup("#conversation")).lookup(".viewport");
        var bounds=scope.localToScene(scope.getLayoutBounds());var visible=viewport.localToScene(viewport.getLayoutBounds());
        if(scope.getHeight()+1<scope.prefHeight(scope.getWidth())||bounds.getMinY()<visible.getMinY()||bounds.getMaxY()>visible.getMaxY())
            throw new AssertionError("Rendering source limitation clipped or outside viewport: "+name);
        report.put(name+"ScopeVisible",true);
    }

    private static void capture(Path output,String name,JSONObject report) throws Exception {
        root.applyCss();root.layout();
        var scroll=(ScrollPane)root.lookup("#conversation");var compose=root.lookup(".chat-compose-box");
        var history=scroll.localToScene(scroll.getLayoutBounds());var input=compose.localToScene(compose.getLayoutBounds());
        if(history.getHeight()<100 || history.getMaxY()>input.getMinY()+1 || input.getMaxY()>root.getLayoutBounds().getMaxY())throw new AssertionError("Native composer/history overlap");
        Label status=(Label)root.lookup("#status");if(status.prefHeight(status.getWidth())>status.getHeight()+2)throw new AssertionError("Status clipped");
        double scale=stage.getOutputScaleX();var parameters=new SnapshotParameters();parameters.setTransform(new Scale(scale,scale));
        WritableImage image=root.snapshot(parameters,null);BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        ImageIO.write(png,"png",output.resolve(name+".png").toFile());
        report.getJSONArray("scenes").put(new JSONObject().put("name",name).put("logicalWidth",root.getLayoutBounds().getWidth())
                .put("logicalHeight",root.getLayoutBounds().getHeight()).put("pixelWidth",png.getWidth()).put("pixelHeight",png.getHeight()).put("status",status.getText()));
    }
}
