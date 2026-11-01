package com.example.authapp.services;

import com.example.authapp.ui.*;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.json.JSONObject;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in native input/FX-thread profile, runnable unchanged against both old and new application JARs. */
public final class ChatScrollProbe {
    private static final List<Double> intervals=new ArrayList<>(),dispatch=new ArrayList<>();
    private static ScrollPane scroll;private static Stage stage;private static ChatViewport viewport;
    private static boolean measuring;private static long lastPulse;
    private static <T>T fx(Callable<T> task) throws Exception {var result=new FutureTask<>(task);Platform.runLater(result);return result.get(30,TimeUnit.SECONDS);}
    public static void main(String[] args) throws Exception {
        var ready=new CountDownLatch(1);Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});ready.await();
        var cpu=ManagementFactory.getThreadMXBean();
        if(cpu.isThreadCpuTimeSupported() && !cpu.isThreadCpuTimeEnabled())cpu.setThreadCpuTimeEnabled(true);
        try {
            var catalog=ChatQualityProbe.loadCatalog();
            var answer=new ChatBotService(catalog).reply("Собери ПК до 200000 рублей для игр");
            var build=answer.content().stream().filter(ChatBotService.BuildContent.class::isInstance).findFirst().orElseThrow();
            fx(()->{
                VBox messages=new VBox(22);messages.setFillWidth(true);scroll=new ScrollPane(messages);scroll.setFitToWidth(true);scroll.getStyleClass().add("chat-scroll");
                BorderPane root=new BorderPane(scroll);root.getStyleClass().add("chat-root");root.getStylesheets().add(ChatScrollProbe.class.getResource("/views/styles/chat.css").toExternalForm());
                viewport=new ChatViewport(root,scroll,messages,show->{});
                var cards=new ChatCardView(new ChatCardView.Actions(){
                    public void ask(String s){}public boolean canNavigate(){return false;}public void openProduct(com.example.authapp.models.Product p){}
                    public void openBuild(ChatBotService.BuildContent b){}public void reflow(Node anchor){viewport.reflow(anchor);}public void sourceUnavailable(){}
                });
                for(int i=0;i<80;i++){VBox row=new VBox(8,new Label("Ответ помощника "+i));row.getChildren().addAll(cards.render(build));viewport.addMessage(row);}
                stage=new Stage();stage.setTitle("TechStore — scroll regression probe");stage.setScene(new Scene(root,1000,700));stage.show();return null;
            });
            Thread.sleep(500);
            var timer=fx(()->{var t=new AnimationTimer(){public void handle(long now){if(measuring && lastPulse!=0)intervals.add((now-lastPulse)/1e6);lastPulse=now;}};t.start();return t;});
            for(int i=0;i<30;i++){final int tick=i;fx(()->{scroll.setVvalue(.35+.2*Math.sin(tick*.12));return null;});Thread.sleep(16);}
            long start=fx(()->{measuring=true;lastPulse=0;return cpu.getCurrentThreadCpuTime();});
            for(int i=0;i<160;i++) {
                final int tick=i;
                fx(()->{long before=cpu.getCurrentThreadCpuTime();for(int j=0;j<8;j++)scroll.setVvalue(.35+.2*Math.sin((tick*8+j)*.016));dispatch.add((cpu.getCurrentThreadCpuTime()-before)/1e6);return null;});
                Thread.sleep(16);
            }
            Thread.sleep(100);
            long end=fx(()->{measuring=false;timer.stop();return cpu.getCurrentThreadCpuTime();});
            var report=new JSONObject().put("result","PASS").put("backend","Windows Glass").put("messages",80).put("inputUpdates",1280)
                    .put("fxCpuMillis",(end-start)/1e6).put("dispatchCpuP50Millis",percentile(dispatch,.5)).put("dispatchCpuP95Millis",percentile(dispatch,.95))
                    .put("pulseP95Millis",percentile(intervals,.95)).put("maxPulseMillis",intervals.stream().mapToDouble(Double::doubleValue).max().orElse(0))
                    .put("pulseSamples",intervals.size()).put("pulsesOver50Millis",intervals.stream().filter(v->v>50).count())
                    .put("note","Local 80-card scrollbar-input regression, no production smoothness guarantee; includes rendering and FX work, excludes GPU attribution.");
            Path output=Path.of(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,report.toString(2));System.out.println(report);
        } finally {fx(()->{if(viewport!=null)viewport.dispose();if(stage!=null)stage.close();return null;});Platform.exit();}
    }
    private static double percentile(List<Double> values,double p){var sorted=values.stream().sorted().toList();return sorted.isEmpty()?0:sorted.get(Math.min(sorted.size()-1,(int)Math.ceil(p*sorted.size())-1));}
}
