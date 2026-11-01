package com.example.authapp.services;

import com.example.authapp.models.Product;
import com.sun.net.httpserver.HttpServer;
import org.json.*;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalChatEngineTest {
    private HttpServer server;
    private ExecutorService executor;
    private URI base;
    private volatile String plan = plan("BUILD", new JSONArray(), 100000, "100 тысяч", "GAMING");
    private volatile String override;
    private volatile int status = 200;
    private volatile boolean stallBody;
    private final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<JSONObject> request = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private static final List<Product> CATALOG = List.of(new Product(1,"Example Zephyr K","",12345,2,"","Процессоры","Example"));

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(r -> { var thread = new Thread(r); thread.setDaemon(true); return thread; });
        server.setExecutor(executor);
        server.createContext("/api/chat", exchange -> {
            calls.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] body = (override == null ? envelope(plan) : override).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, stallBody ? 0 : body.length);
            try (var out = exchange.getResponseBody()) {
                if (stallBody) {
                    out.write('{'); out.flush(); entered.countDown();
                    try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                } else { entered.countDown(); out.write(body); }
            } catch (IOException ignored) { /* client may cancel */ }
        });
        server.start(); base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void close() { release.countDown(); server.stop(0); executor.shutdownNow(); }
    private OllamaIntentClient client(Duration timeout) { return new OllamaIntentClient(base,"qwen3:14b",timeout); }
    private ChatEngine engine() { return new LocalChatEngine(new ChatBotService(CATALOG), client(Duration.ofSeconds(3)), "Помощник готов"); }
    private static String plan(String action, JSONArray queries, Object budget, Object evidence, String usage) {
        return new JSONObject().put("action",action).put("queries",queries).put("budget",budget == null ? JSONObject.NULL : budget)
                .put("budgetText",evidence == null ? JSONObject.NULL : evidence).put("usage",usage).toString();
    }
    private static String envelope(String content) {
        return new JSONObject().put("model","qwen3:14b").put("done",true).put("done_reason","stop")
                .put("message",new JSONObject().put("role","assistant").put("content",content)).toString();
    }
    @Test void replacementUsesTheProposedBuildWithoutAModelCallEvenWhenTheModelIsUnavailable() throws Exception {
        status=503;
        var engine=new LocalChatEngine(new ChatBotService(ChatQualityProbe.loadCatalog()),client(Duration.ofSeconds(3)),"Помощник готов");
        var initial=engine.reply("Собери ПК до 100000 рублей для игр");
        assertInstanceOf(ChatBotService.BuildContent.class,initial.content().getFirst());
        var question=engine.reply("замени видеокарту на 4080");assertTrue(question.text().contains("4080 SUPER"));
        var replaced=engine.reply("Да");var card=assertInstanceOf(ChatBotService.BuildContent.class,replaced.content().getFirst());
        assertTrue(card.parts().stream().anyMatch(p->p.id()==15));assertEquals(0,calls.get());assertFalse(engine.usedModel());
        assertEquals(ChatEngine.Mode.RULES,engine.mode());
    }

    @Test void reportedShortCpuCommandsPreserveTheBuildInsteadOfAskingTheModelToRegenerateIt() throws Exception {
        for(int responseStatus:List.of(200,503)) {
            status=responseStatus;
            var engine=new LocalChatEngine(new ChatBotService(ChatQualityProbe.loadCatalog()),client(Duration.ofSeconds(3)),"Помощник готов");
            var initial=engine.reply("Собери ПК до 150000 рублей для монтажа");
            var before=assertInstanceOf(ChatBotService.BuildContent.class,initial.content().getFirst());
            var summary=engine.summary();
            var brand=engine.reply("поменяй проц на амд");
            assertTrue(brand.content().isEmpty(),"Brand-only editing must not regenerate the build");
            assertTrue(brand.text().contains("Выберите конкретную модель AMD"));assertEquals(summary,engine.summary());
            var question=engine.reply("поменяй проц на амд 7800");
            assertTrue(question.content().isEmpty());assertEquals(List.of("AMD Ryzen 7 7800X3D"),question.suggestions());
            assertEquals(summary,engine.summary());
            var answer=engine.reply("Да");
            var after=assertInstanceOf(ChatBotService.BuildContent.class,answer.content().getFirst());
            assertTrue(answer.lead().contains("Заменил"));assertTrue(after.parts().stream().anyMatch(p->p.id()==8));
            assertEquals(before.parts().stream().filter(p->!p.category().equals("Процессоры")).toList(),
                    after.parts().stream().filter(p->!p.category().equals("Процессоры")).toList());
            assertEquals(summary,engine.summary());assertFalse(after.verified());assertFalse(after.findings().isEmpty());
            assertEquals(0,calls.get());assertFalse(engine.usedModel());
        }
    }

    @Test void everyOfferedPurposeButtonCompletesTheWizardWithoutCallingTheModel() throws Exception {
        status=503;
        var expected=new java.util.LinkedHashMap<String,BuildRecommendationService.Usage>();
        expected.put("Для игр",BuildRecommendationService.Usage.GAMING);
        expected.put("Для учёбы и офиса",BuildRecommendationService.Usage.OFFICE);
        expected.put("Для монтажа и рабочих задач",BuildRecommendationService.Usage.WORKSTATION);
        for(var choice:expected.entrySet())for(boolean budgetFirst:List.of(true,false)) {
            var engine=new LocalChatEngine(new ChatBotService(ChatQualityProbe.loadCatalog()),client(Duration.ofSeconds(3)),"Помощник готов");
            var begin=engine.reply("Собери ПК");
            assertTrue(begin.suggestions().contains("До 150 000 рублей"));
            ChatBotService.Reply answer;
            if(budgetFirst) {
                var task=engine.reply("До 150 000 рублей");
                assertEquals(List.copyOf(expected.keySet()),task.suggestions());
                answer=engine.reply(task.suggestions().stream().filter(choice.getKey()::equals).findFirst().orElseThrow());
            } else {engine.reply(choice.getKey());answer=engine.reply("До 150 000 рублей");}
            var build=assertInstanceOf(ChatBotService.BuildContent.class,answer.content().getFirst(),choice.getKey());
            assertEquals(choice.getValue(),build.usage());assertEquals(choice.getValue(),engine.summary().usage());
            assertEquals(0,new BigDecimal("150000").compareTo(engine.summary().budget()));assertEquals(8,build.parts().size());
            assertEquals(ChatEngine.Mode.RULES,engine.mode());assertFalse(engine.usedModel());
        }
        assertEquals(0,calls.get(),"An application-generated purpose button must never depend on the model");
    }

    @Test void freeRequestBecomesValidatedBuildAndTransportHasNoCredentials() {
        var engine = engine();
        var answer = engine.reply("Потолок трат 100 тысяч, хочется машинку для игр");
        assertEquals(new BigDecimal("100000"), answer.request().budget());
        assertEquals(BuildRecommendationService.Usage.GAMING, answer.request().usage());
        assertTrue(engine.status().contains("Локальная модель"));
        assertNull(authorization.get());
        var body = request.get();
        assertFalse(body.getBoolean("stream")); assertFalse(body.getBoolean("think"));
        assertEquals(8192,body.getJSONObject("options").getInt("num_ctx"));
        assertEquals(384,body.getJSONObject("options").getInt("num_predict"));
        assertFalse(body.getJSONObject("format").getBoolean("additionalProperties"));
        assertEquals(2,body.getJSONArray("messages").length());
    }
    @Test void explicitCommandsAndOwnershipNeverNeedTheModel() {
        var engine = engine();
        assertTrue(engine.reply("Найди Example Zephyr K").text().contains("Example Zephyr K"));
        engine.reply("У меня есть Example Zephyr K"); engine.reply("нет"); engine.reply("Сброс");
        assertEquals(0,calls.get()); assertNull(engine.summary().budget());
    }
    @Test void searchUsesTypedCatalogCardsAndNeverInventsSuffixes() {
        plan = plan("SEARCH",new JSONArray().put("Example Zephyr K"),null,null,"KEEP");
        var answer = engine().reply("Можно взглянуть на Example Zephyr K?");
        var card = assertInstanceOf(ChatBotService.ProductsContent.class,answer.content().getFirst());
        assertEquals(1,card.items().getFirst().id()); assertEquals(new BigDecimal("12345.00"),card.items().getFirst().price());
        plan = plan("SEARCH",new JSONArray().put("Example Zephyr F"),null,null,"KEEP");
        var engine = engine(); engine.reply("Можно взглянуть на Example Zephyr K?");
        assertEquals(LocalChatEngine.FALLBACK,engine.status());
    }
    @Test void absentCatalogModelRemainsAbsent() {
        plan = plan("SEARCH",new JSONArray().put("RTX 9999"),null,null,"KEEP");
        var answer = engine().reply("Хочу взглянуть на RTX 9999");
        assertTrue(answer.text().contains("ничего не найдено"),answer.text()); assertTrue(answer.content().isEmpty());
    }
    @Test void copiedSubstringCannotDropARealModelVariant() {
        var rules = new ChatBotService(List.of(
                new Product(1,"NVIDIA GeForce RTX 4070","",50000,1,"","Видеокарты","NVIDIA"),
                new Product(2,"NVIDIA GeForce RTX 4070 Ti SUPER","",70000,1,"","Видеокарты","NVIDIA")));
        assertFalse(rules.preservesModelIdentity("Интересует RTX 4070 Ti SUPER",List.of("RTX 4070")));
        assertTrue(rules.preservesModelIdentity("Интересует RTX 4070 Ti SUPER",List.of("RTX 4070 Ti SUPER")));
        assertThrows(IllegalArgumentException.class,()->LocalChatPlan.parse(plan("SEARCH",new JSONArray().put("Ryzen 5 7600"),null,null,"KEEP")).command("Интересует Ryzen 5 7600X"));
    }
    @Test void comparisonAndDefinitionOnlyUseCopiedQueries() {
        plan = plan("COMPARE",new JSONArray().put("Example Zephyr K").put("Example Zephyr F"),null,null,"KEEP");
        assertTrue(engine().reply("Выбираю между Example Zephyr K либо Example Zephyr F").text().contains("не найдена"));
        plan = plan("DEFINE",new JSONArray().put("TDP"),null,null,"KEEP");
        assertTrue(engine().reply("Расшифруй TDP").text().contains("TDP"));
        assertThrows(IllegalArgumentException.class,()->LocalChatPlan.parse(plan("COMPARE",new JSONArray().put("CPU K").put("CPU F"),null,null,"KEEP")).command("CPU K либо CPU KF"));
    }
    @Test void draftsDoNotLeakUntilCommitAndCatalogRemainsCurrent() {
        var engine = engine(); var draft = engine.fork();
        draft.reply("Потолок трат 100 тысяч, хочется машинку для игр");
        assertNull(engine.summary().budget());
        engine.updateCatalog(List.of(new Product(2,"Updated CPU","",7000,1,"","Процессоры","")));
        engine.commitConversation(draft);
        assertEquals(new BigDecimal("100000"),engine.summary().budget());
        assertTrue(engine.reply("Найди Updated CPU").text().contains("Updated CPU"));
        engine.reply("Сброс"); assertNull(engine.summary().budget());
        assertNull(engine.fork().summary().budget());
    }
    @Test void wrongBudgetAndExtraFieldsAreRejectedWithoutStateChanges() {
        for (String invalid : List.of(
                plan("BUILD",new JSONArray(),200000,"100 тысяч","GAMING"),
                plan("BUILD",new JSONArray(),100000,"100000","GAMING"),
                new JSONObject(plan).put("price",1).toString(),
                plan("PURCHASE",new JSONArray(),null,null,"KEEP"))) {
            plan = invalid; var engine = engine(); engine.reply("Потолок трат 100 тысяч, хочется машинку для игр");
            assertEquals(LocalChatEngine.FALLBACK,engine.status()); assertNull(engine.summary().budget());
        }
    }
    @Test void numericModelAndForeignCurrencyCannotBecomeRubles() {
        assertThrows(IllegalArgumentException.class,()->LocalChatPlan.parse(plan("BUILD",new JSONArray(),4090,"4090","GAMING")).command("Хочу RTX 4090"));
        assertThrows(IllegalArgumentException.class,()->LocalChatPlan.parse(plan("BUILD",new JSONArray(),100,"100 долларов","GAMING")).command("Потолок трат 100 долларов"));
        assertThrows(IllegalArgumentException.class,()->LocalChatPlan.parse(plan("BUILD",new JSONArray(),200000,"200 тысяч","KEEP")).command("До 100000 рублей вместо 200 тысяч"));
    }
    @Test void modelIsUnavailableFallsBackAndKnownCommandsStillWork() {
        status = 503; var engine = engine();
        assertTrue(engine.reply("Помоги определиться с железом").text().startsWith("Уточните запрос"));
        assertEquals(LocalChatEngine.FALLBACK,engine.status());
        assertTrue(engine.reply("Найди Example Zephyr K").text().contains("Example Zephyr K"));
        assertEquals(1,calls.get());
    }
    @Test void malformedTruncatedAndOversizedResponsesFallBack() {
        for (String invalid : List.of("{", new JSONObject(envelope(plan)).put("done",false).toString(),
                envelope(plan("BUILD",new JSONArray(),100000,"","GAMING")),
                new JSONObject(envelope(plan)).put("done_reason","length").toString(),
                new JSONObject(envelope(plan)).put("model","other-model").toString(), " ".repeat(65537))) {
            override = invalid; var engine = engine(); engine.reply("Потолок трат 100 тысяч, хочется машинку для игр");
            assertEquals(LocalChatEngine.FALLBACK,engine.status()); assertNull(engine.summary().budget());
        }
    }
    @Test void deadlineCoversAStalledResponseBody() {
        stallBody = true;
        assertThrows(IOException.class,()->client(Duration.ofMillis(250)).plan("тест",new ChatBotService.RequestSummary(null,null)));
        assertEquals(0,entered.getCount());
    }
    @Test void interruptionCancelsTransportAndIsNotConvertedToFallback() throws Exception {
        stallBody = true; var cancelled = new CountDownLatch(1); var interrupted = new AtomicBoolean();
        Future<?> task = executor.submit(()->{
            try { client(Duration.ofSeconds(4)).plan("тест",new ChatBotService.RequestSummary(null,null)); }
            catch (CancellationException ex) { interrupted.set(Thread.currentThread().isInterrupted()); cancelled.countDown(); }
            catch (IOException ex) { throw new AssertionError(ex); }
        });
        assertTrue(entered.await(2,TimeUnit.SECONDS)); task.cancel(true);
        assertTrue(cancelled.await(2,TimeUnit.SECONDS)); assertTrue(interrupted.get());
    }
    @Test void redirectsAreNotFollowed() {
        server.createContext("/redirect",exchange->{fail("Redirect must not be followed");exchange.close();});
        server.removeContext("/api/chat");
        server.createContext("/api/chat",exchange->{exchange.getResponseHeaders().set("Location",base+"/redirect");exchange.sendResponseHeaders(302,-1);exchange.close();});
        assertThrows(IOException.class,()->client(Duration.ofSeconds(2)).plan("тест",new ChatBotService.RequestSummary(null,null)));
    }
    @Test void onlyLiteralLoopbackAndLocalModelsAreAccepted() {
        for (String url : List.of("https://127.0.0.1:11434","http://example.com:11434","http://localhost:11434", "http://127.0.0.1:11434/private", "http://user@127.0.0.1:11434", "http://127.0.0.1:11434?token=private"))
            assertThrows(IllegalArgumentException.class,()->new OllamaIntentClient(URI.create(url),"qwen3:14b",Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,()->new OllamaIntentClient(base,"qwen3:cloud",Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,()->new OllamaIntentClient(base,"qwen3:CLOUD",Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,()->new OllamaIntentClient(base,"QWEN3:Cloud",Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,()->new OllamaIntentClient(base,"../model",Duration.ofSeconds(1)));
    }
    @Test void freeFollowUpChangesBudgetAndNegatedTaskWithoutRepeatingOldBuild() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");
        plan=plan("BUILD",new JSONArray(),150000,"150 тысяч","WORKSTATION");
        var answer=engine.reply("Теперь могу выделить 150 тысяч, вместо игр буду монтировать видео");
        assertEquals(new BigDecimal("150000"),answer.request().budget());
        assertEquals(BuildRecommendationService.Usage.WORKSTATION,answer.request().usage());
        var sent=new JSONObject(request.get().getJSONArray("messages").getJSONObject(1).getString("content"));
        assertTrue(sent.getBoolean("buildActive"));assertEquals(100000,sent.getJSONObject("previousRequest").getInt("budget"));
        assertFalse(sent.getJSONArray("recentMessages").isEmpty());
    }
    @Test void failureOrUnrelatedQuestionCannotMutateActiveBuild() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");status=503;
        var answer=engine.reply("Теперь могу выделить 150 тысяч");
        assertTrue(answer.content().isEmpty());assertEquals(new BigDecimal("100000"),engine.summary().budget());
        status=200;plan=plan("LOCAL",new JSONArray(),null,null,"KEEP");
        assertTrue(engine.reply("Какая погода завтра?").content().isEmpty());
        assertEquals(new BigDecimal("100000"),engine.summary().budget());
    }
    @Test void historyIsBoundedAndResetAndCatalogRefreshClearIt() {
        var engine=engine();for(int i=0;i<7;i++)engine.reply("Найди Example Zephyr K");
        plan=plan("LOCAL",new JSONArray(),null,null,"KEEP");engine.reply("Что дальше?");
        var sent=new JSONObject(request.get().getJSONArray("messages").getJSONObject(1).getString("content"));
        assertEquals(4,sent.getJSONArray("recentMessages").length());
        engine.reply("Сброс");engine.reply("Что дальше?");
        sent=new JSONObject(request.get().getJSONArray("messages").getJSONObject(1).getString("content"));
        assertTrue(sent.getJSONArray("recentMessages").isEmpty());
        engine.reply("Найди Example Zephyr K");engine.updateCatalog(CATALOG);engine.reply("Что дальше?");
        sent=new JSONObject(request.get().getJSONArray("messages").getJSONObject(1).getString("content"));
        assertTrue(sent.getJSONArray("recentMessages").isEmpty());
    }
    @Test void negativeAndZeroBudgetsDoNotReplaceValidBudget() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");
        var before=engine.summary();
        for(String bad:List.of("Собери ПК до -50000 рублей для игр","Собери ПК до 0 рублей для игр","Собери ПК до 0 рублей для офиса, ОЗУ 32 ГБ")) {
            var answer=engine.reply(bad);assertTrue(answer.text().contains("больше нуля"));
            assertEquals(new BigDecimal("100000"),engine.summary().budget());
            assertEquals(before,engine.summary());
        }
        assertThrows(IllegalArgumentException.class,()->LocalChatPlan.parse(plan("BUILD",new JSONArray(),50000,"50 тысяч","GAMING")).command("Готов выделить минус 50 тысяч"));
    }
    @Test void pronounFollowUpUsesCurrentComparisonAndCannotCreateOne() {
        var products=List.of(new Product(1,"Example Zephyr K","",10000,1,"","Процессоры",""),new Product(2,"Example Zephyr F","",11000,1,"","Процессоры",""));
        var engine=new LocalChatEngine(new ChatBotService(products),client(Duration.ofSeconds(3)),"Помощник готов");
        engine.reply("Сравни Example Zephyr K и Example Zephyr F");
        plan=plan("CONTEXT",new JSONArray(),null,null,"WORKSTATION");
        var answer=engine.reply("А кто из них пригодится для монтажа вместо игр?");
        assertTrue(answer.text().contains("Example Zephyr K"));
        var sent=new JSONObject(request.get().getJSONArray("messages").getJSONObject(1).getString("content"));
        assertEquals(2,sent.getJSONArray("comparisonModels").length());
        var fresh=engine();assertTrue(fresh.reply("А кто из них пригодится для монтажа?").content().isEmpty());
        assertEquals(ChatEngine.Mode.CLARIFICATION,fresh.mode());assertTrue(fresh.status().contains("две модели"));
    }
    @Test void draftFromAnotherConversationIsRejectedEvenInRulesMode() {
        var first=new LocalChatEngine(new ChatBotService(CATALOG),null,"Помощник готов");
        var second=new LocalChatEngine(new ChatBotService(CATALOG),null,"Помощник готов");
        second.reply("Собери ПК до 150000 рублей для игр");
        assertThrows(IllegalArgumentException.class,()->first.commitConversation(second.fork()));
        assertNull(first.summary().budget());
    }
    @Test void committingACompletedDraftCannotRestoreHistoryAfterCatalogRefresh() {
        var engine=engine();engine.reply("Найди Example Zephyr K");var draft=engine.fork();
        draft.reply("Найди Example Zephyr K");engine.updateCatalog(CATALOG);engine.commitConversation(draft);
        plan=plan("LOCAL",new JSONArray(),null,null,"KEEP");engine.reply("Что дальше?");
        var sent=new JSONObject(request.get().getJSONArray("messages").getJSONObject(1).getString("content"));
        assertTrue(sent.getJSONArray("recentMessages").isEmpty());
    }
    @Test void comparisonOrderAndViewButtonsWorkWithAnUnavailableModelWithoutAnyTransportCall() throws Exception {
        status=503;
        var engine=new LocalChatEngine(new ChatBotService(ChatQualityProbe.loadCatalog()),client(Duration.ofSeconds(3)),"Готов");
        engine.reply("Сравни Ryzen5 7600 и Ryzen7 7800X3D для игр в F1 25 1080p");
        var swapped=engine.reply("Поменяй местами");
        var card=assertInstanceOf(ChatBotService.BenchmarksContent.class,swapped.content().getFirst());
        assertEquals("39.31",card.comparisons().getFirst().differencePercent().toPlainString());
        assertInstanceOf(ChatBotService.SpecificationsContent.class,engine.reply("Сравнить характеристики").content().getFirst());
        var restored=assertInstanceOf(ChatBotService.BenchmarksContent.class,engine.reply("Сравнить по бенчмаркам").content().getFirst());
        assertEquals("25",restored.comparisons().getFirst().left().context().version());
        assertEquals(0,calls.get());assertEquals(ChatEngine.Mode.RULES,engine.mode());
    }
    @Test void modelContextStripsTheExplicitGamingGoalBeforeMatchingThePublishedScenario() throws Exception {
        var engine=new LocalChatEngine(new ChatBotService(ChatQualityProbe.loadCatalog()),client(Duration.ofSeconds(3)),"Готов");
        engine.reply("Сравни RTX4060 и RX7600");
        plan=plan("CONTEXT",new JSONArray(),null,null,"GAMING");
        for(String scenario:List.of("Для игр в Black Myth: Wukong1440p","Для игр в Starfield1440p")) {
            var answer=engine.reply(scenario);
            assertInstanceOf(ChatBotService.BenchmarksContent.class,answer.content().getFirst(),scenario);
            assertEquals(ChatEngine.Mode.LOCAL_MODEL,engine.mode());
        }
        var unknown=engine.reply("Для игр в Black Myth1440p DLSS");
        assertTrue(unknown.content().isEmpty());assertFalse(unknown.text().contains("%"));
    }
    @Test void explanationUsesOnlyPreviousTypedFactsAndRefreshInvalidatesIt() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");
        engine.reply("Найди Example Zephyr K");plan=plan("EXPLAIN",new JSONArray(),null,null,"KEEP");
        var before=engine.summary();var answer=engine.reply("Почему это нашлось?");
        assertTrue(answer.text().contains("Example Zephyr K"));assertTrue(answer.text().contains("12"));
        assertTrue(answer.content().isEmpty());assertEquals(before,engine.summary());
        engine.updateCatalog(CATALOG);answer=engine.reply("Почему это нашлось?");
        assertTrue(answer.text().contains("Сначала"));assertFalse(answer.text().contains("12"));
        assertEquals(ChatEngine.Mode.CLARIFICATION,engine.mode());
    }
    @Test void colloquialCapacitiesKeepUnitsAndMinimumSemantics() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");
        engine.reply("Оперативки 32 гига");engine.reply("Накопитель не меньше 2 терабайт");
        assertEquals(32,engine.summary().minRamGb());assertEquals(2000,engine.summary().minStorageGb());
        assertEquals(0,calls.get());
        var before=engine.summary();var answer=engine.reply("Оперативки не больше 16 гигабайт");
        assertTrue(answer.text().contains("минимум"));assertEquals(before,engine.summary());
    }
    @Test void explicitBuildWithFreeAmountOrNegatedTaskAlsoUsesModel() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");
        plan=plan("BUILD",new JSONArray(),150000,"150 тысяч","GAMING");
        assertEquals(new BigDecimal("150000"),engine.reply("Собери ПК, могу выделить 150 тысяч для игр").request().budget());
        plan=plan("BUILD",new JSONArray(),null,null,"OFFICE");
        assertEquals(BuildRecommendationService.Usage.OFFICE,engine.reply("Собери ПК не для игр, а для офиса").request().usage());
        assertEquals(2,calls.get());
    }
    @Test void relativeAmountCannotSilentlyReplaceTheTotalBudget() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");
        plan=plan("BUILD",new JSONArray(),10000,"10 тысяч","KEEP");
        var answer=engine.reply("Увеличь бюджет на 10 тысяч");
        assertEquals(new BigDecimal("100000"),engine.summary().budget());assertTrue(answer.content().isEmpty());
        assertEquals(ChatEngine.Mode.CLARIFICATION,engine.mode());
    }
    @Test void budgetDeltaIsComputedByJavaWithExactDecimals() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");
        plan=operationPlan(10000,"10 тысяч","ADD");
        assertEquals(new BigDecimal("110000"),engine.reply("Увеличь бюджет на 10 тысяч").request().budget());
        plan=operationPlan(15000,"15 тысяч","SUBTRACT");
        assertEquals(new BigDecimal("95000"),engine.reply("Уменьши бюджет на 15 тысяч").request().budget());
        plan=operationPlan(new BigDecimal("500.25"),"500,25 рублей","ADD");
        assertEquals(new BigDecimal("95500.25"),engine.reply("Добавь к бюджету 500,25 рублей").request().budget());
        assertEquals(BuildRecommendationService.Usage.GAMING,engine.summary().usage());
        plan=operationPlan(new BigDecimal("500.25"),"500,25","ADD");
        assertEquals(new BigDecimal("96000.50"),engine.reply("Добавь к бюджету 500,25 рублей").request().budget());
    }
    @Test void unsafeOrUnclearDeltaNeverChangesConversation() {
        for(String[] sample:List.of(new String[]{"Уменьши бюджет на 110 тысяч","110 тысяч","110000","SUBTRACT"},
                new String[]{"Не увеличивай бюджет на 10 тысяч","10 тысяч","10000","ADD"},
                new String[]{"Увеличь бюджет 10 тысяч","10 тысяч","10000","ADD"},
                new String[]{"Уменьши бюджет на 10 тысяч","10 тысяч","10000","ADD"},
                new String[]{"Добавь 10 к бюджету","10 к","10000","ADD"},
                new String[]{"Добавь 10 тысяч к бюджету, раньше было 100 тысяч","100 тысяч","100000","ADD"},
                new String[]{"Увеличь бюджет на 10 тысяч и уменьши на 5 тысяч","10 тысяч","10000","ADD"})) {
            var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");var before=engine.summary();
            plan=operationPlan(new BigDecimal(sample[2]),sample[1],sample[3]);var answer=engine.reply(sample[0]);
            assertEquals(before,engine.summary(),sample[0]);assertTrue(answer.content().isEmpty(),sample[0]);
        }
        var engine=engine();plan=operationPlan(10000,"10 тысяч","ADD");
        var answer=engine.reply("Увеличь бюджет на 10 тысяч");assertNull(engine.summary().budget());
        assertEquals(ChatEngine.Mode.CLARIFICATION,engine.mode());assertTrue(answer.text().contains("итоговую"));
    }
    private static String operationPlan(Object amount,String evidence,String operation) {
        return new JSONObject(plan("BUILD",new JSONArray(),amount,evidence,"KEEP")).put("budgetOperation",operation).toString();
    }
    @Test void explanationsUseHumanReadableCompatibilityChecks() throws Exception {
        var bot=new ChatBotService(ChatQualityProbe.loadCatalog());
        var answer=bot.reply("Собери ПК до 100000 рублей для игр");
        var explanation=ChatGroundedExplanation.explain(answer,bot.summary()).orElseThrow();
        assertTrue(explanation.text().contains("поддержка точной модели процессора"));
        assertTrue(explanation.text().contains("разъёмы питания видеокарты"));
        assertFalse(explanation.text().contains("cpu_support"));assertFalse(explanation.text().contains("gpu_connectors"));
    }
    @Test void moneyOnlyChangeCannotReintroduceAnOlderTaskFromModelMetadata() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для офиса");
        plan=new JSONObject(operationPlan(10000,"10 тысяч","ADD")).put("usage","GAMING").toString();
        var answer=engine.reply("Добавь 10 тысяч к бюджету");
        assertEquals(new BigDecimal("110000"),answer.request().budget());
        assertEquals(BuildRecommendationService.Usage.OFFICE,answer.request().usage());
    }
    @Test void twoAdditionsNeedClarificationAndUnusedMoneyCannotMutateAnExplanation() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");var before=engine.summary();
        plan=operationPlan(10000,"10 тысяч","ADD");
        assertTrue(engine.reply("Добавь 10 тысяч и ещё 5 тысяч").content().isEmpty());assertEquals(before,engine.summary());
        engine.reply("Найди Example Zephyr K");
        plan=new JSONObject(plan("EXPLAIN",new JSONArray(),1,"1 рубль","OFFICE")).put("budgetOperation","SUBTRACT").toString();
        var answer=engine.reply("Почему это нашлось?");
        assertTrue(answer.text().contains("Example Zephyr K"));assertEquals(before,engine.summary());
    }
    @Test void exactSearchNeverWidensToAnotherSuffixOrCapacity() throws Exception {
        var bot=new ChatBotService(ChatQualityProbe.loadCatalog());
        for(var sample:List.of(Map.entry("Ryzen 5 7600",6),Map.entry("Ryzen 5 7600X",7),Map.entry("Samsung 990 PRO 1TB",40),Map.entry("RTX 4070 SUPER 12GB",14))) {
            var answer=bot.reply("Найди "+sample.getKey());
            var card=assertInstanceOf(ChatBotService.ProductsContent.class,answer.content().getFirst());
            assertEquals(List.of(sample.getValue()),card.items().stream().map(ChatBotService.ProductInfo::id).toList());
        }
    }
    @Test void productOrdinalAndPriceUseOnlyTheLatestCardWithoutChangingBudget() {
        var products=List.of(new Product(1,"Example Zephyr K","",10000,1,"","Процессоры",""),new Product(2,"Example Zephyr F","",12000,2,"","Процессоры",""));
        var engine=new LocalChatEngine(new ChatBotService(products),client(Duration.ofSeconds(3)),"Помощник готов");
        engine.reply("Собери ПК до 100000 рублей для игр");engine.reply("Найди Example");var before=engine.summary();
        var ambiguous=engine.reply("Сколько стоит этот?");assertTrue(ambiguous.content().isEmpty());assertEquals(ChatEngine.Mode.CLARIFICATION,engine.mode());
        var selected=engine.reply("2");var card=assertInstanceOf(ChatBotService.ProductsContent.class,selected.content().getFirst());
        assertEquals(2,card.items().getFirst().id());assertEquals(before,engine.summary());
        assertTrue(engine.reply("А сколько за него денег?").text().contains("12"));assertEquals(0,calls.get());
        var first=engine.reply("А первый?");
        assertEquals(1,assertInstanceOf(ChatBotService.ProductsContent.class,first.content().getFirst()).items().getFirst().id());
        assertEquals(before,engine.summary());
        engine.updateCatalog(List.of(new Product(2,"Changed Product","",1,1,"","Процессоры","")));
        var stale=engine.reply("А сколько за него денег?");assertTrue(stale.content().isEmpty());assertFalse(stale.text().contains("12"));assertEquals(before,engine.summary());
    }
    @Test void aBuildOrdinalCannotBecomeATwoRubleBudget() throws Exception {
        var engine=new LocalChatEngine(new ChatBotService(ChatQualityProbe.loadCatalog()),null,"Помощник готов");
        engine.reply("Собери ПК до 100000 рублей для игр");var before=engine.summary();
        var answer=engine.reply("2");assertEquals(before,engine.summary());assertTrue(answer.text().contains("нет такого варианта"));
    }
    @Test void aNewExplicitModelCannotBorrowThePreviousCardsPrice() {
        var products=List.of(new Product(1,"Example Zephyr K","",10000,1,"","Процессоры",""),new Product(2,"Example Zephyr F","",12000,2,"","Процессоры",""));
        var engine=new LocalChatEngine(new ChatBotService(products),client(Duration.ofSeconds(3)),"Помощник готов");
        engine.reply("Найди Example Zephyr K");
        plan=plan("SEARCH",new JSONArray().put("Example Zephyr F"),null,null,"KEEP");
        var answer=engine.reply("Этот Example Zephyr F сколько стоит?");
        var card=assertInstanceOf(ChatBotService.ProductsContent.class,answer.content().getFirst());
        assertEquals(2,card.items().getFirst().id());assertEquals(new BigDecimal("12000.00"),card.items().getFirst().price());
        plan=plan("EXPLAIN",new JSONArray(),null,null,"KEEP");
        answer=engine.reply("Этот Example Zephyr K сколько стоит?");
        assertEquals(1,assertInstanceOf(ChatBotService.ProductsContent.class,answer.content().getFirst()).items().getFirst().id());
    }
    @Test void priceOrCartWriteRequestCannotChangeTheActiveBuildBudget() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");var before=engine.summary();
        plan=plan("BUILD",new JSONArray(),1,"1 рубль","GAMING");
        for(String raw:List.of("Измени цену этого процессора на 1 рубль","Положи второй в корзину")) {
            assertTrue(engine.reply(raw).content().isEmpty());assertEquals(before,engine.summary());
        }
        assertEquals(0,calls.get());
    }
    @Test void unsupportedBudgetFormatIsClarifiedWithoutClaimingModelFailure() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");var before=engine.summary();
        for(String message:List.of("Увеличь бюджет на 10%","Увеличь бюджет на 10 долларов","Добавь 10 к бюджету","Добавь 10 к сумме")) {
            var answer=engine.reply(message);assertTrue(answer.content().isEmpty());assertEquals(before,engine.summary());
            assertEquals(ChatEngine.Mode.CLARIFICATION,engine.mode());assertFalse(engine.status().contains("недоступна"));
        }
        assertEquals(0,calls.get());
    }
    @Test void capacityAdditionIsNotMoneyAndCannotLowerTheExistingMinimum() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр, ОЗУ 32 ГБ");var before=engine.summary();
        var answer=engine.reply("Добавь 16 ГБ ОЗУ");
        assertTrue(answer.text().contains("минимум"));assertFalse(answer.text().contains("рубл"));
        assertEquals(before,engine.summary());assertEquals(0,calls.get());
    }
    @Test void compactThousandRubleUnitStillAllowsBudgetChange() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");
        plan=operationPlan(10000,"10тысрублей","ADD");
        assertEquals(new BigDecimal("110000"),engine.reply("Добавь 10тысрублей").request().budget());
        assertEquals(1,calls.get());
    }
    @Test void benchmarkPercentQuestionIsNotAProductPriceReference() {
        assertFalse(ChatProductContext.referenceQuestion("Как получились эти проценты?"));
        assertTrue(ChatProductContext.referenceQuestion("Какая цена у этого товара?"));
    }
    @Test void groupPriceSelectionKeepsSourceOrderAndCatalogRefreshDropsIt() {
        var products=List.of(new Product(1,"Example Zephyr K","",20000,0,"","Процессоры",""),new Product(2,"Example Zephyr F","",15000,3,"","Процессоры",""));
        var engine=new LocalChatEngine(new ChatBotService(products),client(Duration.ofSeconds(3)),"Помощник готов");
        engine.reply("Собери ПК до 100000 рублей для игр");engine.reply("Найди Example Zephyr");
        assertEquals(2,assertInstanceOf(ChatBotService.ProductsContent.class,engine.reply("Кто из них дешевле?").content().getFirst()).items().getFirst().id());
        assertEquals(1,assertInstanceOf(ChatBotService.ProductsContent.class,engine.reply("А первый?").content().getFirst()).items().getFirst().id());
        assertEquals(new BigDecimal("100000"),engine.summary().budget());assertEquals(0,calls.get());
        var draft=engine.fork();draft.reply("Кто из них дороже?");engine.updateCatalog(products);engine.commitConversation(draft);
        assertTrue(engine.reply("Кто из них дешевле?").content().isEmpty());
        engine.reply("Найди Example Zephyr");engine.reply("Сброс");assertTrue(engine.reply("Кто из них дешевле?").content().isEmpty());
        assertEquals(0,calls.get());
    }
    @Test void modelCannotConvertAnItemPriceLimitIntoTheWholeBuildBudget() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");var before=engine.summary();
        plan=plan("BUILD",new JSONArray(),15000,"15 тысяч","KEEP");
        var answer=engine.reply("Можно SSD до 15 тысяч?");
        assertTrue(answer.content().isEmpty());assertEquals(before,engine.summary());assertEquals(ChatEngine.Mode.CLARIFICATION,engine.mode());
        var rules=new LocalChatEngine(new ChatBotService(CATALOG),null,"Помощник готов");rules.reply("Собери ПК до 100000 рублей для игр");
        rules.reply("Подбери процессор до 15 тысяч");assertEquals(new BigDecimal("100000"),rules.summary().budget());
    }
    @Test void earmarkedPartMoneyCannotChangeBudgetOrBecomeACapacityConstraint() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");var before=engine.summary();
        for(String raw:List.of("У меня 20 тысяч на процессор","На SSD могу потратить 15 тысяч","Процессор хочу за 25 тысяч","Добавь 10 тысяч на SSD")) {
            var answer=engine.reply(raw);assertTrue(answer.text().contains("цены"));assertTrue(answer.content().isEmpty());
            assertEquals(before,engine.summary());assertEquals(ChatEngine.Mode.CLARIFICATION,engine.mode());
        }assertEquals(0,calls.get());
        assertTrue(ChatItemMoney.problem("Увеличь бюджет на 20 тысяч, ОЗУ 32 ГБ").isEmpty());
        assertTrue(ChatItemMoney.problem("У меня есть Example Zephyr K, собери остальное до 100 тысяч").isEmpty());
    }
    @Test void explicitPartSelectionClearsThePreviousComparisonPair() {
        var products=List.of(new Product(1,"Example Zephyr K","",10000,1,"","Процессоры",""),new Product(2,"Example Zephyr F","",20000,1,"","Процессоры",""));
        var engine=new LocalChatEngine(new ChatBotService(products),client(Duration.ofSeconds(3)),"Помощник готов");
        engine.reply("Сравни Example Zephyr K и Example Zephyr F");engine.reply("Подбери процессор до 15 тысяч");
        plan=plan("CONTEXT",new JSONArray(),null,null,"WORKSTATION");
        assertTrue(engine.reply("А для монтажа кто из них лучше?").content().isEmpty());
        assertEquals(0,new JSONObject(request.get().getJSONArray("messages").getJSONObject(1).getString("content")).getJSONArray("comparisonModels").length());
    }
    @Test void priceFilterRunsBeforeTenCardLimitAndDoesNotPersistAcrossSearchOrRefresh() {
        var products=new ArrayList<Product>();for(int i=1;i<=12;i++)products.add(new Product(i,"Test CPU "+i,"",i==12?100:10000,1,"","Процессоры",""));
        var rules=new ChatBotService(products);rules.reply("Собери ПК до 100000 рублей для игр");
        var filtered=assertInstanceOf(ChatBotService.ProductsContent.class,rules.reply("Найди процессоры до 500 рублей").content().getFirst());
        assertEquals(12,filtered.items().getFirst().id());assertEquals(1,filtered.total());assertEquals(new BigDecimal("100000"),rules.summary().budget());
        var all=assertInstanceOf(ChatBotService.ProductsContent.class,rules.reply("Найди процессоры").content().getFirst());
        assertEquals(12,all.total());assertEquals(10,all.items().size());
        rules.updateCatalog(List.of(new Product(12,"Test CPU 12","",600,1,"","Процессоры","")));
        assertTrue(rules.reply("Найди процессоры до 500 рублей").content().isEmpty());
        assertEquals(new BigDecimal("100000"),rules.summary().budget());
    }
    @Test void namedReferenceStillHonorsPriceAndFilteredListDefinesNewOrdinals() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");
        assertTrue(engine.reply("Эта Example Zephyr K не дороже 10000 рублей?").content().isEmpty());assertEquals(0,calls.get());
        var products=List.of(new Product(1,"Example Zephyr K","",10000,1,"","Процессоры",""),new Product(2,"Example Zephyr F","",20000,1,"","Процессоры",""),new Product(3,"Example Zephyr G","",30000,1,"","Процессоры",""));
        var local=new LocalChatEngine(new ChatBotService(products),null,"Помощник готов");local.reply("Найди Example Zephyr");
        local.reply("Оставь из них от 20 тысяч");
        assertEquals(3,assertInstanceOf(ChatBotService.ProductsContent.class,local.reply("А второй?").content().getFirst()).items().getFirst().id());
    }
    @Test void cancelledDeltaDraftAndRetryCannotApplyMoneyTwice() throws Exception {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");
        plan=operationPlan(10000,"10 тысяч","ADD");stallBody=true;
        var draft=engine.fork();var cancelled=new CountDownLatch(1);
        var task=executor.submit(()->{try{draft.reply("Добавь 10 тысяч");}catch(CancellationException ex){cancelled.countDown();}});
        assertTrue(entered.await(2,TimeUnit.SECONDS));task.cancel(true);assertTrue(cancelled.await(2,TimeUnit.SECONDS));
        assertEquals(new BigDecimal("100000"),engine.summary().budget());
        stallBody=false;release.countDown();var retry=engine.fork();retry.reply("Добавь 10 тысяч");
        engine.commitConversation(retry);engine.commitConversation(retry);
        assertEquals(new BigDecimal("110000"),engine.summary().budget());
        var nextAccount=engine();assertThrows(IllegalArgumentException.class,()->nextAccount.commitConversation(retry));
        assertNull(nextAccount.summary().budget());assertTrue(nextAccount.reply("Добавь 10 тысяч").content().isEmpty());
        assertNull(nextAccount.summary().budget());
    }
    @Test void completedDeltaCannotRestoreProductReferenceAfterCatalogRefresh() {
        var engine=engine();engine.reply("Собери ПК до 100000 рублей для игр");
        plan=operationPlan(10000,"10 тысяч","ADD");var draft=engine.fork();draft.reply("Добавь 10 тысяч");
        draft.reply("Найди Example Zephyr K");
        engine.updateCatalog(List.of(new Product(1,"Example Zephyr K","",15000,1,"","Процессоры","Example")));
        engine.commitConversation(draft);
        assertEquals(new BigDecimal("110000"),engine.summary().budget());
        assertTrue(engine.reply("Сколько он стоит?").text().contains("Нет актуальной карточки"));
        var answer=engine.reply("Найди Example Zephyr K");
        assertEquals(new BigDecimal("15000.00"),assertInstanceOf(ChatBotService.ProductsContent.class,answer.content().getFirst()).items().getFirst().price());
    }
}
