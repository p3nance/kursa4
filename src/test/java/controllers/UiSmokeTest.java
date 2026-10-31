package controllers;

import com.sun.net.httpserver.HttpServer;
import com.example.authapp.models.Product;
import com.example.authapp.models.Cart;
import config.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.json.*;
import org.junit.jupiter.api.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** All requests terminate at a loopback fixture; no real account or catalog is touched. */
@Tag("ui")
class UiSmokeTest {
    private static HttpServer api;
    private static final List<Throwable> failures=new CopyOnWriteArrayList<>();
    private static final AtomicBoolean failCatalog=new AtomicBoolean();
    private static final List<JSONObject> cartRows=new CopyOnWriteArrayList<>();
    private static final List<JSONObject> orderRows=new CopyOnWriteArrayList<>(),promoRows=new CopyOnWriteArrayList<>();
    private static volatile List<Product> catalogOverride;
    private static final List<MainController> hosts=new ArrayList<>();
    private static final List<Object> standalone=new ArrayList<>();
    private static final List<Product> CATALOG=List.of(
            new Product(1,"AMD Ryzen 5 7600","socket: AM5",18490,5,"","Процессоры","AMD"),
            new Product(2,"Palit GeForce RTX 4060 Dual","gpu_power: 115",32990,3,"","Видеокарты","Palit"),
            new Product(3,"MSI PRO B650M-P","socket: AM5 memory: DDR5",12990,4,"","Материнские платы","MSI"),
            new Product(4,"Kingston Fury Beast 16 ГБ","memory: DDR5",5490,10,"","Оперативная память","Kingston"),
            new Product(5,"Kingston NV2 1 ТБ","",5290,10,"","SSD-накопители","Kingston"),
            new Product(6,"DeepCool PK650D","psu_power: 650",5990,3,"","Блоки питания","DeepCool"),
            new Product(7,"Montech Air 100 Lite","",5490,3,"","Корпуса","Montech"),
            new Product(8,"DeepCool AG400","",2290,10,"","Охлаждение","DeepCool"));
    private record Shell(Parent root,MainController host){}

    @BeforeAll static void start() throws Exception {
        api=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        api.createContext("/",exchange->{
            String path=exchange.getRequestURI().getPath();
            String response;
            int status=200;
            if(path.startsWith("/auth/v1/token"))response="{\"access_token\":\"test-session\",\"refresh_token\":\"test-refresh\",\"expires_in\":3600,\"user\":{\"id\":\"11111111-1111-1111-1111-111111111111\",\"email\":\"test@example.invalid\"}}";
            else if(path.startsWith("/auth/v1/signup"))response="{\"id\":\"11111111-1111-1111-1111-111111111111\",\"email\":\"test@example.invalid\"}";
            else if(path.startsWith("/rest/v1/profiles"))response="[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"email\":\"test@example.invalid\",\"name\":\"Тест\",\"is_admin\":true,\"is_manager\":true,\"is_blocked\":false,\"blocked\":false}]";
            else if(path.startsWith("/rest/v1/products")){
                if(failCatalog.get()){status=503;response="{\"message\":\"local test failure\"}";}
                else {JSONArray array=new JSONArray();for(Product p:catalogOverride==null?CATALOG:catalogOverride)array.put(new JSONObject().put("id",p.getId()).put("name",p.getName()).put("description",p.getDescription()).put("price",p.getPrice()).put("stock",p.getStock()).put("image_url",p.getImageUrl()).put("category",p.getCategory()).put("manufacturer",p.getManufacturer()));response=array.toString();}
            } else if(path.startsWith("/rest/v1/cart_items")){
                String verb=exchange.getRequestMethod(),query=Objects.toString(exchange.getRequestURI().getRawQuery(),"");
                if(verb.equals("POST")){
                    JSONObject row=new JSONObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                    row.put("id",cartRows.size()+1);cartRows.add(row);status=201;response="{}";
                }else if(verb.equals("PATCH")){
                    int quantity=new JSONObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).getInt("quantity");
                    cartRows.stream().filter(row->query.equals("id=eq."+row.getInt("id"))).forEach(row->row.put("quantity",quantity));response="{}";
                }else if(verb.equals("DELETE")){
                    cartRows.removeIf(row->query.equals("id=eq."+row.getInt("id")));status=204;response="";
                }else response=new JSONArray(cartRows).toString();
            } else if(path.startsWith("/rest/v1/orders"))response=new JSONArray(orderRows).toString();
            else if(path.startsWith("/rest/v1/promo_codes"))response=new JSONArray(promoRows).toString();
            else response="[]";
            byte[] body=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();
        });api.start();
        System.setProperty("techstore.supabase.url","http://127.0.0.1:"+api.getAddress().getPort());
        System.setProperty("techstore.supabase.key","sb_publishable_test");
        System.setProperty("techstore.knowledge.dir","build/ui-empty-knowledge");
        assertTrue(SessionManager.login("test@example.invalid","test-password"));
        CountDownLatch ready=new CountDownLatch(1);
        Platform.startup(()->{Platform.setImplicitExit(false);Thread.currentThread().setUncaughtExceptionHandler((thread,error)->failures.add(error));ready.countDown();});
        assertTrue(ready.await(15,TimeUnit.SECONDS));
    }
    @BeforeEach void prepare(){catalogOverride=null;failCatalog.set(false);cartRows.clear();orderRows.clear();promoRows.clear();failures.clear();assertTrue(SessionManager.login("test@example.invalid","test-password"));}
    @AfterEach void cleanup() throws Exception {
        onFx(()->{hosts.forEach(MainController::dispose);hosts.clear();standalone.forEach(UiSmokeTest::dispose);standalone.clear();for(var window:List.copyOf(javafx.stage.Window.getWindows()))window.hide();return null;});
        assertTrue(failures.isEmpty(),failures.toString());
    }
    @AfterAll static void stop(){if(api!=null)api.stop(0);Platform.exit();}

    private Shell shell() throws Exception {
        Shell result=onFx(()->{FXMLLoader loader=new FXMLLoader(getClass().getResource("/views/main.fxml"));Parent root=loader.load();MainController host=loader.getController();hosts.add(host);Stage stage=new Stage();stage.setScene(new Scene(root,1440,900));stage.show();root.applyCss();root.layout();return new Shell(root,host);});
        waitUntil(()->result.host().isCatalogLoaded());return result;
    }
    @Test void shellKeepsNavigationAndStateAcrossChatAndBuild() throws Exception {
        Shell app=shell();
        Parent[] chatNode=new Parent[1];Node[] messageBox=new Node[1];
        onFx(()->{
            assertEquals(8,app.root().lookupAll(".product-card").size());
            app.root().applyCss();app.root().layout();
            assertEquals(lookup(app.root(),"#product-1").getLayoutY(),lookup(app.root(),"#product-3").getLayoutY(),1,"Three product cards should fit a desktop row");
            Node sidebar=lookup(app.root(),"#categoryPane");Node header=lookup(app.root(),"#headerPane");
            app.host().openChat();chatNode[0]=(Parent)((BorderPane)lookup(app.root(),"#mainPane")).getCenter();messageBox[0]=lookup(app.root(),"#messages");
            TextArea input=(TextArea)lookup(app.root(),"#input");input.setText("Что такое сокет?");((Button)lookup(app.root(),"#sendButton")).fire();
            assertTrue(sidebar.isVisible()&&header.isVisible());return null;
        });
        waitUntil(()->text(chatNode[0]).contains("Разъём CPU"));
        onFx(()->{
            app.host().openSuggestedBuild(CATALOG,new BigDecimal("100000"));
            ConfiguratorController config=(ConfiguratorController)((BorderPane)lookup(app.root(),"#mainPane")).getCenter().getUserData();
            assertEquals(8,config.getSelectedProducts().size());assertEquals(new BigDecimal("100000"),config.getBudget());
            app.host().showMainContent();app.host().openConfigurator();
            assertEquals(8,config.getSelectedProducts().size());
            app.host().openChat();assertSame(messageBox[0],lookup(app.root(),"#messages"));
            assertTrue(text(chatNode[0]).contains("Разъём CPU"));return null;
        });
    }
    @Test void benchmarkComparisonShowsContextSourcesDateMeaningAndPercentage(@org.junit.jupiter.api.io.TempDir Path knowledge) throws Exception {
        String context="""
                {"suite":"SYNTHETIC_FIXTURE","version":"1","workload":"fixture-render","metric":"score",
                "unit":"points","direction":"HIGHER_IS_BETTER",
                "conditions":{"reviewCohort":"synthetic-batch-1","os":"fixture-os","driver":"fixture-driver"}}
                """;
        Files.writeString(knowledge.resolve("benchmarks.json"),"""
                {"version":1,"results":[
                {"modelId":"SYNTHETIC_CPU_A","context":%s,"value":120,"source":"https://example.test/synthetic-left",
                "measuredAt":"2026-01-01","publishedAt":"2026-01-03"},
                {"modelId":"SYNTHETIC_CPU_B","context":%s,"value":100,"source":"https://example.test/synthetic-right",
                "measuredAt":null,"publishedAt":"2026-01-04"}]}
                """.formatted(context,context));
        Files.writeString(knowledge.resolve("component-specs.json"),"""
                {"version":1,"products":[
                {"productId":1001,"catalogName":"Тест CPU A","catalogCategory":"CPU","facts":{
                "model_id":{"value":"SYNTHETIC_CPU_A","source":"https://example.test/synthetic-model-a","verification":"VERIFIED"}}},
                {"productId":1002,"catalogName":"Тест CPU B","catalogCategory":"CPU","facts":{
                "model_id":{"value":"SYNTHETIC_CPU_B","source":"https://example.test/synthetic-model-b","verification":"VERIFIED"}}}]}
                """);
        List<Product> fixtures=List.of(new Product(1001,"Тест CPU A","",1000,1,"","Процессоры","Fixture"),
                new Product(1002,"Тест CPU B","",1000,1,"","Процессоры","Fixture"));
        Parent chat=localChat(fixtures,knowledge);
        onFx(()->{submitChat(chat,"Сравни Тест CPU A и Тест CPU B");return null;});
        waitUntil(()->text(lookup(chat,"#messages")).contains("Разница значений: 20.00 %"));
        onFx(()->{
            String displayed=text(lookup(chat,"#messages"));
            assertTrue(displayed.contains("(левое − правое) / правое"));
            assertTrue(displayed.contains("Лучший результат в этом тесте: Тест CPU A"));
            assertTrue(displayed.contains("Он не определяет производительность в других задачах"));
            assertTrue(displayed.contains("измерено 2026-01-01; опубликовано 2026-01-03"));
            assertTrue(displayed.contains("опубликовано 2026-01-04; точная дата измерения не указана"));
            var panes=detailsPanes(chat,"Условия и источники");assertEquals(1,panes.size());
            assertFalse(panes.getFirst().isExpanded());
            assertFalse(visibleText(chat).contains("fixture-driver"));
            panes.getFirst().setExpanded(true);chat.applyCss();chat.layout();
            assertTrue(visibleText(chat).contains("fixture-driver"));
            assertEquals(2,nodes(chat,Hyperlink.class).stream().filter(n->n.getStyleClass().contains("chat-source")).count());
            assertFalse(displayed.contains("null"));
            render(chat,"benchmark-comparison",1440,900);render(chat,"benchmark-comparison-900",900,600);return null;
        });
    }
    @Test void naturalComparisonWrappersAndRepeatedScenarioChangesSurviveTheUiWorker() throws Exception {
        Parent chat=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        Node initial=chatReplyAfter(chat,()->submitChat(chat,"Сравни видеокарты 4070 SUPER и 4070 по бенчмаркам"));
        onFx(()->{assertEquals(4,initial.lookupAll(".chat-benchmark-card").size());return null;});
        Node at4k=chatReplyAfter(chat,()->submitChat(chat,"Для игр в Starfield 4K"));
        onFx(()->{assertEquals(1,at4k.lookupAll(".chat-benchmark-card").size());assertTrue(text(at4k).contains("52.9"));assertFalse(text(at4k).contains("86.4"));return null;});
        Node at1440=chatReplyAfter(chat,()->submitChat(chat,"а на 1440p?"));
        onFx(()->{
            assertEquals(1,at1440.lookupAll(".chat-benchmark-card").size());
            assertTrue(text(at1440).contains("86.4"));assertTrue(text(at1440).contains("78.7"));assertTrue(text(at1440).contains("9.78 %"));
            assertFalse(text(at1440).contains("52.9"));assertFalse(text(at1440).contains("13.76 %"));return null;
        });
        Node unavailable=chatReplyAfter(chat,()->submitChat(chat,"а в Cyberpunk 2077?"));
        onFx(()->{
            assertTrue(unavailable.lookupAll(".chat-benchmark-card").isEmpty());assertFalse(text(unavailable).contains("9.78 %"));
            assertTrue(text(unavailable).contains("NVIDIA GeForce RTX 4070 SUPER 12GB"));
            assertTrue(text(unavailable).contains("NVIDIA GeForce RTX 4070 12GB"));
            assertTrue(text(unavailable).contains("нет подтверждённого общего игрового результата"));return null;
        });
        Node restored=chatReplyAfter(chat,()->{
            Button supported=findButton(unavailable,"Для игр в Starfield 1440p");assertNotNull(supported);supported.fire();
        });
        onFx(()->{
            assertEquals(1,restored.lookupAll(".chat-benchmark-card").size());assertTrue(text(restored).contains("86.4"));assertFalse(text(restored).contains("52.9"));
            render(chat,"r24-repeated-scenarios",1440,900);render(chat,"r24-repeated-scenarios-900",900,600);return null;
        });
    }

    private static Node chatReplyAfter(Parent chat,Runnable action) throws Exception {
        Node previous=onFx(()->((VBox)lookup(chat,"#messages")).getChildren().getLast());
        onFx(()->{action.run();return null;});
        waitUntil(()->{
            Node last=((VBox)lookup(chat,"#messages")).getChildren().getLast();
            return last!=previous && last.getStyleClass().contains("chat-bot-row") && !((Button)lookup(chat,"#sendButton")).isDisabled();
        });
        return onFx(()->((VBox)lookup(chat,"#messages")).getChildren().getLast());
    }

    @Test void coolerComparisonKeepsRadiatorClassSeparateFromAirCoolerHeight() throws Exception {
        Parent chat=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        Node reply=chatReplyAfter(chat,()->submitChat(chat,"Сравни Noctua NH-D15 и Corsair iCUE H150i Elite Capellix"));
        onFx(()->{
            assertTrue(reply.lookupAll(".chat-benchmark-card").isEmpty());assertEquals(1,reply.lookupAll(".chat-specifications-card").size());
            GridPane table=nodes(reply,GridPane.class).stream().filter(n->n.getStyleClass().contains("chat-table")).findFirst().orElseThrow();
            java.util.function.Function<String,List<String>> rowValues=title->{
                Label label=nodes(table,Label.class).stream().filter(l->title.equals(l.getText())).findFirst().orElseThrow();
                int row=GridPane.getRowIndex(label);
                return table.getChildren().stream().filter(n->Objects.equals(GridPane.getRowIndex(n),row))
                        .sorted(Comparator.comparingInt(n->GridPane.getColumnIndex(n)))
                        .map(n->((Label)n).getText()).toList();
            };
            assertEquals(List.of("Тип охлаждения","Воздушное","Жидкостное (AIO)"),rowValues.apply("Тип охлаждения"));
            assertEquals(List.of("Номинальный класс радиатора","Не применяется","360 мм"),rowValues.apply("Номинальный класс радиатора"));
            assertEquals(List.of("Высота воздушного кулера","Не подтверждено","Не применяется к СЖО"),rowValues.apply("Высота воздушного кулера"));
            ScrollPane conversation=(ScrollPane)lookup(chat,"#conversation");
            for(int[] size:List.of(new int[]{1440,900},new int[]{900,600})) {
                chat.resize(size[0],size[1]);chat.applyCss();chat.layout();conversation.setVvalue(0);conversation.layout();
                assertTrue(conversation.getContent().getLayoutBounds().getWidth()<=conversation.getViewportBounds().getWidth()+1);
                render(chat,size[0]==1440?"r26-cooler-comparison":"r26-cooler-comparison-900",size[0],size[1]);
            }
            return null;
        });
    }

    @Test void inlineGameScenarioNeverShowsAnotherGamesFpsAsItsResult() throws Exception {
        Parent chat=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        onFx(()->{submitChat(chat,"Сравни 4070 super и 4070 для игр в Cyberpunk 1440p");return null;});
        waitUntil(()->!((Button)lookup(chat,"#sendButton")).isDisabled() && text(lookup(chat,"#messages")).contains("указанного сценария"));
        onFx(()->{
            String displayed=text(lookup(chat,"#messages"));assertTrue(chat.lookupAll(".chat-benchmark-card").isEmpty());
            assertTrue(displayed.contains("NVIDIA GeForce RTX 4070 SUPER 12GB"));assertTrue(displayed.contains("NVIDIA GeForce RTX 4070 12GB"));
            assertTrue(displayed.contains("Starfield"));assertFalse(displayed.contains("13.76 %"));assertFalse(displayed.contains("52.9"));
            render(chat,"r22-inline-unsupported",1440,900);render(chat,"r22-inline-unsupported-900",900,600);
            Button available=findButton(chat,"Для игр в Starfield 4K");assertNotNull(available,"Offer a supported scenario instead of making the user guess its syntax");available.fire();return null;
        });
        waitUntil(()->!((Button)lookup(chat,"#sendButton")).isDisabled() && chat.lookupAll(".chat-benchmark-card").size()==1);
        onFx(()->{String displayed=text(lookup(chat,"#messages"));assertTrue(displayed.contains("13.76 %"));assertTrue(displayed.contains("52.9"));assertTrue(displayed.contains("46.5"));render(chat,"r22-inline-supported",1440,900);return null;});
    }
    @Test void starfield1440pUsesItsOwnFpsAndKeepsConditionsReadableAtBothWindowSizes() throws Exception {
        Parent chat=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        onFx(()->{submitChat(chat,"Сравни 4070 super и 4070 для игр в Starfield 1440p");return null;});
        waitUntil(()->!((Button)lookup(chat,"#sendButton")).isDisabled() && chat.lookupAll(".chat-benchmark-card").size()==1);
        onFx(()->{
            String displayed=text(lookup(chat,"#messages"));
            for(String value:List.of("Starfield","1440p","86.4","78.7","9.78 %","средняя частота кадров",
                    "опубликовано 2024-03-07; точная дата измерения не указана","ASUS RTX 4060 Dual", "XFX QICK 319 RX 7700 XT Black"))
                assertTrue(displayed.contains(value),"1440p comparison is missing "+value);
            assertFalse(displayed.contains("13.76 %"));assertFalse(displayed.contains("52.9"));
            assertFalse(displayed.contains("Not reported"));assertFalse(displayed.contains("GPU gaming benchmark"));
            var details=detailsPanes(chat,"Условия и источники");assertEquals(1,details.size());
            ScrollPane conversation=(ScrollPane)lookup(chat,"#conversation");
            for(int[] size:List.of(new int[]{1440,900},new int[]{900,600})) {
                details.getFirst().setExpanded(false);chat.resize(size[0],size[1]);chat.applyCss();chat.layout();conversation.setVvalue(0);conversation.layout();
                double width=conversation.getViewportBounds().getWidth();assertTrue(width>0);
                assertTrue(conversation.getContent().getLayoutBounds().getWidth()<=width+1);
                render(chat,size[0]==1440?"r23-starfield-1440p":"r23-starfield-1440p-900",size[0],size[1]);
                details.getFirst().setExpanded(true);chat.applyCss();chat.layout();
                assertTrue(conversation.getContent().getLayoutBounds().getWidth()<=width+1);
                assertTrue(nodes(chat,Label.class).stream().filter(l->l.getStyleClass().contains("chat-condition"))
                        .allMatch(l->l.getHeight()+1>=l.prefHeight(l.getWidth())),"1440p source conditions must retain their full wrapped height");
                conversation.setVvalue(1);conversation.layout();
                render(chat,size[0]==1440?"r23-starfield-1440p-sources":"r23-starfield-1440p-sources-900",size[0],size[1]);
            }
            return null;
        });
        Parent at4k=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        onFx(()->{submitChat(at4k,"Сравни 4070 super и 4070 для игр в Starfield 4K");return null;});
        waitUntil(()->!((Button)lookup(at4k,"#sendButton")).isDisabled() && at4k.lookupAll(".chat-benchmark-card").size()==1);
        onFx(()->{
            String displayed=text(lookup(at4k,"#messages"));assertTrue(displayed.contains("13.76 %"));assertTrue(displayed.contains("52.9"));
            assertTrue(displayed.contains("46.5"));assertFalse(displayed.contains("9.78 %"));assertFalse(displayed.contains("86.4"));return null;
        });
    }

    @Test void newlyCoveredGpuPairOffersOnlyItsMeasured1440pScenario() throws Exception {
        Parent chat=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        onFx(()->{submitChat(chat,"Сравни RTX 4060 и RX 7600 для игр в Starfield 1080p");return null;});
        waitUntil(()->!((Button)lookup(chat,"#sendButton")).isDisabled() && text(lookup(chat,"#messages")).contains("указанного сценария"));
        onFx(()->{
            assertTrue(chat.lookupAll(".chat-benchmark-card").isEmpty());assertFalse(text(lookup(chat,"#messages")).contains("4.08 %"));
            assertNull(findButton(chat,"Для игр в Starfield 4K"));
            Button available=findButton(chat,"Для игр в Starfield 1440p");assertNotNull(available);available.fire();return null;
        });
        waitUntil(()->!((Button)lookup(chat,"#sendButton")).isDisabled() && chat.lookupAll(".chat-benchmark-card").size()==1);
        onFx(()->{
            String displayed=text(lookup(chat,"#messages"));assertTrue(displayed.contains("48.5"));assertTrue(displayed.contains("46.6"));assertTrue(displayed.contains("4.08 %"));return null;
        });
    }

    @Test void realGamingComparisonShowsAverageFpsAndHonestUnknownsAtBothWindowSizes() throws Exception {
        Parent chat=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        onFx(()->{submitChat(chat,"Сравни Ryzen 7 7800X3D и Ryzen 5 7600X для игр");return null;});
        waitUntil(()->chat.lookupAll(".chat-benchmark-card").size()==1 && !((Button)lookup(chat,"#sendButton")).isDisabled());
        onFx(()->{
            String displayed=text(lookup(chat,"#messages"));
            for(String value:List.of("F1 24","438.3","311.5","40.71 %","кадр/с","средняя частота кадров","GamersNexus","[10/24]","Не опубликовано","Высокие"))
                assertTrue(displayed.contains(value),"Gaming comparison is missing "+value);
            assertFalse(displayed.contains("Он не определяет игровой FPS"));
            assertFalse(displayed.contains("Тесты рендеринга не определяют выбор для игр"));
            assertTrue(displayed.contains("не прогноз для других игр"));
            var details=detailsPanes(chat,"Условия и источники");assertEquals(1,details.size());
            ScrollPane conversation=(ScrollPane)lookup(chat,"#conversation");
            for(int[] size:List.of(new int[]{1440,900},new int[]{900,600})) {
                details.getFirst().setExpanded(false);chat.resize(size[0],size[1]);chat.applyCss();chat.layout();conversation.setVvalue(0);conversation.layout();
                double width=conversation.getViewportBounds().getWidth();assertTrue(width>0);
                assertTrue(conversation.getContent().getLayoutBounds().getWidth()<=width+1);
                render(chat,size[0]==1440?"r19-gaming-fps":"r19-gaming-fps-900",size[0],size[1]);
                details.getFirst().setExpanded(true);chat.applyCss();chat.layout();
                assertTrue(conversation.getContent().getLayoutBounds().getWidth()<=width+1);
                assertTrue(nodes(chat,Label.class).stream().filter(l->l.getStyleClass().contains("chat-condition"))
                        .allMatch(l->l.getHeight()+1>=l.prefHeight(l.getWidth())),"Gaming condition must retain wrapped text");
                conversation.setVvalue(1);conversation.layout();
                render(chat,size[0]==1440?"r19-gaming-sources":"r19-gaming-sources-900",size[0],size[1]);
            }
            return null;
        });
    }
    @Test void realBenchmarkComparisonWrapsLongPublishedConditionsAtBothWindowSizes() throws Exception {
        Parent chat=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        onFx(()->{submitChat(chat,"4070 super против 4070");return null;});
        waitUntil(()->text(lookup(chat,"#messages")).contains("Разница значений: 19.46 %"));
        onFx(()->{
            String displayed=text(lookup(chat,"#messages"));
            assertEquals(4,chat.lookupAll(".chat-benchmark-card").size());
            assertEquals(8,nodes(chat,Hyperlink.class).stream().filter(n->n.getStyleClass().contains("chat-source")).count());
            for(String value:List.of("19279","17271","3112","2605","52.9","46.5","86.4","78.7","11.63 %","19.46 %","13.76 %","9.78 %",
                    "опубликовано 2024-02-01; точная дата измерения не указана",
                    "NVIDIA GeForce RTX 4070 SUPER 12GB", "NVIDIA GeForce RTX 4070 12GB", "AMD Ryzen Threadripper 7970X"))
                assertTrue(displayed.contains(value),"Real comparison is missing "+value);
            assertTrue(displayed.contains("Cinebench 2024"));assertTrue(displayed.contains("V-Ray Benchmark 5.02"));
            assertTrue(displayed.contains("Starfield"));assertFalse(displayed.contains("Starfield Not reported"));
            assertTrue(displayed.contains("(1/24)"));assertTrue(displayed.contains("Nitro+ — образец лаборатории"));
            assertFalse(displayed.contains("Blender"));
            assertFalse(displayed.contains("sourceReview:"));assertFalse(displayed.contains("review_cohort:"));
            var panes=detailsPanes(chat,"Условия и источники");assertEquals(4,panes.size());
            assertTrue(panes.stream().noneMatch(TitledPane::isExpanded));
            assertFalse(visibleText(chat).contains("AMD Ryzen Threadripper 7970X"));
            ScrollPane conversation=(ScrollPane)lookup(chat,"#conversation");
            for(int[] size:List.of(new int[]{1440,900},new int[]{900,600})) {
                panes.forEach(p->p.setExpanded(false));
                chat.resize(size[0],size[1]);chat.applyCss();chat.layout();conversation.setVvalue(0);conversation.layout();
                double viewport=conversation.getViewportBounds().getWidth();
                assertTrue(viewport>0);
                assertTrue(conversation.getContent().getLayoutBounds().getWidth()<=viewport+1,
                        "Long source URLs must not widen the conversation beyond its viewport");
                render(chat,size[0]==1440?"real-benchmark-comparison":"real-benchmark-comparison-900",size[0],size[1]);
                panes.forEach(p->p.setExpanded(true));chat.applyCss();chat.layout();
                for(Node card:chat.lookupAll(".chat-benchmark-card")) {
                    assertTrue(card.getLayoutBounds().getWidth()<=viewport+1);
                    var conditions=nodes(card,Label.class).stream().filter(l->l.getStyleClass().contains("chat-condition")).toList();
                    assertTrue(conditions.size()>5);
                    for(Label condition:conditions) {
                        assertTrue(condition.isWrapText());
                        assertTrue(condition.prefHeight(condition.getWidth())<=condition.getHeight()+1,
                                "Published conditions must receive their full wrapped height");
                        assertTrue(condition.getWidth()<=card.getLayoutBounds().getWidth());
                    }
                }
                assertTrue(visibleText(chat).contains("AMD Ryzen Threadripper 7970X"));
                render(chat,size[0]==1440?"real-benchmark-details":"real-benchmark-details-900",size[0],size[1]);
            }
            return null;
        });
    }
    @Test void userAbbreviationsStillShowSpecificationsWithoutACommonContext() throws Exception {
        Parent chat=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        onFx(()->{submitChat(chat,"сравни i9-14900k и ryzen 5 7600x");return null;});
        waitUntil(()->!lookup(chat,"#messages").lookupAll(".chat-specifications-card").isEmpty());
        onFx(()->{
            String displayed=visibleText(lookup(chat,"#messages"));String lower=displayed.toLowerCase(Locale.ROOT);
            assertTrue(displayed.contains("Intel Core i9-14900K"));assertTrue(displayed.contains("AMD Ryzen 5 7600X"));
            assertTrue(lower.contains("нет") || lower.contains("отсутств"));
            assertTrue(lower.contains("измерен") || lower.contains("бенчмарк"));
            assertTrue(displayed.contains("LGA1700"));assertTrue(displayed.contains("AM5"));
            assertTrue(displayed.contains("Цена и наличие из каталога"));
            assertFalse(displayed.contains("%"));assertFalse(displayed.contains("intel-core-i9"));
            assertFalse(displayed.contains("Нужны название модели, результат"));
            assertFalse(chat.lookupAll(".chat-chip").isEmpty());
            assertTrue(detailsPanes(chat,"Источники и ограничения").stream().noneMatch(TitledPane::isExpanded));
            render(chat,"r9-specifications-fallback",1440,900);render(chat,"r9-specifications-fallback-900",900,600);return null;
        });
    }
    @Test void originalCpuQuestionShowsPublishedPlatformsAndKeepsScopeVisibleAboveCollapsedDetails() throws Exception {
        Parent chat=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        onFx(()->{submitChat(chat,"сравни i9-14900k и ryzen 7 7800x3d");return null;});
        waitUntil(()->!((Button)lookup(chat,"#sendButton")).isDisabled() && chat.lookupAll(".chat-benchmark-card").size()==1);
        onFx(()->{
            Node card=chat.lookup(".chat-benchmark-card");String displayed=text(lookup(chat,"#messages"));
            for(String value:List.of("Intel Core i9-14900K","AMD Ryzen 7 7800X3D","F1 24","384.5","438.3","-12.27 %",
                    "двух тестовых платформ","распределение памяти по CPU","не прогноз FPS готовой сборки",
                    "опубликовано 2024-10-29; точная дата измерения не указана"))
                assertTrue(displayed.contains(value),"Published CPU comparison is missing "+value);
            assertFalse(displayed.contains("389.3"));assertFalse(displayed.contains("PUBLISHED_PLATFORM_COMPARISON"));
            Label scope=(Label)card.lookup(".chat-benchmark-scope");assertNotNull(scope);
            for(Node parent=scope.getParent();parent!=null && parent!=card;parent=parent.getParent())
                assertFalse(parent instanceof TitledPane,"The platform limitation must remain outside collapsed details");
            var details=detailsPanes(chat,"Условия и источники");assertEquals(1,details.size());
            ScrollPane conversation=(ScrollPane)lookup(chat,"#conversation");
            for(int[] size:List.of(new int[]{1440,900},new int[]{900,600})) {
                details.getFirst().setExpanded(false);chat.resize(size[0],size[1]);chat.applyCss();chat.layout();conversation.setVvalue(0);conversation.layout();
                assertTrue(visibleText(card).contains("распределение памяти по CPU"));
                assertTrue(visibleText(card).contains("Игровой тест процессора · 1080p"));
                assertTrue(scope.getHeight()+1>=scope.prefHeight(scope.getWidth()),"The visible platform note must retain its wrapped height");
                double width=conversation.getViewportBounds().getWidth();assertTrue(width>0);
                assertTrue(conversation.getContent().getLayoutBounds().getWidth()<=width+1);
                render(chat,size[0]==1440?"r25-cpu-platforms":"r25-cpu-platforms-900",size[0],size[1]);
                details.getFirst().setExpanded(true);chat.applyCss();chat.layout();
                assertTrue(nodes(card,Label.class).stream().filter(l->l.getStyleClass().contains("chat-condition"))
                        .allMatch(l->l.getHeight()+1>=l.prefHeight(l.getWidth())),"CPU platform source conditions must not clip");
                assertEquals(2,nodes(card,Hyperlink.class).stream().filter(n->n.getStyleClass().contains("chat-source")).count());
                conversation.setVvalue(1);conversation.layout();
                render(chat,size[0]==1440?"r25-cpu-platform-sources":"r25-cpu-platform-sources-900",size[0],size[1]);
            }
            findButton(chat,"Сравнить характеристики").fire();return null;
        });
        waitUntil(()->!((Button)lookup(chat,"#sendButton")).isDisabled() && chat.lookupAll(".chat-specifications-card").size()==1);
        onFx(()->{
            VBox messages=(VBox)lookup(chat,"#messages");Node last=messages.getChildren().getLast();
            assertTrue(text(last).contains("LGA1700"));assertTrue(text(last).contains("AM5"));assertTrue(last.lookupAll(".chat-benchmark-card").isEmpty());
            submitChat(chat,"Сравни 14900K и 7800X3D для работы");return null;
        });
        waitUntil(()->!((Button)lookup(chat,"#sendButton")).isDisabled() && chat.lookupAll(".chat-specifications-card").size()==2);
        onFx(()->{
            Node last=((VBox)lookup(chat,"#messages")).getChildren().getLast();assertTrue(last.lookupAll(".chat-benchmark-card").isEmpty());
            assertTrue(text(last).contains("нет общих проверенных измерений рабочих задач"));assertFalse(text(last).contains("-12.27 %"));return null;
        });
    }
    @Test void shortCpuPairShowsHumanNamesAndCollapsedMethodology() throws Exception {
        Parent chat=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        onFx(()->{submitChat(chat,"14700K vs 14600K");return null;});
        waitUntil(()->lookup(chat,"#messages").lookupAll(".chat-benchmark-card").size()==2);
        onFx(()->{
            String displayed=visibleText(lookup(chat,"#messages"));
            assertTrue(displayed.contains("Intel Core i7-14700K"));assertTrue(displayed.contains("Intel Core i5-14600K"));
            assertTrue(displayed.contains("23.62 %"));assertTrue(displayed.contains("5.92 %"));
            assertFalse(displayed.contains("intel-core-i7"));assertFalse(displayed.contains("review_cohort"));
            assertTrue(detailsPanes(chat,"Условия и источники").stream().noneMatch(TitledPane::isExpanded));
            render(chat,"r9-short-cpu-comparison",1440,900);render(chat,"r9-short-cpu-comparison-900",900,600);return null;
        });
    }
    @Test void cancelledComparisonCannotRestoreContextAfterANewDialog() throws Exception {
        Parent chat=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        onFx(()->{
            submitChat(chat,"14700K vs 14600K");
            ((Button)lookup(chat,"#cancelButton")).fire();
            Button reset=findButton(chat,"Новый диалог");assertNotNull(reset);reset.fire();
            submitChat(chat,"сравни со вторым");return null;
        });
        waitUntil(()->!((Button)lookup(chat,"#sendButton")).isDisabled());
        onFx(()->{
            assertTrue(lookup(chat,"#messages").lookupAll(".chat-benchmark-card").isEmpty());
            assertTrue(lookup(chat,"#messages").lookupAll(".chat-specifications-card").isEmpty());
            assertFalse(visibleText(lookup(chat,"#messages")).contains("Intel Core i7-14700K"));return null;
        });
    }
    private Parent localChat(List<Product> products,Path knowledge) throws Exception {
        String previous=System.getProperty("techstore.knowledge.dir");
        try {
            System.setProperty("techstore.knowledge.dir",knowledge.toString());
            return onFx(()->{
                FXMLLoader loader=new FXMLLoader(getClass().getResource("/views/chat.fxml"));Parent root=loader.load();
                ChatController controller=loader.getController();standalone.add(controller);controller.configure(null,products);
                Stage stage=new Stage();stage.setScene(new Scene(root,1440,900));stage.show();return root;
            });
        } finally {
            if(previous==null)System.clearProperty("techstore.knowledge.dir");else System.setProperty("techstore.knowledge.dir",previous);
        }
    }
    private static void submitChat(Parent chat,String message){((TextArea)lookup(chat,"#input")).setText(message);((Button)lookup(chat,"#sendButton")).fire();}
    @Test void chatColumnsAndWrappedControlsStayAlignedWhenTheWindowActuallyResizes() throws Exception {
        Parent chat=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        onFx(()->{submitChat(chat,"Как сравнить комплектующие по бенчмаркам?");return null;});
        waitUntil(()->!((Button)lookup(chat,"#sendButton")).isDisabled());
        List<String> problems=new ArrayList<>();JSONArray geometry=new JSONArray();
        for(int[] size:List.of(new int[]{1440,900},new int[]{1200,800},new int[]{900,600},new int[]{1440,900})) {
            resizeWindow(chat,size[0],size[1]);
            onFx(()->{
                ScrollPane scroll=(ScrollPane)lookup(chat,"#conversation");scroll.setVvalue(0);chat.applyCss();chat.layout();
                Node messages=lookup(chat,"#messages"),compose=chat.lookup(".chat-compose-box");
                var a=messages.localToScene(messages.getLayoutBounds());var b=compose.localToScene(compose.getLayoutBounds());
                geometry.put(new JSONObject().put("width",size[0]).put("height",size[1]).put("messagesX",a.getMinX()).put("composeX",b.getMinX()).put("messagesWidth",a.getWidth()).put("composeWidth",b.getWidth()).put("outputScaleX",chat.getScene().getWindow().getOutputScaleX()).put("outputScaleY",chat.getScene().getWindow().getOutputScaleY()));
                if(Math.abs(a.getMinX()-b.getMinX())>12 || Math.abs(a.getMaxX()-b.getMaxX())>12)
                    problems.add(size[0]+": history and composer use different columns: "+a+" / "+b);
                for(Button chip:nodes(messages,Button.class))if(chip.getStyleClass().contains("chat-chip")) {
                    var bounds=chip.localToScene(chip.getLayoutBounds());
                    if(bounds.getMaxX()>a.getMaxX()+1)problems.add(size[0]+": suggestion overflows message column: "+chip.getText());
                    if(chip.prefHeight(chip.getWidth())>chip.getHeight()+2)problems.add(size[0]+": suggestion text is clipped: "+chip.getText());
                }
                render(chat,"r10-layout-"+size[0],size[0],size[1]);return null;
            });
        }
        onFx(()->{Files.createDirectories(Path.of("build/reports/ui"));Files.writeString(Path.of("build/reports/ui/r10-layout-geometry.json"),geometry.toString(2));return null;});
        assertTrue(problems.isEmpty(),String.join("\n",problems));
    }
    private static void resizeWindow(Parent root,int width,int height) throws Exception {
        onFx(()->{
            Stage stage=(Stage)root.getScene().getWindow();
            double frameW=stage.getWidth()-root.getScene().getWidth(),frameH=stage.getHeight()-root.getScene().getHeight();
            stage.setWidth(width+Math.max(0,frameW));stage.setHeight(height+Math.max(0,frameH));return null;
        });
        waitUntil(()->Math.abs(root.getLayoutBounds().getWidth()-width)<2 && Math.abs(root.getLayoutBounds().getHeight()-height)<2);
        onFx(()->{root.applyCss();root.layout();return null;});
    }
    @Test void chatComparisonTablesDetailsAndLongChoicesReflowWithoutClipping() throws Exception {
        Parent chat=localChat(localCatalogSnapshot(),Path.of("src/main/resources/knowledge").toAbsolutePath());
        onFx(()->{submitChat(chat,"Сравни ASUS ROG Strix Z790-E Gaming WiFi и ASUS ROG Strix X670E-F Gaming WiFi");return null;});
        waitUntil(()->!chat.lookupAll(".chat-specifications-card").isEmpty());
        for(int[] size:List.of(new int[]{1440,900},new int[]{1200,800},new int[]{900,600},new int[]{720,534},new int[]{1440,900})) {
            resizeWindow(chat,size[0],size[1]);
            onFx(()->{
                chat.applyCss();chat.layout();ScrollPane scroll=(ScrollPane)lookup(chat,"#conversation");scroll.setVvalue(0);
                assertChatFrame(chat);
                GridPane table=(GridPane)chat.lookup(".chat-table");assertNotNull(table);
                for(Node cell:table.getChildren()) {
                    assertTrue(cell.getBoundsInParent().getMaxX()<=table.getWidth()+2,"Comparison cell overflows its column grid");
                    assertTrue(cell.getBoundsInParent().getMinX()>=-1);
                    if(cell instanceof Label label)assertTrue(label.prefHeight(label.getWidth())<=label.getHeight()+2,"Wrapped table text is clipped: "+label.getText());
                    for(Node peer:table.getChildren())if(!Objects.equals(GridPane.getColumnIndex(cell),GridPane.getColumnIndex(peer)) && Objects.equals(GridPane.getRowIndex(cell),GridPane.getRowIndex(peer)))
                        assertEquals(cell.getBoundsInParent().getMinY(),peer.getBoundsInParent().getMinY(),1.5,"Wrapped cells should align to the top of their row");
                }
                render(chat,"r10-table-"+size[0],size[0],size[1]);
                double range=Math.max(1,scroll.getContent().getLayoutBounds().getHeight()-scroll.getViewportBounds().getHeight());
                double tableY=table.localToScene(table.getLayoutBounds()).getMinY()-scroll.getContent().localToScene(scroll.getContent().getLayoutBounds()).getMinY();
                scroll.setVvalue(Math.max(0,Math.min(1,tableY/range)));
                render(chat,"r10-table-focus-"+size[0],size[0],size[1]);return null;
            });
        }
        TitledPane details=onFx(()->detailsPanes(chat,"Источники и ограничения").getFirst());
        double collapsed=onFx(()->details.getHeight());
        onFx(()->{details.setExpanded(true);return null;});
        waitUntil(()->details.getHeight()>collapsed+80);
        onFx(()->{
            for(Label line:nodes(details.getContent(),Label.class))assertTrue(line.prefHeight(line.getWidth())<=line.getHeight()+2,"Expanded detail text is clipped");
            render(chat,"r10-details-expanded",1440,900);details.setExpanded(false);return null;
        });
        waitUntil(()->Math.abs(details.getHeight()-collapsed)<2);
        onFx(()->{submitChat(chat,"Как сравнить комплектующие по бенчмаркам?");return null;});
        waitUntil(()->!((Button)lookup(chat,"#sendButton")).isDisabled());
        resizeWindow(chat,720,534);
        onFx(()->{
            VBox messages=(VBox)lookup(chat,"#messages");Node last=messages.getChildren().getLast();
            for(Button chip:nodes(last,Button.class))if(chip.getStyleClass().contains("chat-chip")) {
                assertTrue(chip.getBoundsInParent().getMaxX()<=chip.getParent().getLayoutBounds().getWidth()+1,"Long query chip extends past its flow row");
                assertTrue(chip.prefHeight(chip.getWidth())<=chip.getHeight()+2,"Long query chip loses text");
                assertTrue(chip.getHeight()>=31);
            }
            render(chat,"r10-long-choices-720",720,534);return null;
        });
    }
    @Test void chatReadingAnchorSurvivesAppendingMessagesAndReflowAboveIt() throws Exception {
        javafx.scene.layout.VBox[] messages={null};ScrollPane[] scroll={null};com.example.authapp.ui.ChatViewport[] viewport={null};
        AtomicBoolean unread=new AtomicBoolean();
        Parent root=onFx(()->{
            messages[0]=new VBox(10);messages[0].setFillWidth(true);
            for(int i=0;i<35;i++){Label line=new Label("Строка чтения "+i+" — сохранение позиции при изменении истории. ".repeat(3));line.setWrapText(true);line.setMinWidth(0);line.setMaxWidth(Double.MAX_VALUE);messages[0].getChildren().add(line);}
            scroll[0]=new ScrollPane(messages[0]);scroll[0].setFitToWidth(true);BorderPane pane=new BorderPane(scroll[0]);
            viewport[0]=new com.example.authapp.ui.ChatViewport(pane,scroll[0],messages[0],unread::set);
            Stage stage=new Stage();stage.setScene(new Scene(pane,700,500));stage.show();return pane;
        });
        try {
            onFx(()->{root.applyCss();root.layout();scroll[0].setVvalue(0.4);return null;});
            onFx(()->null);
            Node anchor=onFx(()->messages[0].getChildren().stream().filter(n->n.localToScene(n.getLayoutBounds()).getMinY()>2).findFirst().orElseThrow());
            double y=onFx(()->anchor.localToScene(anchor.getLayoutBounds()).getMinY());
            onFx(()->{Label extra=new Label("Новое длинное сообщение. ".repeat(100));extra.setWrapText(true);extra.setMaxWidth(Double.MAX_VALUE);viewport[0].addMessage(extra);return null;});
            onFx(()->null);
            onFx(()->{assertEquals(y,anchor.localToScene(anchor.getLayoutBounds()).getMinY(),2,"A new reply must not move the text currently being read");assertTrue(unread.get());return null;});
            onFx(()->{((Label)messages[0].getChildren().getFirst()).setText("Изменившаяся строка выше позиции чтения. ".repeat(100));root.applyCss();root.layout();return null;});
            onFx(()->null);onFx(()->null);
            onFx(()->{assertEquals(y,anchor.localToScene(anchor.getLayoutBounds()).getMinY(),2,"Reflow above the reading position must keep its anchor");viewport[0].scrollToBottom();return null;});
            waitUntil(()->scroll[0].getVvalue()>=0.99);
            assertFalse(unread.get());
        } finally {onFx(()->{viewport[0].dispose();return null;});}
    }
    @Test void longModelChoiceButtonsWrapTheirWholeLabelInsideTheConversation() throws Exception {
        String variant="ТЕСТОВЫЙ артикул с длинным названием исполнения и комплектации ".repeat(4);
        Parent chat=localChat(List.of(
                new Product(10001,"NVIDIA GeForce RTX 4070 SUPER 12GB "+variant+" A","",100,1,"","Видеокарты","Тест"),
                new Product(10002,"NVIDIA GeForce RTX 4070 SUPER 12GB "+variant+" B","",100,1,"","Видеокарты","Тест"),
                new Product(10003,"NVIDIA GeForce RTX 4070 12GB","",100,1,"","Видеокарты","Тест")),Path.of("src/main/resources/knowledge").toAbsolutePath());
        resizeWindow(chat,720,534);
        onFx(()->{submitChat(chat,"4070 super против 4070");return null;});
        waitUntil(()->!((Button)lookup(chat,"#sendButton")).isDisabled());
        onFx(()->{chat.applyCss();chat.layout();return null;});
        waitUntil(()->nodes(lookup(chat,"#messages"),Button.class).stream().filter(b->b.getStyleClass().contains("chat-chip")).allMatch(b->b.getWidth()>100));
        onFx(()->{
            List<Button> choices=nodes(lookup(chat,"#messages"),Button.class).stream().filter(b->b.getStyleClass().contains("chat-chip")).toList();
            assertEquals(2,choices.size());
            render(chat,"r10-wrapped-model-options-720",720,534);
            JSONArray geometry=new JSONArray();
            for(Button choice:choices)geometry.put(new JSONObject().put("textLength",choice.getText().length()).put("width",choice.getWidth()).put("height",choice.getHeight()).put("prefHeight",choice.prefHeight(choice.getWidth())).put("flowWidth",choice.getParent().getLayoutBounds().getWidth()));
            Files.writeString(Path.of("build/reports/ui/r10-button-geometry.json"),geometry.toString(2));
            JSONArray parents=new JSONArray();
            for(Node node=choices.getLast();node!=null;node=node.getParent()) {
                var bounds=node.localToScene(node.getLayoutBounds());
                JSONObject entry=new JSONObject().put("type",node.getClass().getSimpleName()).put("height",bounds.getHeight()).put("top",bounds.getMinY()).put("bottom",bounds.getMaxY());
                if(node instanceof Region region) {
                    entry.put("prefHeight",region.prefHeight(region.getWidth())).put("minHeight",region.minHeight(region.getWidth()));
                    if(node==lookup(chat,"#messages") || node==choices.getLast().getParent() || node.getStyleClass().contains("chat-bot-row")
                            || node.getParent()!=null && node.getParent().getStyleClass().contains("chat-bot-row"))
                        assertTrue(region.getHeight()+2>=region.prefHeight(region.getWidth()),"Wrapped content has an under-sized parent: "+node.getClass().getSimpleName());
                }
                parents.put(entry);
            }
            Files.writeString(Path.of("build/reports/ui/r10-button-parents.json"),parents.toString(2));
            for(Button choice:choices) {
                assertTrue(choice.getHeight()>45,"This fixture must actually exercise multiline button wrapping");
                assertTrue(choice.prefHeight(choice.getWidth())<=choice.getHeight()+2,"A model choice loses part of its label");
                assertTrue(choice.getBoundsInParent().getMaxX()<=choice.getParent().getLayoutBounds().getWidth()+1);
            }
            return null;
        });
    }
    @Test void chatFrameStaysUsableInsideTheShellAcrossSizesAndNavigation() throws Exception {
        Shell app=shell();onFx(()->{app.host().openChat();submitChat(app.root(),"Собери ПК до 100000 рублей для игр");return null;});
        waitUntil(()->!app.root().lookupAll(".chat-build-card").isEmpty());
        for(int[] size:List.of(new int[]{1440,900},new int[]{1200,800},new int[]{900,600})) {
            resizeWindow(app.root(),size[0],size[1]);
            onFx(()->{Parent chat=(Parent)lookup(app.root(),"#chatRoot");assertChatFrame(chat);render(app.root(),"r10-shell-"+size[0],size[0],size[1]);return null;});
        }
        onFx(()->{ScrollPane scroll=(ScrollPane)lookup(app.root(),"#conversation");scroll.setVvalue(0.3);app.host().showMainContent();app.host().openChat();return null;});
        onFx(()->{assertFalse(app.root().lookupAll(".chat-build-card").isEmpty());assertChatFrame((Parent)lookup(app.root(),"#chatRoot"));findButton(app.root(),"Новый диалог").fire();return null;});
        onFx(()->{assertTrue(app.root().lookupAll(".chat-build-card").isEmpty());assertNotNull(lookup(app.root(),"#input"));return null;});
    }
    private static void assertChatFrame(Parent chat) {
        ScrollPane scroll=(ScrollPane)lookup(chat,"#conversation");Node compose=chat.lookup(".chat-compose-box");
        var history=scroll.localToScene(scroll.getLayoutBounds());var input=compose.localToScene(compose.getLayoutBounds());
        assertTrue(history.getHeight()>=100,"The header/composer leave no room for reading");
        assertTrue(history.getMaxY()<=input.getMinY()+1,"History is covered by the composer");
        var rootBounds=chat.localToScene(chat.getLayoutBounds());
        assertTrue(input.getMaxY()<rootBounds.getMaxY());
        if(chat.getLayoutBounds().getWidth()<1080)assertFalse(lookup(chat,"#contextPane").isManaged(),"The context panel compresses the conversation");
        assertTrue(scroll.getContent().getLayoutBounds().getWidth()<=scroll.getViewportBounds().getWidth()+1);
        for(Label header:nodes(chat.lookup(".chat-header"),Label.class))assertTrue(header.prefHeight(header.getWidth())<=header.getHeight()+2,"Header text is clipped");
    }
    private static List<Product> localCatalogSnapshot() throws java.io.IOException {
        var snapshot=new JSONObject(Files.readString(Path.of("src/test/resources/knowledge/catalog-reference.json"),StandardCharsets.UTF_8));
        List<Product> catalog=new ArrayList<>();
        for(Object item:snapshot.getJSONArray("products")) {
            var row=(JSONObject)item;
            catalog.add(new Product(row.getInt("id"),row.getString("name"),row.optString("description",""),
                    row.getDouble("price"),row.getInt("stock"),"",row.getString("category"),row.optString("manufacturer","")));
        }
        return List.copyOf(catalog);
    }
    @Test void catalogFiltersAndDetailsWorkInsideSameShell() throws Exception {
        Shell app=shell();
        onFx(()->{
            ((TextField)lookup(app.root(),"#priceMax")).setText("6000");
            assertEquals(5,app.root().lookupAll(".product-card").size());
            ((ComboBox<?>)lookup(app.root(),"#catalogSort")).getSelectionModel().select(1);
            ((TextField)lookup(app.root(),"#priceMin")).setText("9000");
            assertTrue(text(lookup(app.root(),"#catalogState")).contains("Проверьте"));
            ((Button)lookup(app.root(),"#resetFilters")).fire();assertEquals(8,app.root().lookupAll(".product-card").size());
            app.host().openProduct(CATALOG.getFirst());assertTrue(text(app.root()).contains("AMD Ryzen 5 7600"));
            render(app.root(),"product-shell",1440,900);render(app.root(),"product-shell-900",900,600);
            assertTrue(lookup(app.root(),"#categoryPane").isVisible());app.host().showMainContent();return null;
        });
    }
    @Test void typedRecommendationOpensBuildAndSurvivesManualChanges() throws Exception {
        Shell app=shell();
        onFx(()->{app.host().openChat();((TextArea)lookup(app.root(),"#input")).setText("Собери ПК до 100000 рублей для игр");((Button)lookup(app.root(),"#sendButton")).fire();return null;});
        waitUntil(()->text(lookup(app.root(),"#messages")).contains("ВАРИАНТ СБОРКИ"));
        onFx(()->{
            Button open=findButton(app.root(),"Открыть в конфигураторе");assertNotNull(open);open.fire();
            ConfiguratorController config=(ConfiguratorController)((BorderPane)lookup(app.root(),"#mainPane")).getCenter().getUserData();assertEquals(8,config.getSelectedProducts().size());
            assertTrue(text(lookup(app.root(),"#compatStatusText")).contains("Недостаточно")||text(lookup(app.root(),"#compatStatusText")).contains("данных"));
            config.selectSuggestedBuild(List.of(CATALOG.getFirst()));app.host().openChat();
            assertTrue(text(lookup(app.root(),"#messages")).contains("89"));findButton(app.root(),"Открыть в конфигураторе").fire();assertEquals(8,config.getSelectedProducts().size());return null;
        });
    }
    @Test void logoutClearsChatAndGuestCanUseDictionary() throws Exception {
        Shell app=shell();
        onFx(()->{app.host().openChat();TextArea input=(TextArea)lookup(app.root(),"#input");input.setText("private draft");app.host().onLogout();app.host().openChat();assertFalse(text(app.root()).contains("private draft"));assertTrue(lookup(app.root(),"#categoryPane").isVisible());app.host().openCart();assertEquals("Вход в аккаунт",((Label)lookup(app.root(),"#sectionName")).getText());render(app.root(),"auth-shell",1440,900);render(app.root(),"auth-shell-900",900,600);return null;});
    }
    @Test void catalogFailureShowsRetryAndRecovers() throws Exception {
        failCatalog.set(true);Shell app=shell();
        onFx(()->{assertTrue(text(lookup(app.root(),"#catalogState")).contains("недоступен"));app.host().openChat();assertNotNull(lookup(app.root(),"#input"));failCatalog.set(false);app.host().reloadProducts();return null;});
        waitUntil(()->app.host().getCatalogSnapshot().size()==8);
        onFx(()->{app.host().showMainContent();assertEquals(8,app.root().lookupAll(".product-card").size());return null;});
    }
    @Test void screensAndShellRenderAtSupportedSizes() throws Exception {
        Shell app=shell();
        onFx(()->{render(app.root(),"main",1440,900);app.host().openChat();return null;});
        onFx(()->{((TextArea)lookup(app.root(),"#input")).setText("Собери ПК до 100000 рублей для игр");((Button)lookup(app.root(),"#sendButton")).fire();return null;});
        waitUntil(()->text(lookup(app.root(),"#messages")).contains("ВАРИАНТ СБОРКИ"));
        onFx(()->{
            render(app.root(),"chat",1440,900);
            app.host().openSuggestedBuild(CATALOG,new BigDecimal("100000"));render(app.root(),"configurator",1440,900);
            for(int width:List.of(1200,900)){
                app.host().showMainContent();render(app.root(),"main-"+width,width,width==900?600:800);
                app.host().openChat();render(app.root(),"chat-"+width,width,width==900?600:800);
                app.host().openConfigurator();render(app.root(),"configurator-"+width,width,width==900?600:800);
            }
            return null;
        });
        for(String name:List.of("auth","cabinet","cart","admin","manager","product_card","product-detail")){
            Parent view=onFx(()->{FXMLLoader loader=new FXMLLoader(getClass().getResource("/views/"+name+".fxml"));Parent node=loader.load();standalone.add(loader.getController());return node;});
            if(name.equals("admin")||name.equals("manager"))waitUntil(()->lookup(view,"#productsTable") instanceof TableView<?> table && table.getItems().size()==8 && !table.getColumns().isEmpty() && !text(lookup(view,"#screenStatus")).contains("Обновляем"));
            if(name.equals("cabinet"))waitUntil(()->!text(lookup(view,"#ordersStatus")).contains("Загружаем"));
            if(name.equals("cart"))waitUntil(()->lookup(view,"#cartStatus")!=null && !lookup(view,"#cartStatus").isVisible());
            onFx(()->{render(view,name,1200,800);return null;});
        }
    }
    @Test void privateScreensUsePersistentShellAndRealLoadedTables() throws Exception {
        Shell app=shell();
        onFx(()->{app.host().openAdminPanel();return null;});
        waitUntil(()->lookup(app.root(),"#productsTable") instanceof TableView<?> table && table.getItems().size()==8 && !text(lookup(app.root(),"#screenStatus")).contains("Обновляем"));
        onFx(()->{assertTrue(lookup(app.root(),"#categoryPane").isVisible());render(app.root(),"admin-shell",1440,900);render(app.root(),"admin-shell-900",900,600);app.host().openManagerPanel();return null;});
        waitUntil(()->lookup(app.root(),"#productsTable") instanceof TableView<?> table && table.getItems().size()==8 && !text(lookup(app.root(),"#screenStatus")).contains("Обновляем"));
        onFx(()->{render(app.root(),"manager-shell",1440,900);render(app.root(),"manager-shell-900",900,600);app.host().openCabinetInMain();return null;});
        waitUntil(()->!text(lookup(app.root(),"#ordersStatus")).contains("Загружаем"));
        onFx(()->{render(app.root(),"cabinet-shell",1440,900);render(app.root(),"cabinet-shell-900",900,600);cartRows.add(cartRow(CATALOG.getFirst(),1));cartRows.add(cartRow(CATALOG.get(1),2));app.host().openCart();return null;});
        waitUntil(()->lookup(app.root(),"#cartStatus")!=null&&!lookup(app.root(),"#cartStatus").isVisible());
        onFx(()->{assertEquals(3,Cart.getInstance().getTotalQuantity());render(app.root(),"cart-shell",1440,900);assertEquals(220,lookup(app.root(),"#categoryPane").getLayoutBounds().getWidth(),1);render(app.root(),"cart-shell-900",900,600);return null;});
    }
    @Test void cartAddQuantityAndRemovalUseRealControllerActions() throws Exception {
        Shell app=shell();
        onFx(()->{app.host().addProductToCart(CATALOG.getFirst(),1);return null;});
        waitUntil(()->Cart.getInstance().getTotalQuantity()==1);
        onFx(()->{app.host().openCart();return null;});
        waitUntil(()->lookup(app.root(),"#cartStatus")!=null&&!lookup(app.root(),"#cartStatus").isVisible());
        onFx(()->{Spinner<?> quantity=(Spinner<?>)lookup(app.root(),"#cartItemsContainer").lookup(".spinner");assertNotNull(quantity);quantity.increment();return null;});
        waitUntil(()->Cart.getInstance().getTotalQuantity()==2&&!lookup(app.root(),"#cartItemsContainer").isDisabled());
        onFx(()->{assertEquals("36980₽",((Label)lookup(app.root(),"#totalLabel")).getText().replaceAll("[\\s\\u00a0\\u202f]",""));((Button)lookup(app.root(),"#cartItemsContainer").lookup(".cart-remove")).fire();return null;});
        waitUntil(()->text(lookup(app.root(),"#cartItemsContainer")).contains("Корзина пока пуста"));
        assertEquals(0,Cart.getInstance().getTotalQuantity());assertTrue(cartRows.isEmpty());
    }
    @Test void chatOwnedPartTransfersToConfiguratorAndNeverBecomesACartPurchase() throws Exception {
        var products=new ArrayList<>(CATALOG);var gpu=new Product(2,"NVIDIA GeForce RTX 4070 12GB","",new BigDecimal("50000.00"),0,"","Видеокарты","NVIDIA");products.set(1,gpu);catalogOverride=products;
        Shell app=shell();onFx(()->{app.host().openChat();submitChat(app.root(),"У меня уже есть RTX 4070, собери остальное до 80000 рублей для игр");return null;});
        waitUntil(()->app.root().lookupAll(".chat-build-card").size()==1&&!((Button)lookup(app.root(),"#sendButton")).isDisabled());
        onFx(()->{
            Node card=app.root().lookup(".chat-build-card");assertTrue(text(card).contains("Уже есть"));assertTrue(text(lookup(app.root(),"#ownedValue")).contains("RTX 4070"));
            assertEquals(com.example.authapp.ui.Ui.money(new BigDecimal("56030.00")),((Label)card.lookup(".chat-build-total")).getText());
            render(app.root(),"r17-owned-chat",1440,900);render(app.root(),"r17-owned-chat-900",900,600);
            findButton(card,"Открыть в конфигураторе").fire();
            ConfiguratorController config=(ConfiguratorController)((BorderPane)lookup(app.root(),"#mainPane")).getCenter().getUserData();
            assertEquals(Set.of(com.example.authapp.domain.ComponentCategory.GPU),config.getOwnedCategories());assertEquals(8,config.getSelectedProducts().size());
            assertEquals(new BigDecimal("80000"),config.getBudget());assertTrue(((CheckBox)lookup(app.root(),"#owned-GPU")).isSelected());
            render(app.root(),"r17-owned-transfer",1440,900);((Button)lookup(app.root(),"#addBuildToCart")).fire();return null;
        });
        waitUntil(()->cartRows.size()==7&&!((Button)lookup(app.root(),"#addBuildToCart")).isDisabled());assertTrue(cartRows.stream().noneMatch(row->row.getInt("product_id")==gpu.getId()));
        onFx(()->{app.host().onLogout();app.host().openChat();assertFalse(lookup(app.root(),"#ownedValue").isVisible());return null;});
    }
    @Test void ambiguousOwnedChoiceKeepsBudgetAndCanBeClearedFromTheRequestDialog() throws Exception {
        var products=new ArrayList<>(CATALOG);products.set(1,new Product(2,"ASUS GeForce RTX 4070 Dual 12GB","",new BigDecimal("50000.00"),0,"","Видеокарты","ASUS"));
        products.add(new Product(9002,"Palit GeForce RTX 4070 Dual 12GB","",new BigDecimal("51000.00"),0,"","Видеокарты","Palit"));catalogOverride=products;
        Shell app=shell();onFx(()->{app.host().openChat();submitChat(app.root(),"У меня есть RTX 4070, собери ПК до 100000 рублей для игр");return null;});
        waitUntil(()->!((Button)lookup(app.root(),"#sendButton")).isDisabled()&&text(lookup(app.root(),"#messages")).contains("несколько моделей"));
        onFx(()->{assertTrue(app.root().lookupAll(".chat-build-card").isEmpty());submitChat(app.root(),"2");return null;});
        waitUntil(()->app.root().lookupAll(".chat-build-card").size()==1&&!((Button)lookup(app.root(),"#sendButton")).isDisabled());
        onFx(()->{
            assertEquals(com.example.authapp.ui.Ui.money(new BigDecimal("100000")),((Label)lookup(app.root(),"#budgetValue")).getText());assertTrue(text(lookup(app.root(),"#ownedValue")).contains("Palit"));
            render(app.root(),"r17-owned-choice",1440,900);Platform.runLater(()->findButton(app.root(),"Изменить запрос").fire());return null;
        });waitUntil(()->capacityDialog()!=null);
        onFx(()->{DialogPane pane=capacityDialog();CheckBox clear=(CheckBox)lookup(pane,"#requestClearOwned");assertTrue(clear.isVisible());clear.setSelected(true);render(pane,"r17-owned-request-dialog",500,720);((Button)pane.lookupButton(pane.getButtonTypes().stream().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow())).fire();return null;});
        waitUntil(()->!((Button)lookup(app.root(),"#sendButton")).isDisabled()&&!lookup(app.root(),"#ownedValue").isVisible());
    }
    @Test void staleOwnedCardCannotApplyItsFlagToAReusedCatalogId() throws Exception {
        var products=new ArrayList<>(CATALOG);products.set(1,new Product(2,"NVIDIA GeForce RTX 4070 12GB","",50000,0,"","Видеокарты","NVIDIA"));catalogOverride=products;
        Shell app=shell();onFx(()->{app.host().openChat();submitChat(app.root(),"У меня есть RTX 4070, собери ПК до 100000 рублей для игр");return null;});
        waitUntil(()->app.root().lookupAll(".chat-build-card").size()==1&&!((Button)lookup(app.root(),"#sendButton")).isDisabled());
        var changed=new ArrayList<>(products);changed.set(1,new Product(2,"NVIDIA GeForce RTX 4090 24GB","",50000,5,"","Видеокарты","NVIDIA"));catalogOverride=changed;
        onFx(()->{app.host().reloadProducts();return null;});
        waitUntil(()->app.host().isCatalogLoaded()&&!lookup(app.root(),"#ownedValue").isVisible());
        onFx(()->{submitChat(app.root(),"Собери ПК до 100000 рублей для игр");return null;});
        waitUntil(()->!((Button)lookup(app.root(),"#sendButton")).isDisabled()&&text(lookup(app.root(),"#messages")).contains("Каталог изменился"));
        onFx(()->{
            assertEquals(1,app.root().lookupAll(".chat-build-card").size());findButton(app.root(),"Открыть в конфигураторе").fire();
            ConfiguratorController config=(ConfiguratorController)((BorderPane)lookup(app.root(),"#mainPane")).getCenter().getUserData();assertTrue(config.getOwnedCategories().isEmpty());assertTrue(config.getSelectedProducts().isEmpty());
            assertTrue(text(lookup(app.root(),"#cartStatusText")).contains("Карточка устарела"));app.root().applyCss();app.root().layout();assertTrue(lookup(app.root(),"#cartStatusText").localToScene(lookup(app.root(),"#cartStatusText").getBoundsInLocal()).getMaxY()<app.root().getLayoutBounds().getHeight(),"The rejected-card explanation must be visible without scrolling");render(app.root(),"r17-stale-owned-card",1200,800);return null;
        });assertTrue(cartRows.isEmpty());
    }
    @Test void aReusedProductIdCannotSilentlyChangeTheCartModelAtTheSamePrice() throws Exception {
        Shell app=shell();Product original=CATALOG.getFirst();
        for(boolean changedCategory:List.of(false,true)) {
            var fresh=new ArrayList<>(CATALOG);fresh.set(0,new Product(original.getId(),changedCategory?original.getName():"A different processor",original.getDescription(),original.getPrice(),original.getStock(),"",changedCategory?"Видеокарты":original.getCategory(),original.getManufacturer()));catalogOverride=fresh;
            onFx(()->{app.host().addProductToCart(original,1);assertEquals("Проверяем цену и наличие…",((Label)lookup(app.root(),"#toastLabel")).getText());return null;});
            // The skin Text may still show the previous operation until the next pulse.
            // Read the Label property so every fixture change waits for its own response.
            waitUntil(()->((Label)lookup(app.root(),"#toastLabel")).getText().contains("Данные товара изменились"));assertTrue(cartRows.isEmpty());
        }
        var normalized=new ArrayList<>(CATALOG);normalized.set(0,new Product(original.getId(),"  amd   ryzen 5 7600  ",original.getDescription(),original.getPrice(),original.getStock(),"",original.getCategory(),original.getManufacturer()));catalogOverride=normalized;
        onFx(()->{app.host().addProductToCart(original,1);return null;});
        try{waitUntil(()->cartRows.size()==1);}catch(AssertionError timeout){
            fail("Expected one cart row, actual="+cartRows.size()+", last UI message="+onFx(()->((Label)lookup(app.root(),"#toastLabel")).getText()),timeout);
        }
        assertEquals(original.getId(),cartRows.getFirst().getInt("product_id"));
    }
    @Test void aPeripheralCategoryChangeCannotSilentlyChangeTheCartProduct() throws Exception {
        Product original=new Product(99,"Test peripheral","",new BigDecimal("10.00"),2,"","Мониторы","");
        catalogOverride=List.of(original);Shell app=shell();
        catalogOverride=List.of(new Product(99,original.getName(),"",original.getPrice(),2,"","Клавиатуры",""));
        onFx(()->{app.host().addProductToCart(original,1);return null;});
        waitUntil(()->((Label)lookup(app.root(),"#toastLabel")).getText().contains("Данные товара изменились"));
        assertTrue(cartRows.isEmpty());
    }
    @Test void sharedProductEditorRetainsInvalidInputAndCancelDoesNotMutateSource() throws Exception {
        var source=new com.example.authapp.dto.ProductDTO(42,"Original","Description",new BigDecimal("12.34"),5,"https://example.invalid/image.png","Процессоры","Test");
        var result=new java.util.concurrent.atomic.AtomicReference<Optional<com.example.authapp.dto.ProductDTO>>();
        onFx(()->{Platform.runLater(()->result.set(com.example.authapp.ui.ProductEditDialog.show(null,source)));return null;});waitUntil(()->productDialog()!=null);
        onFx(()->{
            DialogPane pane=productDialog();((TextField)lookup(pane,"#productName")).setText("Edited");((TextField)lookup(pane,"#productPrice")).setText("1,005");findButton(pane,"Сохранить").fire();
            assertNotNull(productDialog());assertTrue(lookup(pane,"#productError").isVisible());assertEquals("1,005",((TextField)lookup(pane,"#productPrice")).getText());
            assertEquals(new BigDecimal("12.34"),source.price);assertEquals("Original",source.name);
            ((TextField)lookup(pane,"#productPrice")).setText("100,50");((TextField)lookup(pane,"#productStock")).setText("-1");findButton(pane,"Сохранить").fire();assertTrue(text(lookup(pane,"#productError")).contains("неотрицательным"));
            render(pane,"r15-product-validation",500,600);findButton(pane,"Отмена").fire();return null;
        });waitUntil(()->result.get()!=null);assertTrue(result.get().isEmpty());assertEquals("Original",source.name);assertEquals(5,source.stock);
        result.set(null);onFx(()->{Platform.runLater(()->result.set(com.example.authapp.ui.ProductEditDialog.show(null,source)));return null;});waitUntil(()->productDialog()!=null);
        onFx(()->{DialogPane pane=productDialog();((TextField)lookup(pane,"#productName")).setText("Updated");((TextField)lookup(pane,"#productPrice")).setText("100,50");((TextField)lookup(pane,"#productStock")).setText("0");render(pane,"r15-product-editor",500,600);findButton(pane,"Сохранить").fire();return null;});
        waitUntil(()->result.get()!=null);var updated=result.get().orElseThrow();assertNotSame(source,updated);assertEquals(42,updated.id);assertEquals(source.imageUrl,updated.imageUrl);assertEquals(new BigDecimal("100.50"),updated.price);assertEquals("Updated",updated.name);assertEquals(new BigDecimal("12.34"),source.price);
    }
    @Test void sharedProductEditorCreatesDetachedDataAndBothManagementScreensUseIt() throws Exception {
        var result=new java.util.concurrent.atomic.AtomicReference<Optional<com.example.authapp.dto.ProductDTO>>();
        onFx(()->{Platform.runLater(()->result.set(com.example.authapp.ui.ProductEditDialog.show(null,null)));return null;});waitUntil(()->productDialog()!=null);
        onFx(()->{DialogPane pane=productDialog();findButton(pane,"Сохранить").fire();assertTrue(lookup(pane,"#productError").isVisible());((TextField)lookup(pane,"#productName")).setText("New CPU");((TextField)lookup(pane,"#productCategory")).setText("Процессоры");((TextField)lookup(pane,"#productPrice")).setText("25.05");findButton(pane,"Сохранить").fire();return null;});
        waitUntil(()->result.get()!=null);assertEquals(0,result.get().orElseThrow().id);assertEquals(new BigDecimal("25.05"),result.get().orElseThrow().price);
        Shell app=shell();
        for(boolean admin:List.of(true,false)) {
            onFx(()->{if(admin)app.host().openAdminPanel();else app.host().openManagerPanel();return null;});
            waitUntil(()->lookup(app.root(),"#productsTable")!=null);
            onFx(()->{Platform.runLater(()->((Button)lookup(app.root(),"#addProductBtn")).fire());return null;});waitUntil(()->productDialog()!=null);
            onFx(()->{assertNotNull(lookup(productDialog(),"#productPrice"));findButton(productDialog(),"Отмена").fire();return null;});
        }
    }
    private static DialogPane productDialog() {
        for(var window:javafx.stage.Window.getWindows())if(window.isShowing()&&window.getScene().getRoot() instanceof DialogPane pane&&pane.lookup("#productPrice")!=null)return pane;
        return null;
    }
    @Test void ownedOutOfStockPartIsPreservedWhileOnlyMissingPurchasesEnterCart() throws Exception {
        var products=new ArrayList<>(CATALOG);Product gpu=CATALOG.get(1);
        products.set(1,new Product(gpu.getId(),gpu.getName(),gpu.getDescription(),gpu.getPrice(),0,"",gpu.getCategory(),gpu.getManufacturer()));catalogOverride=products;
        Shell app=shell();onFx(()->{Cart.getInstance().clear();app.host().openSuggestedBuild(List.of(gpu),new BigDecimal("100000"));((CheckBox)lookup(app.root(),"#owned-GPU")).fire();((Button)lookup(app.root(),"#completeBuildButton")).fire();return null;});
        waitUntil(()->((Button)lookup(app.root(),"#completeBuildButton")).getText().equals("Подобрать остальные") && text(lookup(app.root(),"#selectedCount")).startsWith("8 из"));
        onFx(()->{
            ConfiguratorController config=(ConfiguratorController)((BorderPane)lookup(app.root(),"#mainPane")).getCenter().getUserData();
            assertEquals(java.util.Set.of(com.example.authapp.domain.ComponentCategory.GPU),config.getOwnedCategories());
            var total=products.stream().filter(p->p.getId()!=gpu.getId()).map(Product::getPrice).reduce(BigDecimal.ZERO,BigDecimal::add);
            assertEquals(com.example.authapp.ui.Ui.money(total),((Label)lookup(app.root(),"#totalPrice")).getText());assertFalse(((Button)lookup(app.root(),"#addBuildToCart")).isDisabled());
            render(app.root(),"r14-owned-completed",1440,900);app.host().openChat();app.host().openConfigurator();assertTrue(((CheckBox)lookup(app.root(),"#owned-GPU")).isSelected());
            assertFalse(text(lookup(app.root(),"#compatStatusText")).contains("отсутствуют"));
            Product cpu=products.getFirst();var changed=new ArrayList<>(products);changed.set(0,new Product(cpu.getId(),cpu.getName(),cpu.getDescription(),cpu.getPrice().add(new BigDecimal("0.01")),cpu.getStock(),"",cpu.getCategory(),cpu.getManufacturer()));catalogOverride=changed;
            ((Button)lookup(app.root(),"#addBuildToCart")).fire();return null;
        });
        waitUntil(()->text(lookup(app.root(),"#cartStatusText")).contains("Цены изменились")&&!((Button)lookup(app.root(),"#addBuildToCart")).isDisabled());
        assertTrue(cartRows.isEmpty());
        onFx(()->{assertTrue(((CheckBox)lookup(app.root(),"#owned-GPU")).isSelected());((Button)lookup(app.root(),"#addBuildToCart")).fire();return null;});
        waitUntil(()->cartRows.size()==7&&!((Button)lookup(app.root(),"#addBuildToCart")).isDisabled());
        assertEquals(0,cartRows.stream().filter(row->row.getInt("product_id")==products.getFirst().getId()).findFirst().orElseThrow().getBigDecimal("price").compareTo(products.getFirst().getPrice().add(new BigDecimal("0.01"))));
        assertTrue(cartRows.stream().noneMatch(row->row.getInt("product_id")==gpu.getId()));
        onFx(()->{
            for(var category:com.example.authapp.domain.ComponentCategory.values()) {
                CheckBox box=(CheckBox)lookup(app.root(),"#owned-"+category.name());if(!box.isSelected())box.fire();
            }
            assertEquals("0 ₽",((Label)lookup(app.root(),"#totalPrice")).getText());assertTrue(((Button)lookup(app.root(),"#addBuildToCart")).isDisabled());
            ConfiguratorController config=(ConfiguratorController)((BorderPane)lookup(app.root(),"#mainPane")).getCenter().getUserData();config.selectSuggestedBuild(CATALOG);assertTrue(config.getOwnedCategories().isEmpty());
            ((CheckBox)lookup(app.root(),"#owned-CPU")).fire();app.host().onLogout();app.host().openConfigurator();
            ConfiguratorController clean=(ConfiguratorController)((BorderPane)lookup(app.root(),"#mainPane")).getCenter().getUserData();
            assertTrue(clean.getSelectedProducts().isEmpty());assertTrue(clean.getOwnedCategories().isEmpty());return null;
        });
    }
    @Test void cancellingCompletionWithANewBudgetCannotRestoreItsOldSelection() throws Exception {
        Shell app=shell();onFx(()->{
            app.host().openSuggestedBuild(List.of(CATALOG.get(1)),new BigDecimal("100000"));
            ConfiguratorController config=(ConfiguratorController)((BorderPane)lookup(app.root(),"#mainPane")).getCenter().getUserData();
            ((CheckBox)lookup(app.root(),"#owned-GPU")).fire();((Button)lookup(app.root(),"#completeBuildButton")).fire();config.setBudget(new BigDecimal("1000"));
            assertEquals(1,config.getSelectedProducts().size());assertEquals(new BigDecimal("1000"),config.getBudget());return null;
        });
        onFx(()->{ConfiguratorController config=(ConfiguratorController)((BorderPane)lookup(app.root(),"#mainPane")).getCenter().getUserData();assertEquals(1,config.getSelectedProducts().size());assertTrue(config.getOwnedCategories().contains(com.example.authapp.domain.ComponentCategory.GPU));return null;});
    }
    @Test void alternativeBuildCardsOpenTheirOwnComposition() throws Exception {
        var products=new ArrayList<>(CATALOG);
        products.add(new Product(10001,"Alternative CPU A","socket: AM5",new BigDecimal("18990.01"),2,"","Процессоры","AMD"));
        products.add(new Product(10002,"Alternative RAM B","memory: DDR5",new BigDecimal("5590.02"),2,"","Оперативная память","Kingston"));
        catalogOverride=products;Shell app=shell();
        onFx(()->{app.host().openChat();submitChat(app.root(),"Собери ПК до 100000 рублей для игр, покажи 3 варианта сборки");return null;});
        waitUntil(()->app.root().lookupAll(".chat-build-card").size()==3 && !((Button)lookup(app.root(),"#sendButton")).isDisabled());
        onFx(()->{
            assertTrue(text(lookup(app.root(),"#messages")).contains("Вариант 3"));
            var cards=app.root().lookupAll(".chat-build-card").stream().sorted(Comparator.comparingDouble(n->n.localToScene(n.getBoundsInLocal()).getMinY())).toList();
            Node chosen=cards.get(2);var expected=products.stream().filter(p->nodes(chosen,Label.class).stream().anyMatch(label->label.getText().equals(p.getName()))).map(Product::getId).collect(java.util.stream.Collectors.toSet());
            assertEquals(8,expected.size());render(app.root(),"r13-alternative-cards",1440,900);
            Button open=findButton(chosen,"Открыть в конфигураторе");assertNotNull(open);open.fire();
            ConfiguratorController config=(ConfiguratorController)((BorderPane)lookup(app.root(),"#mainPane")).getCenter().getUserData();
            assertEquals(expected,config.getSelectedProducts().stream().map(Product::getId).collect(java.util.stream.Collectors.toSet()));
            render(app.root(),"r13-chosen-alternative",1440,900);return null;
        });
    }
    @Test void buildCapacitySummaryAndDialogApplyValidateAndClear() throws Exception {
        Shell app=shell();onFx(()->{app.host().openChat();submitChat(app.root(),"Собери ПК до 100000 рублей для игр, ОЗУ 32 ГБ и SSD 2 ТБ");return null;});
        waitUntil(()->!((Button)lookup(app.root(),"#sendButton")).isDisabled() && text(lookup(app.root(),"#constraintsValue")).contains("2000"));
        onFx(()->{assertTrue(text(lookup(app.root(),"#constraintsValue")).contains("32 ГБ"));render(app.root(),"r12-build-summary",1440,900);Platform.runLater(()->((Button)app.root().lookupAll(".chat-link").stream().filter(n->n instanceof Button b&&b.getText().equals("Изменить запрос")).findFirst().orElseThrow()).fire());return null;});
        waitUntil(()->capacityDialog()!=null);
        onFx(()->{DialogPane pane=capacityDialog();assertEquals("32",((TextField)lookup(pane,"#requestRam")).getText());((TextField)lookup(pane,"#requestRam")).setText("0");((Button)pane.lookupButton(pane.getButtonTypes().stream().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow())).fire();assertNotNull(capacityDialog());assertTrue(pane.lookup(".chat-error").isVisible());pane.applyCss();pane.layout();
            Label error=(Label)pane.lookup(".chat-error");assertTrue(error.getHeight()+1>=error.prefHeight(error.getWidth()),"The complete validation message must be visible");
            render(pane,"r19-request-validation",500,650);return null;});
        onFx(()->{DialogPane pane=capacityDialog();((TextField)lookup(pane,"#requestRam")).setText("64");((TextField)lookup(pane,"#requestStorage")).setText("1000");((CheckBox)lookup(pane,"#requestVerified")).setSelected(true);render(pane,"r12-build-dialog",500,650);((Button)pane.lookupButton(pane.getButtonTypes().stream().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow())).fire();return null;});
        waitUntil(()->!((Button)lookup(app.root(),"#sendButton")).isDisabled() && text(lookup(app.root(),"#constraintsValue")).contains("64 ГБ"));
        onFx(()->{assertTrue(text(lookup(app.root(),"#constraintsValue")).contains("1000 ГБ"));assertTrue(text(lookup(app.root(),"#constraintsValue")).contains("Только проверенная"));submitChat(app.root(),"Сбрось ограничения");return null;});
        waitUntil(()->!((Button)lookup(app.root(),"#sendButton")).isDisabled() && !lookup(app.root(),"#constraintsValue").isVisible());
    }
    private static DialogPane capacityDialog() {
        for(var window:javafx.stage.Window.getWindows())if(window.isShowing()&&window.getScene().getRoot() instanceof DialogPane pane&&pane.lookup("#requestRam")!=null)return pane;
        return null;
    }
    @Test void decimalCartDiscountAndOrderHistoryKeepCents() throws Exception {
        var product=new Product(8001,"Товар с копейками","",new BigDecimal("10.05"),10,"","Процессоры","");
        cartRows.add(cartRow(product,3));
        promoRows.add(new JSONObject().put("id",91).put("code","TEN").put("discount_percent",new BigDecimal("10.00"))
                .put("max_uses",100).put("used_count",0).put("expiry_date","2099-12-31").put("is_active",true));
        var item=new JSONObject().put("id",1).put("order_id",91).put("product_id",8001).put("product_name",product.getName())
                .put("quantity",3).put("price",new BigDecimal("10.05")).put("subtotal",new BigDecimal("30.15"));
        orderRows.add(new JSONObject().put("id",91).put("user_id",SessionManager.getUserId()).put("status","pending")
                .put("created_at","2026-10-30T12:00:00Z").put("total_amount",new BigDecimal("30.15"))
                .put("discount_amount",new BigDecimal("3.02")).put("final_amount",new BigDecimal("27.13"))
                .put("order_items",new JSONArray().put(item)));
        Shell app=shell();onFx(()->{app.host().openCart();return null;});
        waitUntil(()->text(lookup(app.root(),"#totalLabel")).contains("30,15"));
        onFx(()->{((TextField)lookup(app.root(),"#promoCodeField")).setText("TEN");((Button)lookup(app.root(),"#applyPromoButton")).fire();return null;});
        waitUntil(()->text(lookup(app.root(),"#totalLabel")).contains("27,13"));
        onFx(()->{assertTrue(text(lookup(app.root(),"#discountLabel")).contains("3,02"));render(app.root(),"r11-decimal-cart",1200,800);app.host().openCabinetInMain();return null;});
        waitUntil(()->!app.root().lookupAll(".order-card").isEmpty());
        onFx(()->{Node card=app.root().lookup(".order-card");assertTrue(text(card).contains("Товар с копейками"));assertTrue(text(card).contains("30,15"));assertTrue(text(card).contains("27,13"));((ScrollPane)lookup(app.root(),"#scrollPane")).setVvalue(1);render(app.root(),"r11-decimal-order",1200,800);return null;});
    }
    private static JSONObject cartRow(Product product,int quantity){return new JSONObject().put("id",product.getId()).put("product_id",product.getId()).put("product_name",product.getName()).put("price",product.getPrice()).put("quantity",quantity).put("product_image","");}
    @Test void configuratorPickerSearchRemoveAndKeyboardCloseWork() throws Exception {
        Shell app=shell();
        onFx(()->{
            app.host().openSuggestedBuild(CATALOG,new BigDecimal("100000"));
            ConfiguratorController config=(ConfiguratorController)((BorderPane)lookup(app.root(),"#mainPane")).getCenter().getUserData();
            Button edit=findStyledButton(app.root(),"config-component-edit");assertNotNull(edit);edit.fire();
            assertTrue(lookup(app.root(),"#overlayPane").isVisible());
            TextField search=(TextField)lookup(app.root(),"#pickerSearch");search.setText("no-such-product");
            assertTrue(text(lookup(app.root(),"#overlayProductsGrid")).contains("Ничего не найдено"));
            search.clear();assertNotNull(findButton(app.root(),"Выбрать компонент"));
            render(app.root(),"configurator-picker",1440,900);
            ((Button)lookup(app.root(),"#clearSelectionBtn")).fire();assertEquals(7,config.getSelectedProducts().size());
            findStyledButton(app.root(),"config-component-edit").fire();
            lookup(app.root(),"#configuratorRoot").fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.ESCAPE,false,false,false,false));
            assertFalse(lookup(app.root(),"#overlayPane").isVisible());assertEquals(7,config.getSelectedProducts().size());return null;
        });
    }
    @Test void enterSendsAndShiftEnterDoesNotSend() throws Exception {
        Shell app=shell();onFx(()->{app.host().openChat();TextArea input=(TextArea)lookup(app.root(),"#input");input.setText("Что такое сокет?");input.positionCaret(input.getLength());input.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.ENTER,true,false,false,false));assertEquals("Что такое сокет?\n",input.getText());assertTrue(input.getText().contains("\n"));input.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.ENTER,false,false,false,false));assertTrue(input.getText().isEmpty());return null;});
        waitUntil(()->text(lookup(app.root(),"#messages")).contains("Разъём CPU"));
    }
    @Test void emailConfirmationRegistrationIsNotFailure(){try{assertTrue(SessionManager.register("test@example.invalid","test-password"));assertTrue(SessionManager.isRegistrationAwaitingConfirmation());assertNull(SessionManager.getAccessToken());}finally{assertTrue(SessionManager.login("test@example.invalid","test-password"));}}

    private static List<Node> children(Node node){
        if(node instanceof ScrollPane scroll)return scroll.getContent()==null?List.of():List.of(scroll.getContent());
        if(node instanceof TitledPane pane)return pane.getContent()==null?List.of():List.of(pane.getContent());
        if(node instanceof TabPane tabs)return tabs.getTabs().stream().map(Tab::getContent).filter(Objects::nonNull).toList();
        return node instanceof Parent parent?parent.getChildrenUnmodifiable():List.of();
    }
    private static Node lookup(Node node,String selector){
        if(node==null)return null;if(selector.equals("#"+node.getId()))return node;
        for(Node child:children(node)){Node found=lookup(child,selector);if(found!=null)return found;}return null;
    }
    private static Button findButton(Node root,String label){if(root instanceof Button b&&label.equals(b.getText()))return b;for(Node child:children(root)){Button found=findButton(child,label);if(found!=null)return found;}return null;}
    private static Button findStyledButton(Node root,String style){if(root instanceof Button b&&b.getStyleClass().contains(style))return b;for(Node child:children(root)){Button found=findStyledButton(child,style);if(found!=null)return found;}return null;}
    private static String text(Node node){if(node==null)return "";StringBuilder result=new StringBuilder();if(node instanceof Labeled l)result.append(l.getText()).append('\n');if(node instanceof Text t)result.append(t.getText()).append('\n');for(Node child:children(node))result.append(text(child));return result.toString();}
    private static String visibleText(Node node){
        if(node==null || !node.isVisible())return "";
        if(node instanceof TitledPane pane)return pane.getText()+"\n"+(pane.isExpanded()?visibleText(pane.getContent()):"");
        StringBuilder result=new StringBuilder();if(node instanceof Labeled label)result.append(label.getText()).append('\n');if(node instanceof Text text)result.append(text.getText()).append('\n');
        for(Node child:children(node))result.append(visibleText(child));return result.toString();
    }
    private static <T extends Node> List<T> nodes(Node root,Class<T> type){
        List<T> result=new ArrayList<>();if(type.isInstance(root))result.add(type.cast(root));for(Node child:children(root))result.addAll(nodes(child,type));return result;
    }
    private static List<TitledPane> detailsPanes(Node root,String title){return nodes(root,TitledPane.class).stream().filter(p->p.getText().equals(title)).toList();}
    private static void dispose(Object c){if(c instanceof ChatController a)a.dispose();else if(c instanceof ConfiguratorController a)a.dispose();else if(c instanceof AuthController a)a.dispose();else if(c instanceof CartController a)a.dispose();else if(c instanceof CabinetController a)a.dispose();else if(c instanceof AdminController a)a.dispose();else if(c instanceof ManagerController a)a.dispose();}
    private static void render(Parent root,String name,int width,int height)throws Exception{
        if(root.getScene()==null)new Scene(root,width,height);
        double oldWidth=root.getLayoutBounds().getWidth(),oldHeight=root.getLayoutBounds().getHeight();
        root.resize(width,height);root.applyCss();root.layout();
        try{WritableImage image=root.snapshot(null,null);BufferedImage png=new BufferedImage((int)image.getWidth(),(int)image.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,image.getPixelReader().getArgb(x,y));Path output=Path.of("build/reports/ui",name+".png");Files.createDirectories(output.getParent());ImageIO.write(png,"png",output.toFile());}
        finally{if(oldWidth>0&&oldHeight>0){root.resize(oldWidth,oldHeight);root.layout();}}
    }
    private static void waitUntil(Callable<Boolean> condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<end){if(onFx(condition))return;Thread.sleep(25);}fail("Condition did not become true");}
    private static <T>T onFx(Callable<T> action)throws Exception{FutureTask<T> task=new FutureTask<>(action);Platform.runLater(task);return task.get(40,TimeUnit.SECONDS);}
}
