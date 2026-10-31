package com.example.authapp.services;

import com.example.authapp.models.Product;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real user phrases and multi-turn state against the actual local catalog, without network. */
class ComparisonConversationTest {
    private static List<Product> catalog;
    private String previous;
    @BeforeAll static void readCatalog() throws Exception {
        var list=new ArrayList<Product>();
        try(var reader=Files.newBufferedReader(Path.of("src/test/resources/knowledge/catalog-reference.json"))) {
            for(var item:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("products")) {
                var p=item.getAsJsonObject();
                list.add(new Product(p.get("id").getAsInt(),p.get("name").getAsString(),p.get("description").getAsString(),
                        p.get("price").getAsDouble(),p.get("stock").getAsInt(),"",p.get("category").getAsString(),p.get("manufacturer").getAsString()));
            }
        }
        catalog=List.copyOf(list);
    }
    @BeforeEach void localData() {previous=System.getProperty("techstore.knowledge.dir");System.setProperty("techstore.knowledge.dir",Path.of("src/main/resources/knowledge").toAbsolutePath().toString());}
    @AfterEach void restore() {if(previous==null)System.clearProperty("techstore.knowledge.dir");else System.setProperty("techstore.knowledge.dir",previous);Thread.interrupted();}
    private ChatBotService bot() {return new ChatBotService(catalog);}
    private static ChatBotService.BenchmarksContent benchmarks(ChatBotService.Reply reply) {
        return assertInstanceOf(ChatBotService.BenchmarksContent.class,reply.content().getFirst(),reply.text());
    }
    private static ChatBotService.SpecificationsContent specs(ChatBotService.Reply reply) {
        return assertInstanceOf(ChatBotService.SpecificationsContent.class,reply.content().getFirst(),reply.text());
    }
    @Test void screenshotPhraseResolvesBothModelsAndShowsTheirPublishedPlatformComparison() {
        var reply=bot().reply("сравни i9-14900k и ryzen 7 7800x3d");var card=benchmarks(reply);
        assertEquals(1,card.comparisons().size());var compared=card.comparisons().getFirst();
        assertEquals("Intel Core i9-14900K",card.modelNames().get(compared.left().modelId()));
        assertEquals("AMD Ryzen 7 7800X3D",card.modelNames().get(compared.right().modelId()));
        assertEquals(0,new java.math.BigDecimal("384.5").compareTo(compared.left().value()));
        assertEquals(0,new java.math.BigDecimal("438.3").compareTo(compared.right().value()));
        assertTrue(reply.lead().contains("двух тестовых платформ"));assertTrue(reply.text().contains("-12.27 %"));
        assertTrue(reply.text().contains("распределение памяти по CPU"));
    }
    @Test void conversationPhrasesAndCompactPairsUseTheSameRealMeasurements() {
        for(String phrase:List.of("14700K vs 14600K","можешь сравнить i7-14700k и i5-14600k?",
                "чем отличается 14700K от 14600K","что лучше: 14700K или 14600K?",
                "сравнение 14700K и 14600K","14700K 14600K","сравни, пожалуйста, 14700K и 14600K")) {
            var reply=bot().reply(phrase);assertEquals(2,benchmarks(reply).comparisons().size(),phrase);
            assertTrue(reply.text().contains("Intel Core i7-14700K"));assertFalse(reply.text().contains("intel-core-i7"));
        }
    }
    @Test void gpuSuffixesRemainSeparateEvenInCompactQueries() {
        var card=benchmarks(bot().reply("RTX4070Super против 4070"));
        assertEquals(4,card.comparisons().size());
        assertTrue(card.comparisons().stream().allMatch(c->c.left().modelId().endsWith("4070 SUPER") && c.right().modelId().endsWith("4070")));
    }
    @Test void missingMeasurementsAndDifferentPlatformsHaveDistinctUsefulAnswers() {
        var missing=bot().reply("4060 Ti 16GB vs 4070");specs(missing);assertTrue(missing.lead().contains("нет проверенных измерений"));
        var otherPlatform=bot().reply("14600K vs Ryzen7600X");specs(otherPlatform);
        assertTrue(otherPlatform.lead().contains("есть измерения"));assertTrue(otherPlatform.lead().contains("нет общего теста"));
        assertFalse(otherPlatform.text().contains("%"));
    }
    @Test void unknownNumericModelIsNotReplacedByTheNearestProduct() {
        var reply=bot().reply("14999K vs 14600K");assertTrue(reply.content().isEmpty());
        assertTrue(reply.text().contains("14999K"));assertTrue(reply.text().contains("не найдена"));assertFalse(reply.text().contains("%"));
    }
    @Test void possibleTypoRequiresConfirmationAndPreservesTheOtherModel() {
        var bot=bot();var question=bot.reply("сравни ryze 7800X3D и 14900K");
        assertTrue(question.content().isEmpty());assertFalse(question.suggestions().isEmpty());
        assertTrue(question.text().contains("Подтвердите"));
        var card=benchmarks(bot.reply(question.suggestions().getFirst()));var compared=card.comparisons().getFirst();
        assertEquals("AMD Ryzen 7 7800X3D",card.modelNames().get(compared.left().modelId()));
        assertEquals("Intel Core i9-14900K",card.modelNames().get(compared.right().modelId()));
    }
    @Test void aSingleModelCanBeCompletedInTheNextTurn() {
        var bot=bot();assertTrue(bot.reply("сравни 14700K").text().contains("вторую"));
        assertEquals(2,benchmarks(bot.reply("14600K")).comparisons().size());
    }
    @Test void ambiguousVramChoiceDoesNotSilentlyChooseAnExecutionVariant() {
        var bot=new ChatBotService(List.of(new Product(1001,"NVIDIA GeForce RTX 4060 Ti 8GB","",100,1,"","Видеокарты","NVIDIA"),
                new Product(1002,"NVIDIA GeForce RTX 4060 Ti 16GB","",200,1,"","Видеокарты","NVIDIA"),
                new Product(1003,"NVIDIA GeForce RTX 4070 12GB","",300,1,"","Видеокарты","NVIDIA")));
        var question=bot.reply("4060 Ti vs 4070");assertTrue(question.text().contains("несколько вариантов"));
        assertEquals(2,question.suggestions().size());
        var card=specs(bot.reply("16 гб"));assertTrue(card.left().name().endsWith("16GB"));assertTrue(card.right().name().contains("4070"));
    }
    @Test void gamingFollowUpKeepsThePairAndDoesNotDeclareAGamingWinner() {
        var bot=bot();bot.reply("14900K vs 7800X3D");var answer=bot.reply("а для игр?");
        var card=benchmarks(answer);assertEquals(1,card.comparisons().size());var measured=card.comparisons().getFirst();
        assertTrue(card.modelNames().get(measured.left().modelId()).contains("14900K"));
        assertTrue(card.modelNames().get(measured.right().modelId()).contains("7800X3D"));
        assertEquals("F1",measured.left().context().suite());assertTrue(answer.text().contains("В какие игры"));
        assertTrue(answer.text().contains("двух тестовых платформ"));assertTrue(answer.text().contains("не прогноз FPS готовой сборки"));
    }
    @Test void commonTestActionIsNotHijackedByCatalogSearch() {
        var bot=bot();bot.reply("14900K vs 7800X3D");var examples=bot.reply("Показать модели с общим тестом");
        assertFalse(examples.suggestions().isEmpty());assertTrue(examples.content().isEmpty());
        assertFalse(examples.text().contains("ничего не найдено"));
        benchmarks(bot.reply(examples.suggestions().getFirst()));
    }
    @Test void explicitSpecificationsAndModeSwitchingKeepTheCorrectPair() {
        var bot=bot();var card=specs(bot.reply("сравни характеристики 14700K и 14600K"));
        assertTrue(card.left().name().contains("14700K"));assertTrue(card.right().name().contains("14600K"));
        assertEquals(2,benchmarks(bot.reply("Сравнить по бенчмаркам")).comparisons().size());
        specs(bot.reply("Покажи характеристики"));
        specs(bot().reply("покажи характеристики 14900K и 7800X3D"));
    }
    @Test void anExplicitNewPairWithPurposeReplacesPreviousModels() {
        var bot=bot();bot.reply("14900K vs 14600K");
        var card=benchmarks(bot.reply("4070 super 4070 для игр"));
        assertTrue(card.comparisons().getFirst().left().modelId().contains("4070"));
    }
    @Test void aSingleNewModelWithPurposeAsksWhichPositionToReplace() {
        var bot=bot();bot.reply("14900K vs 14600K");var question=bot.reply("а 14700K для работы");
        assertTrue(question.content().isEmpty());assertEquals(2,question.suggestions().size());
        assertTrue(benchmarks(bot.reply(question.suggestions().getFirst())).comparisons().getFirst().left().modelId().contains("14700k"));
    }
    @Test void newExplicitComparisonOverridesPendingReplacementTarget() {
        var bot=bot();bot.reply("14900K vs 14600K");bot.reply("а вместо него 14700K");
        assertTrue(bot.reply("сравни 4070 super").text().contains("вторую"));
        assertTrue(benchmarks(bot.reply("4070")).comparisons().getFirst().left().modelId().contains("4070"));
    }
    @Test void unknownNewModelWithPurposeDoesNotSilentlyUseThePreviousPair() {
        var bot=bot();bot.reply("14900K vs 14600K");var reply=bot.reply("а RTX 9999 для игр?");
        assertTrue(reply.content().isEmpty());assertTrue(reply.text().contains("Не распознал новую модель"));
        assertFalse(reply.text().contains("%"));
    }
    @Test void requestedWorkloadDetailsDoNotReplaceACpuWithTheMentionedGpu() {
        var bot=bot();bot.reply("14900K vs 7800X3D");bot.reply("а для игр?");
        var details=bot.reply("Cyberpunk 2077, RTX 4070, 1440p");
        assertTrue(details.text().contains("Учёл уточнение"));assertFalse(details.text().contains("%"));
        var followUp=bot.reply("Сравнить характеристики");var card=specs(followUp);
        assertTrue(card.left().name().contains("14900K"));assertTrue(card.right().name().contains("7800X3D"));
        assertTrue(followUp.text().contains("Cyberpunk"));assertFalse(followUp.text().contains("В какие игры"));
    }
    @Test void explicitReplacementChangesOnlyTheSpecifiedPosition() {
        var bot=bot();bot.reply("14900K vs 14600K");var card=benchmarks(bot.reply("а вместо первого 14700K?"));
        assertTrue(card.comparisons().stream().allMatch(c->c.left().modelId().contains("14700k") && c.right().modelId().contains("14600k")));
    }
    @Test void ambiguousPronounAsksForReplacementTargetBeforeChangingThePair() {
        var bot=bot();bot.reply("14900K vs 14600K");var question=bot.reply("а вместо него 14700K?");
        assertTrue(question.content().isEmpty());assertEquals(2,question.suggestions().size());
        var card=benchmarks(bot.reply(question.suggestions().getFirst()));
        assertTrue(card.comparisons().getFirst().left().modelId().contains("14700k"));
    }
    @Test void secondReferenceAsksForAModelAndUsesTheCorrectAnchor() {
        var bot=bot();bot.reply("14900K vs 14600K");assertTrue(bot.reply("сравни со вторым").text().contains("14600K"));
        var card=benchmarks(bot.reply("14700K"));assertTrue(card.comparisons().getFirst().right().modelId().contains("14600k"));
    }
    @Test void newIntentAndResetDoNotReuseComparisonOrPendingChoices() {
        var bot=bot();bot.reply("14900K vs 7800X3D");bot.reply("объясни сокет");
        assertTrue(bot.reply("а для игр?").content().isEmpty());
        bot.reply("14900K vs 14600K");bot.reply("новый диалог");assertTrue(bot.reply("сравни со вторым").text().contains("Назовите обе модели"));
    }
    @Test void ordinarySearchAndBuildAnswersAreNotHijackedByComparisonParsing() {
        var bot=bot();var found=bot.reply("можешь найти 14900K?");
        assertInstanceOf(ChatBotService.ProductsContent.class,found.content().getFirst());
        bot.reply("собери ПК");assertTrue(bot.reply("60000").text().contains("каких задач"));
        assertFalse(bot.reply("Для учёбы и офиса").text().contains("модель"));
    }
    @Test void categorySearchAndNaturalModelNounsResolveActualCatalogProducts() {
        var bot=bot();
        for(String query:List.of("есть ли у вас CPU?","найди видеокарту RTX4070Super","покажи процессор 14900K"))
            assertInstanceOf(ChatBotService.ProductsContent.class,bot.reply(query).content().getFirst(),query);
        assertTrue(bot.reply("найди процессор 99999K").text().contains("ничего не найдено"));
    }
    @Test void askingWhatIsBetterForGamesWithoutModelsAsksForThePair() {
        var answer=bot().reply("что лучше для игр?");assertTrue(answer.content().isEmpty());
        assertTrue(answer.text().contains("две модели"));assertFalse(answer.text().contains("не найдена"));
    }
    @Test void commonTestSuggestionsComeFromActualCoverage() {
        var bot=bot();var help=bot.reply("Как сравнить комплектующие по бенчмаркам?");
        assertFalse(help.text().contains("Нужны название модели, результат"));assertFalse(help.suggestions().isEmpty());
        for(var suggestion:help.suggestions())assertFalse(benchmarks(bot().reply(suggestion)).comparisons().isEmpty());
    }
    @Test void discardedDraftCannotChangeTheCommittedConversation() {
        var bot=bot();bot.reply("14900K vs 14600K");var draft=bot.fork();draft.reply("4070 super vs 4070");
        assertInstanceOf(ChatBotService.BenchmarksContent.class,bot.reply("а для работы?").content().getFirst());
        assertTrue(benchmarks(bot.reply("Сравни 14700K и 14600K")).comparisons().getFirst().left().modelId().contains("14700k"));
        bot.commitConversation(draft);var next=benchmarks(bot.reply("а для работы?"));
        assertTrue(next.comparisons().getFirst().left().modelId().contains("4070"));
    }
    @Test void catalogRefreshDoesNotAcceptOldIdsReusedForAnotherModel() {
        var bot=bot();bot.reply("14900K vs 14600K");
        var changed=new ArrayList<Product>();for(var p:catalog)changed.add(ChatBotService.ProductInfo.from(p).toProduct());
        changed.stream().filter(p->p.getId()==4).findFirst().orElseThrow().setName("Different CPU");bot.updateCatalog(changed);
        var answer=bot.reply("а для игр?");assertTrue(answer.content().isEmpty());assertTrue(answer.text().contains("Каталог изменился"));
    }
    @Test void interruptedCallDoesNotMutateCommittedState() {
        var bot=bot();bot.reply("14900K vs 14600K");
        try {Thread.currentThread().interrupt();assertThrows(java.util.concurrent.CancellationException.class,()->bot.reply("4070 super vs 4070"));}
        finally {Thread.interrupted();}
        assertTrue(benchmarks(bot.reply("а для работы?")).comparisons().getFirst().left().modelId().contains("14900k"));
    }

    @Test void aGamingFollowUpFiltersBothCpuAndGpuPairsToTheirActualGameMeasurements() {
        for(var pair:List.of(new String[]{"7600X vs 7900X","F1"},new String[]{"4070 SUPER vs 4070","Starfield"})) {
            var bot=bot();boolean cpu=pair[0].contains("7600X");
            assertEquals(cpu?3:4,benchmarks(bot.reply(pair[0])).comparisons().size(),pair[0]);
            var reply=bot.reply("а для игр?");var card=benchmarks(reply);
            assertEquals(cpu?1:2,card.comparisons().size(),pair[0]);assertEquals(pair[1],card.comparisons().getFirst().left().context().suite());
            assertEquals("FPS",card.comparisons().getFirst().left().context().unit());
            assertFalse(reply.text().contains("Тесты рендеринга не определяют"));
        }
    }
    @Test void knownBareGameDetailsUseThePublishedGameWithoutClaimingThereAreNoMeasurements() {
        var bot=bot();bot.reply("4070 SUPER vs 4070");bot.reply("Для игр");
        var reply=bot.reply("Starfield, 4K");var card=benchmarks(reply);
        assertEquals(1,card.comparisons().size());assertEquals("Starfield",card.comparisons().getFirst().left().context().suite());
        assertTrue(card.comparisons().getFirst().left().modelId().endsWith("4070 SUPER"));assertTrue(card.comparisons().getFirst().right().modelId().endsWith("4070"));
        assertTrue(reply.text().contains("Учёл уточнение"));assertFalse(reply.text().contains("нет игровых измерений"));
        assertTrue(reply.text().contains("не подтверждает эквивалентность вашего ПК"));
        var cpu=bot();cpu.reply("7600X vs 7900X");cpu.reply("Для игр");
        var f1=benchmarks(cpu.reply("F1 24, 1080p"));assertEquals(1,f1.comparisons().size());assertEquals("F1",f1.comparisons().getFirst().left().context().suite());
    }
    @Test void unknownGamesAndUnmeasuredResolutionsKeepThePairWithoutInheritingAvailableFps() {
        for(String details:List.of("Cyberpunk 2077, RTX 4070, 1440p","Starfield, 1080p","а Starfield, RTX 4090, 4K")) {
            var bot=bot();bot.reply("4070 SUPER vs 4070");bot.reply("Для игр");
            var reply=bot.reply(details);assertTrue(reply.text().contains("Учёл уточнение"),details);
            assertTrue(reply.content().isEmpty(),details);assertFalse(reply.text().contains("%"),details);
            var card=specs(bot.reply("Сравнить характеристики"));
            assertTrue(card.left().name().contains("4070 SUPER"),details);assertTrue(card.right().name().contains("4070"),details);
        }
    }
    @Test void mentioningAGpuInCpuGamingDetailsDoesNotReplaceACpuOrPredictThatGpuScenario() {
        for(String details:List.of("а F1 24, RTX 4070, 1080p","F1 24, RTX 4090, 1080p","F1 24, 1440p")) {
            var bot=bot();bot.reply("7600X vs 7900X");bot.reply("Для игр");
            var reply=bot.reply(details);assertTrue(reply.text().contains("Учёл уточнение"),details);
            assertTrue(reply.content().isEmpty(),details);assertFalse(reply.text().contains("%"),details);
            assertFalse(reply.text().contains("Для этой пары общих игровых измерений пока нет"),details);
            var card=specs(bot.reply("Сравнить характеристики"));assertTrue(card.left().name().contains("7600X"));assertTrue(card.right().name().contains("7900X"));
        }
    }
    @Test void gamingDetailsAreIsolatedInDraftsAndSurviveOnlyAnExplicitCommit() {
        var bot=bot();bot.reply("4070 SUPER vs 4070");bot.reply("Для игр");var draft=bot.fork();
        benchmarks(draft.reply("Starfield, 4K"));
        assertTrue(bot.reply("Cyberpunk 2077, 1440p").content().isEmpty());
        bot.commitConversation(draft);assertEquals("Starfield",benchmarks(bot.reply("Сравнить по бенчмаркам")).comparisons().getFirst().left().context().suite());
        bot.reply("Новый диалог");assertTrue(bot.reply("Starfield, 4K").content().isEmpty());
    }

    @Test void anInitialGamingRequestKeepsItsUnknownGameOrUnsupportedResolutionTail() {
        for(String suffix:List.of("для игр в Cyberpunk 2077 на 1440p","для игр на 1440p","в играх Starfield на 1080p","для игр в Starfield 4K с RTX 4090")) {
            var bot=bot();var reply=bot.reply("Сравни 4070 SUPER и 4070 "+suffix);
            assertTrue(reply.content().isEmpty(),suffix);assertFalse(reply.text().contains("%"),suffix);
            var followUp=bot.reply("Сравнить характеристики");var card=specs(followUp);
            assertTrue(card.left().name().contains("4070 SUPER"));assertTrue(card.right().name().contains("4070"));
            assertTrue(followUp.text().contains("1440p") || followUp.text().contains("1080p") || followUp.text().contains("RTX 4090"),suffix);
        }
    }
    @Test void supportedInitialGameTailsUseOnlyTheirActualGamingContexts() {
        for(var request:List.of(new String[]{"Сравни 4070 SUPER и 4070 для игр в Starfield 4K","Starfield"},
                new String[]{"Сравни 7600X и 7900X в играх F1 24 на 1080p","F1"})) {
            var card=benchmarks(bot().reply(request[0]));assertEquals(1,card.comparisons().size(),request[0]);
            assertEquals(request[1],card.comparisons().getFirst().left().context().suite());
        }
        var generic=bot().reply("Сравни 4070 SUPER и 4070 для игр");
        assertEquals(2,benchmarks(generic).comparisons().size());assertTrue(generic.text().contains("В какие игры"));
    }
    @Test void goalFirstFollowUpsRetainTheOriginalPairAndApplyTheirInlineScenario() {
        var known=bot();known.reply("4070 SUPER vs 4070");var starfield=known.reply("а для игр в Starfield 4K");
        assertEquals("Starfield",benchmarks(starfield).comparisons().getFirst().left().context().suite());
        assertTrue(starfield.text().contains("Учёл уточнение"));
        for(String followUp:List.of("а для игр в Cyberpunk 2077 на 1440p","а для игр на 1440p","а для игр в Starfield 4K с RTX 4090")) {
            var bot=bot();bot.reply("4070 SUPER vs 4070");var reply=bot.reply(followUp);
            assertTrue(reply.content().isEmpty(),followUp);assertFalse(reply.text().contains("%"),followUp);
            assertTrue(reply.text().contains("Учёл уточнение"),followUp);
            var card=specs(bot.reply("Сравнить характеристики"));
            assertTrue(card.left().name().contains("4070 SUPER"));assertTrue(card.right().name().contains("4070"));
        }
    }
    @Test void anInlineGamingTailSurvivesModelClarificationAndACommittedDraft() {
        var bot=bot();assertTrue(bot.reply("Сравни 4070 SUPER для игр в Starfield 4K").text().contains("вторую"));
        assertEquals("Starfield",benchmarks(bot.reply("4070")).comparisons().getFirst().left().context().suite());
        var draft=bot.fork();assertTrue(draft.reply("а для игр в Cyberpunk 2077 на 1440p").content().isEmpty());
        assertFalse(benchmarks(bot.reply("Сравнить по бенчмаркам")).comparisons().isEmpty());
        bot.commitConversation(draft);assertTrue(bot.reply("Сравнить по бенчмаркам").content().isEmpty());
        var generic=bot.reply("а для игр?");assertEquals(2,benchmarks(generic).comparisons().size());
        assertTrue(generic.text().contains("В какие игры"));
        bot.reply("Новый диалог");assertTrue(bot.reply("а для игр в Starfield 4K").content().isEmpty());
    }
    @Test void explicitModelPairsAcceptBoundedCategoryAndComparisonModeWrappers() {
        for(String phrase:List.of("сравни процессоры 14700K и 14600K", "сравни CPU 14700K и процессор 14600K по бенчмаркам",
                "сравни видеокарты 4070 super и 4070 по бенчмаркам")) {
            var card=benchmarks(bot().reply(phrase));assertFalse(card.comparisons().isEmpty(),phrase);
        }
        var card=specs(bot().reply("сравни по характеристикам 14700K и 14600K"));
        assertTrue(card.left().name().contains("14700K"));assertTrue(card.right().name().contains("14600K"));
        card=specs(bot().reply("сравни 14700K и 14600K по цене"));
        assertTrue(card.left().name().contains("14700K"));assertTrue(card.right().name().contains("14600K"));
    }
    @Test void wrappersNeverEraseUnknownIdentityVariantsOrAThirdModel() {
        for(String phrase:List.of("сравни процессоры 14999K и 14600K по бенчмаркам",
                "сравни видеокарты 4070 SUPER Ti и 4070 по бенчмаркам",
                "сравни видеокарты 4070 SUPER 16GB и 4070 по бенчмаркам",
                "сравни процессоры 14900K и 14700K и 14600K по бенчмаркам")) {
            var reply=bot().reply(phrase);assertTrue(reply.content().isEmpty(),phrase);assertFalse(reply.text().contains("%"),phrase);
        }
        var bot=new ChatBotService(List.of(new Product(1001,"NVIDIA GeForce RTX 4060 Ti 8GB","",100,1,"","Видеокарты","NVIDIA"),
                new Product(1002,"NVIDIA GeForce RTX 4060 Ti 16GB","",200,1,"","Видеокарты","NVIDIA"),
                new Product(1003,"NVIDIA GeForce RTX 4070 12GB","",300,1,"","Видеокарты","NVIDIA")));
        assertEquals(2,bot.reply("сравни видеокарты 4060 Ti и 4070 по бенчмаркам").suggestions().size());
        var card=specs(bot.reply("16 гб"));assertTrue(card.left().name().endsWith("16GB"));assertTrue(card.right().name().contains("4070"));
    }
    @Test void anInlineWorkScenarioIsRetainedWithoutPredictingNamedProgramsOrPlatforms() {
        var bot=bot();var reply=bot.reply("сравни процессоры 14700K и 14600K для работы в Blender с RTX 4090");
        assertTrue(reply.text().contains("Учёл уточнение"));assertTrue(reply.content().isEmpty());assertFalse(reply.text().contains("%"));
        var followUp=bot.reply("Сравнить характеристики");var card=specs(followUp);
        assertTrue(card.left().name().contains("14700K"));assertTrue(card.right().name().contains("14600K"));
        assertTrue(followUp.text().contains("Blender с RTX 4090"));
    }
    @Test void repeatedGamingScenarioQuestionsKeepThePairWithoutReusingAnotherScenariosFps() {
        var bot=bot();bot.reply("4070 SUPER vs 4070 для игр в Starfield 4K");
        for(String question:List.of("а в Starfield на 1080p?", "а Cyberpunk?", "а на 1080p?")) {
            var reply=bot.reply(question);assertTrue(reply.text().contains("Учёл уточнение"),question);
            assertTrue(reply.content().isEmpty(),question);assertFalse(reply.text().contains("%"),question);
        }
        var card=specs(bot.reply("Сравнить характеристики"));
        assertTrue(card.left().name().contains("4070 SUPER"));assertTrue(card.right().name().contains("4070"));
    }
    @Test void resolutionOnlyFollowUpsRetainTheGameAndOtherExplicitConditions() {
        var bot=bot();bot.reply("7600X vs 7900X для игр F1 24 на 1440p");
        var measured=bot.reply("а на 1080p?");assertEquals("F1",benchmarks(measured).comparisons().getFirst().left().context().suite());
        bot.reply("а в F1 24 на 1440p с RTX 4070");
        var constrained=bot.reply("а на 1080p?");assertTrue(constrained.content().isEmpty());assertFalse(constrained.text().contains("%"));
        assertTrue(constrained.text().contains("RTX 4070"));
    }
    @Test void gameScenarioHardwareRemainsAConditionButANewComparisonOverridesTheContext() {
        var bot=bot();bot.reply("7600X vs 7900X для игр F1 24 1080p");
        var answer=bot.reply("а в F1 24 с RTX 4070 и RTX 4090 на 1080p?");
        assertTrue(answer.text().contains("Учёл уточнение"));assertTrue(answer.content().isEmpty());assertFalse(answer.text().contains("%"));
        var card=specs(bot.reply("Сравнить характеристики"));assertTrue(card.left().name().contains("7600X"));assertTrue(card.right().name().contains("7900X"));
        var next=benchmarks(bot.reply("Сравни процессоры 14700K и 14600K по бенчмаркам"));
        assertTrue(next.comparisons().getFirst().left().modelId().contains("14700k"));
        assertTrue(next.comparisons().getFirst().right().modelId().contains("14600k"));
    }
    @Test void repeatedScenarioStateIsAtomicAndExplicitOtherIntentsStillClearIt() {
        var bot=bot();bot.reply("7600X vs 7900X для игр F1 24 1080p");var draft=bot.fork();
        assertTrue(draft.reply("а на 4K?").content().isEmpty());
        assertEquals("F1",benchmarks(bot.reply("Сравнить по бенчмаркам")).comparisons().getFirst().left().context().suite());
        bot.commitConversation(draft);assertTrue(bot.reply("Сравнить по бенчмаркам").content().isEmpty());
        assertEquals("F1",benchmarks(bot.reply("а на 1080p?")).comparisons().getFirst().left().context().suite());
        bot.reply("Объясни сокет");assertFalse(bot.reply("а Cyberpunk?").text().contains("Учёл уточнение"));
        bot.reply("4070 SUPER vs 4070 для игр Starfield 4K");bot.reply("Найди 4070");
        assertFalse(bot.reply("а в Starfield на 1440p?").text().contains("Учёл уточнение"));
        bot.reply("4070 SUPER vs 4070 для игр Starfield 4K");bot.reply("Новый диалог");
        assertFalse(bot.reply("а на 1080p?").text().contains("Учёл уточнение"));
    }

    @Test void changingToThePublished1440pScenarioUsesItsOwnCohortAndNumbers() {
        var bot=bot();var initial=benchmarks(bot.reply("4070 SUPER vs 4070 для игр Starfield 4K")).comparisons().getFirst();
        assertEquals(new java.math.BigDecimal("52.9"),initial.left().value());
        assertEquals(new java.math.BigDecimal("46.5"),initial.right().value());
        assertEquals(new java.math.BigDecimal("13.76"),initial.differencePercent());
        var reply=bot.reply("а в Starfield на 1440p?");var next=benchmarks(reply).comparisons().getFirst();
        assertTrue(reply.text().contains("Учёл уточнение"));
        assertEquals(new java.math.BigDecimal("86.4"),next.left().value());
        assertEquals(new java.math.BigDecimal("78.7"),next.right().value());
        assertEquals(new java.math.BigDecimal("9.78"),next.differencePercent());
        assertNotEquals(initial.left().context(),next.left().context());
        var restored=benchmarks(bot.reply("а на 4K?")).comparisons().getFirst();
        assertEquals(initial.left().context(),restored.left().context());
        assertEquals(initial.left().value(),restored.left().value());
        assertEquals(initial.right().value(),restored.right().value());
    }

}
