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
        try(var reader=Files.newBufferedReader(Path.of("knowledge/catalog-reference.json"))) {
            for(var item:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("products")) {
                var p=item.getAsJsonObject();
                list.add(new Product(p.get("id").getAsInt(),p.get("name").getAsString(),p.get("description").getAsString(),
                        p.get("price").getAsDouble(),p.get("stock").getAsInt(),"",p.get("category").getAsString(),p.get("manufacturer").getAsString()));
            }
        }
        catalog=List.copyOf(list);
    }
    @BeforeEach void localData() {previous=System.getProperty("techstore.knowledge.dir");System.setProperty("techstore.knowledge.dir",Path.of("knowledge").toAbsolutePath().toString());}
    @AfterEach void restore() {if(previous==null)System.clearProperty("techstore.knowledge.dir");else System.setProperty("techstore.knowledge.dir",previous);Thread.interrupted();}
    private ChatBotService bot() {return new ChatBotService(catalog);}
    private static ChatBotService.BenchmarksContent benchmarks(ChatBotService.Reply reply) {
        return assertInstanceOf(ChatBotService.BenchmarksContent.class,reply.content().getFirst(),reply.text());
    }
    private static ChatBotService.SpecificationsContent specs(ChatBotService.Reply reply) {
        return assertInstanceOf(ChatBotService.SpecificationsContent.class,reply.content().getFirst(),reply.text());
    }
    @Test void screenshotPhraseResolvesBothModelsAndExplainsTheActualMissingData() {
        var reply=bot().reply("сравни i9-14900k и ryzen 7 7800x3d");var card=specs(reply);
        assertEquals("Intel Core i9-14900K",card.left().name());assertEquals("AMD Ryzen 7 7800X3D",card.right().name());
        assertTrue(reply.lead().contains("нет проверенных измерений для AMD Ryzen 7 7800X3D"));
        assertFalse(reply.text().contains("%"));assertFalse(card.sources().isEmpty());
        assertTrue(card.rows().stream().anyMatch(r->r.left().equals("LGA1700") && r.right().equals("AM5")));
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
        assertEquals(2,card.comparisons().size());
        assertTrue(card.comparisons().stream().allMatch(c->c.left().modelId().endsWith("4070 SUPER") && c.right().modelId().endsWith("4070")));
    }
    @Test void missingMeasurementsAndDifferentPlatformsHaveDistinctUsefulAnswers() {
        var missing=bot().reply("4060 vs 4070");specs(missing);assertTrue(missing.lead().contains("нет проверенных измерений"));
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
        var card=specs(bot.reply(question.suggestions().getFirst()));
        assertEquals("AMD Ryzen 7 7800X3D",card.left().name());assertEquals("Intel Core i9-14900K",card.right().name());
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
        var card=specs(answer);assertTrue(card.left().name().contains("14900K"));assertTrue(card.right().name().contains("7800X3D"));
        assertTrue(answer.text().contains("В какие игры"));assertFalse(answer.text().contains("%"));
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
}
