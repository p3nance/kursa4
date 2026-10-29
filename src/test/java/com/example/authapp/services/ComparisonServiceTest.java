package com.example.authapp.services;

import com.example.authapp.domain.*;
import com.example.authapp.models.Product;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static com.example.authapp.domain.ComponentSpecs.*;
import static org.junit.jupiter.api.Assertions.*;

class ComparisonServiceTest {
    private static final String SOURCE="https://example.test/verified-specifications";
    private static ComponentSpecs specs(String model,String socket,Verification verification) {
        return ComponentSpecs.builder().set(MODEL_ID,model,SOURCE,verification)
                .set(SOCKET,socket,SOURCE,verification)
                .set(MEMORY_TYPE,"DDR9-IMPORTED",SOURCE,Verification.IMPORTED).build();
    }
    private static CatalogComponent part(int id,String name,ComponentCategory category,String model,String socket) {
        return new CatalogComponent(id,name,category,BigDecimal.valueOf(1000+id),2,specs(model,socket,Verification.VERIFIED));
    }
    private static BenchmarkService.Context context(String platform) {
        return new BenchmarkService.Context("SYNTHETIC_FIXTURE","1","CPU Multi Core","Score","points",
                BenchmarkService.Direction.HIGHER_IS_BETTER,Map.of("platform",platform,"review_cohort","fixture-batch"));
    }
    private static BenchmarkService.Result result(String id,int score,String platform) {
        return new BenchmarkService.Result(id,context(platform),BigDecimal.valueOf(score),
                URI.create("https://example.test/benchmark"),null,LocalDate.of(2026,1,1));
    }
    private static LocalKnowledgeRepository.Snapshot snapshot(List<CatalogComponent> parts,BenchmarkService.Result... results) {
        return new LocalKnowledgeRepository.Snapshot(parts,new BenchmarkService(List.of(results)),List.of());
    }
    private static ComparisonService service(LocalKnowledgeRepository.Snapshot snapshot) { return new ComparisonService(snapshot,List.of()); }
    private static List<CatalogComponent> cpus() {
        return List.of(part(1,"Проверяемый процессор А",ComponentCategory.CPU,"fixture-processor-a","LGA1700"),
                part(2,"Проверяемый процессор Б",ComponentCategory.CPU,"fixture-processor-b","AM5"));
    }

    @Test void missingOneMeasurementNamesThatModelAndStillShowsVerifiedFacts() {
        var cpus=cpus();var service=service(snapshot(cpus,result("fixture-processor-a",100,"A")));
        var outcome=service.compare(cpus.get(0),cpus.get(1),false,"");
        assertEquals(ComparisonService.Status.NO_MEASUREMENTS,outcome.status());
        assertTrue(outcome.reply().lead().contains("нет проверенных измерений для Проверяемый процессор Б"));
        var card=assertInstanceOf(ChatBotService.SpecificationsContent.class,outcome.reply().content().getFirst());
        var socket=card.rows().stream().filter(r->r.label().equals("Сокет")).findFirst().orElseThrow();
        assertEquals("LGA1700",socket.left());assertEquals("AM5",socket.right());
        var memory=card.rows().stream().filter(r->r.label().contains("оперативной памяти")).findFirst().orElseThrow();
        assertEquals("Не подтверждено",memory.left());assertEquals("Не подтверждено",memory.right());
        assertFalse(outcome.reply().text().contains("DDR9-IMPORTED"));
        assertFalse(card.sources().isEmpty());
        assertEquals(2,card.sources().size(),"One web specification source per model, not one repeated link per field");
        assertFalse(outcome.reply().text().contains("%"));
        assertTrue(outcome.reply().suggestions().contains("Показать модели с общим тестом"));
        assertTrue(outcome.reply().text().contains("Цена и наличие из каталога"));
        assertTrue(card.rows().stream().noneMatch(r->r.label().contains("Цена")),"Catalog data is presented separately from verified specifications");
    }

    @Test void measurementsOnDifferentPlatformsExplainTheMismatchInsteadOfClaimingMissingModels() {
        var cpus=cpus();var service=service(snapshot(cpus,result("fixture-processor-a",100,"Intel"),result("fixture-processor-b",120,"AMD")));
        var outcome=service.compare(cpus.get(0),cpus.get(1),false,"");
        assertEquals(ComparisonService.Status.NO_COMMON_CONTEXT,outcome.status());
        assertTrue(outcome.reply().lead().contains("есть измерения"));
        assertTrue(outcome.reply().lead().contains("разные стенды, настройки или серии измерений"));
        assertInstanceOf(ChatBotService.SpecificationsContent.class,outcome.reply().content().getFirst());
        assertFalse(outcome.reply().text().contains("%"));
    }

    @Test void comparableMeasurementsHaveCatalogNamesAndHonestPercentDirection() {
        var cpus=cpus();var service=service(snapshot(cpus,result("fixture-processor-a",100,"same"),result("fixture-processor-b",125,"same")));
        var outcome=service.compare(cpus.get(0),cpus.get(1),false,"");
        assertEquals(ComparisonService.Status.COMPARABLE,outcome.status());assertEquals(1,outcome.reply().content().size());
        var card=assertInstanceOf(ChatBotService.BenchmarksContent.class,outcome.reply().content().getFirst());
        assertEquals("Проверяемый процессор Б",card.modelNames().get("fixture-processor-b"));
        assertTrue(outcome.reply().text().contains("Разница значений: -20.00 %"));
        assertTrue(outcome.reply().text().contains("Лучший результат в этом тесте: Проверяемый процессор Б"));
        assertFalse(outcome.reply().text().contains("fixture-processor-a"));
        assertFalse(outcome.reply().text().contains("review_cohort"));
        assertFalse(outcome.reply().text().contains("fixture-batch"));
        assertTrue(outcome.reply().text().contains("опубликовано 2026-01-01; точная дата измерения не указана"));
    }

    @Test void mismatchExplainsTheClosestSharedModeInsteadOfCollectingUnrelatedModeDifferences() {
        var cpus=cpus();
        var a=result("fixture-processor-a",100,"Intel");var b=result("fixture-processor-b",125,"AMD");
        var contextA=new BenchmarkService.Context(a.context().suite(),a.context().version(),"CPU Single Core",a.context().metric(),
                a.context().unit(),a.context().direction(),a.context().conditions());
        var contextB=new BenchmarkService.Context(b.context().suite(),b.context().version(),"CPU Single Core",b.context().metric(),
                b.context().unit(),b.context().direction(),b.context().conditions());
        var service=service(snapshot(cpus,a,b,new BenchmarkService.Result(a.modelId(),contextA,BigDecimal.TEN,a.source(),a.measuredAt(),a.publishedAt()),
                new BenchmarkService.Result(b.modelId(),contextB,BigDecimal.TEN,b.source(),b.measuredAt(),b.publishedAt())));
        var lead=service.compare(cpus.get(0),cpus.get(1),false,"").reply().lead();
        assertTrue(lead.contains("разные стенды, настройки или серии измерений"));
        assertFalse(lead.contains("разные режимы или метрики"),"Both models have the same modes; the platform is the actual mismatch");
    }

    @Test void requestForSpecificationsDoesNotForceBenchmarkCards() {
        var cpus=cpus();var service=service(snapshot(cpus,result("fixture-processor-a",100,"same"),result("fixture-processor-b",125,"same")));
        var outcome=service.compare(cpus.get(0),cpus.get(1),true,"");
        assertInstanceOf(ChatBotService.SpecificationsContent.class,outcome.reply().content().getFirst());
        assertTrue(outcome.reply().lead().startsWith("Сравниваю проверенные характеристики"));
        assertFalse(outcome.reply().text().contains("%"));
    }

    @Test void differentCategoriesDoNotReceiveANonsenseSpeedOrSpecificationTable() {
        var cpu=cpus().getFirst();var gpu=part(3,"Видеокарта",ComponentCategory.GPU,"gpu","ignored");
        var outcome=service(snapshot(List.of(cpu,gpu))).compare(cpu,gpu,false,"");
        assertEquals(ComparisonService.Status.DIFFERENT_CATEGORIES,outcome.status());
        assertTrue(outcome.reply().content().isEmpty());
        assertTrue(outcome.reply().text().contains("одной категории"));
        assertFalse(outcome.reply().text().contains("%"));
    }

    @Test void aGamingGoalDoesNotTurnRenderingScoresIntoAGamingRecommendation() {
        var cpus=cpus();var service=service(snapshot(cpus,result("fixture-processor-a",100,"same"),result("fixture-processor-b",125,"same")));
        var reply=service.compare(cpus.get(0),cpus.get(1),false,"для игр").reply();
        assertTrue(reply.lead().contains("Тесты рендеринга не определяют выбор для игр"));
        assertTrue(reply.lead().contains("В какие игры и с какой видеокартой"));
        assertFalse(reply.lead().contains("лучше для игр"));
    }

    @Test void importedModelIdentityCannotPullBenchmarksForAnotherModel() {
        var imported=new CatalogComponent(3,"Импортированная модель",ComponentCategory.CPU,BigDecimal.ONE,1,
                specs("fixture-processor-a","AM5",Verification.IMPORTED));
        var cpu=cpus().get(1);
        var outcome=service(snapshot(List.of(imported,cpu),result("fixture-processor-a",100,"same"),result("fixture-processor-b",125,"same")))
                .compare(imported,cpu,false,"");
        assertEquals(ComparisonService.Status.NO_MEASUREMENTS,outcome.status());
    }

    @Test void helpAndSuggestionsAreBuiltFromActualComparableCatalogPairs() {
        var cpu=cpus();var gpuA=part(3,"Видеокарта А",ComponentCategory.GPU,"gpu-a","ignored");
        var gpuB=part(4,"Видеокарта Б",ComponentCategory.GPU,"gpu-b","ignored");
        var parts=List.of(cpu.get(0),cpu.get(1),gpuA,gpuB);
        var service=service(snapshot(parts,result("fixture-processor-a",100,"same"),result("fixture-processor-b",125,"same"),
                result("gpu-a",100,"gpu"),result("gpu-b",125,"gpu")));
        var suggestions=service.comparablePairSuggestions(null);
        assertEquals(2,suggestions.size());
        assertTrue(suggestions.contains("Сравни Видеокарта А и Видеокарта Б"));
        assertEquals(List.of("Сравни Проверяемый процессор А и Проверяемый процессор Б"),service.comparablePairSuggestions(ComponentCategory.CPU));
        var empty=service(snapshot(cpu));
        assertTrue(empty.comparablePairSuggestions(null).isEmpty());
        assertFalse(empty.help().text().contains("14700K"));
        assertFalse(empty.help().text().contains("Нужны название модели, результат"));
        assertTrue(empty.help().text().contains("нет пары с общим проверенным тестом"));
    }

    @Test void humanReadableConditionsKeepUnknownsAndNeverInventAReplacementPolicy() {
        var context=new BenchmarkService.Context("fixture","1","GPU rendering","score","points",BenchmarkService.Direction.HIGHER_IS_BETTER,
                Map.of("gpuDriverVersion","not_reported","renderBackend","Redshift (chart heading); low-level backend not_reported",
                        "power_policy","Custom laboratory profile at 80 W","review_cohort","fixture-id"));
        var lines=BenchmarkPresentation.conditionLines(context);
        assertTrue(lines.contains("Драйвер видеокарты: не опубликовано"));
        assertTrue(lines.contains("Настройки питания: Custom laboratory profile at 80 W"));
        assertTrue(lines.stream().noneMatch(s->s.contains("Puget") || s.contains("review_cohort") || s.contains("renderBackend") || s.contains("fixture-id")));
        assertEquals("Рендеринг на видеокарте",BenchmarkPresentation.workload(context));
        assertEquals("баллов",BenchmarkPresentation.unit(context.unit()));
    }

    @Test void realCatalogRegressionShows14900kAnd7800x3dFactsAndKeepsActualGpuComparisons() throws Exception {
        List<Product> products=new ArrayList<>();
        try(var reader=Files.newBufferedReader(Path.of("src/test/resources/knowledge/catalog-reference.json"))) {
            for(var element:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("products")) {
                var row=element.getAsJsonObject();
                products.add(new Product(row.get("id").getAsInt(),row.get("name").getAsString(),row.get("description").getAsString(),
                        row.get("price").getAsDouble(),row.get("stock").getAsInt(),"",row.get("category").getAsString(),row.get("manufacturer").getAsString()));
            }
        }
        String previous=System.getProperty("techstore.knowledge.dir");LocalKnowledgeRepository.Snapshot snapshot;
        try {System.setProperty("techstore.knowledge.dir",Path.of("src/main/resources/knowledge").toAbsolutePath().toString());snapshot=new LocalKnowledgeRepository().load(products);}
        finally {if(previous==null)System.clearProperty("techstore.knowledge.dir");else System.setProperty("techstore.knowledge.dir",previous);}
        assertTrue(snapshot.warnings().isEmpty());
        var byId=new HashMap<Integer,CatalogComponent>();snapshot.components().forEach(c->byId.put(c.productId(),c));
        var service=new ComparisonService(snapshot,products);
        var missing=service.compare(byId.get(4),byId.get(8),false,"");
        assertEquals(ComparisonService.Status.NO_MEASUREMENTS,missing.status());
        assertTrue(missing.reply().lead().contains("нет проверенных измерений для AMD Ryzen 7 7800X3D"));
        var spec=assertInstanceOf(ChatBotService.SpecificationsContent.class,missing.reply().content().getFirst());
        assertTrue(spec.rows().stream().anyMatch(r->r.label().equals("Сокет") && r.left().equals("LGA1700") && r.right().equals("AM5")));
        var compared=service.compare(byId.get(14),byId.get(13),false,"");
        assertEquals(ComparisonService.Status.COMPARABLE,compared.status());
        var benchmark=assertInstanceOf(ChatBotService.BenchmarksContent.class,compared.reply().content().getFirst());
        assertEquals(2,benchmark.comparisons().size());
        assertTrue(compared.reply().text().contains("11.63 %"));assertTrue(compared.reply().text().contains("19.46 %"));
    }
}
