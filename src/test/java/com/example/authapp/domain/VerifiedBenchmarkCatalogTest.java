package com.example.authapp.domain;

import com.example.authapp.models.Product;
import com.example.authapp.services.BenchmarkService;
import com.example.authapp.services.ChatBotService;
import com.example.authapp.services.LocalKnowledgeRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.example.authapp.domain.ComponentSpecs.MODEL_ID;
import static org.junit.jupiter.api.Assertions.*;

/** Real published scores and catalog identities, read entirely from local files. */
class VerifiedBenchmarkCatalogTest {
    private static final Path KNOWLEDGE=Path.of("src/main/resources/knowledge").toAbsolutePath();
    private static List<Product> products;
    private static Map<Integer,Product> productsById;
    private static Map<Integer,String> modelByProduct;
    private static List<BenchmarkService.Result> results;
    private static LocalKnowledgeRepository.Snapshot knowledge;

    @BeforeAll static void loadPublishedLocalData() throws IOException {
        var catalog=new ArrayList<Product>();
        for(var item:json(Path.of("src/test/resources/knowledge/catalog-reference.json")).getAsJsonArray("products")) {
            var row=item.getAsJsonObject();
            catalog.add(new Product(row.get("id").getAsInt(),row.get("name").getAsString(),
                    string(row,"description"),row.get("price").getAsDouble(),row.get("stock").getAsInt(),
                    "",row.get("category").getAsString(),string(row,"manufacturer")));
        }
        products=List.copyOf(catalog);
        productsById=products.stream().collect(Collectors.toUnmodifiableMap(Product::getId,Function.identity()));
        knowledge=withLocalKnowledge(()->new LocalKnowledgeRepository().load(products));
        modelByProduct=knowledge.components().stream().filter(c->c.specs().fact(MODEL_ID)
                .map(ComponentSpecs.Fact::verified).orElse(false)).collect(Collectors.toUnmodifiableMap(
                        CatalogComponent::productId,c->c.specs().value(MODEL_ID).orElseThrow()));
        results=new BenchmarkService().load(KNOWLEDGE.resolve("benchmarks.json"));
    }

    @Test void publishedMeasurementsHaveVerifiedCatalogIdentitiesSourcesAndHonestDates() {
        assertTrue(knowledge.warnings().isEmpty(),()->String.join("\n",knowledge.warnings()));
        assertEquals(21,results.size());
        assertEquals(11,results.stream().map(BenchmarkService.Result::modelId).distinct().count());
        Set<String> verifiedModels=Set.copyOf(modelByProduct.values());
        for(var result:results) {
            assertTrue(verifiedModels.contains(result.modelId()),result.modelId());
            assertEquals("https",result.source().getScheme());
            assertTrue(result.source().getHost().equals("www.pugetsystems.com") ||
                    result.source().getHost().equals("pugetsystems.com"));
            assertNull(result.measuredAt(),"Publication date must not become an invented measurement date");
            assertNotNull(result.publishedAt());
            assertTrue(result.dateDescription().contains("точная дата измерения не указана"));
            assertTrue(result.context().conditions().keySet().stream().anyMatch(k->k.toLowerCase(java.util.Locale.ROOT).contains("cohort")),
                    "Review identity must isolate unpublished settings: "+result.modelId());
        }
    }

    @Test void intelCpuComparisonUsesActualSingleAndMultiCoreScores() {
        var compared=knowledge.benchmarks().compareModels(model(3),model(2));
        assertEquals(2,compared.size());
        var single=compared.stream().filter(c->c.left().context().workload().equals("CPU Single Core")).findFirst().orElseThrow();
        assertScores(single,"127.87","120.72","5.92");
        var multi=compared.stream().filter(c->c.left().context().workload().equals("CPU Multi Core")).findFirst().orElseThrow();
        assertScores(multi,"1659.19","1342.14","23.62");
        assertTrue(compared.stream().allMatch(c->c.betterModelId().equals(model(3))));
    }

    @Test void differentCpuPlatformsAndGpuCpuWorkloadsAreNotMerged() {
        assertTrue(knowledge.benchmarks().compareModels(model(2),model(7)).isEmpty(),
                "Intel and AMD use different platform conditions even in the same publication");
        assertTrue(knowledge.benchmarks().compareModels(model(2),model(14)).isEmpty(),
                "Cinebench CPU and GPU scores have different workloads");
    }

    @Test void gpuComparisonUsesActualRenderingScoresWithoutInventingAmdRtxResults() {
        var compared=knowledge.benchmarks().compareModels(model(14),model(13));
        assertEquals(2,compared.size());
        var cinebench=compared.stream().filter(c->c.left().context().suite().equals("Cinebench")).findFirst().orElseThrow();
        assertScores(cinebench,"19279","17271","11.63");
        var vray=compared.stream().filter(c->c.left().context().suite().equals("V-Ray Benchmark")).findFirst().orElseThrow();
        assertEquals("5.02",vray.left().context().version());
        assertEquals("GPU rendering (RTX Mode)",vray.left().context().workload());
        assertScores(vray,"3112","2605","19.46");
        var amdNvidia=knowledge.benchmarks().compareModels(model(21),model(16));
        assertEquals(1,amdNvidia.size());
        assertEquals("Cinebench",amdNvidia.getFirst().left().context().suite());
        assertTrue(knowledge.benchmarks().resultsFor(model(21)).stream()
                .noneMatch(r->r.context().suite().equals("V-Ray Benchmark")),
                "There is no verified AMD result in the publisher's RTX Mode chart");
    }

    @Test void conflictingBlenderVersionsAreExcludedFromTheImportedMeasurements() {
        assertTrue(results.stream().noneMatch(r->r.context().suite().toLowerCase(java.util.Locale.ROOT).contains("blender")),
                "Blender scores are excluded because the article and chart disagree on versions 4.0 and 3.6.0");
        var vray=results.stream().filter(r->r.context().suite().equals("V-Ray Benchmark")).toList();
        assertEquals(4,vray.size());
        assertTrue(vray.stream().allMatch(r->r.context().version().equals("5.02") &&
                r.context().workload().equals("GPU rendering (RTX Mode)")));
    }

    @Test void missingModelsAndUncoveredRtx4060DoNotReceiveManufacturedMeasurements() {
        assertTrue(knowledge.benchmarks().resultsFor(model(12)).isEmpty());
        assertTrue(knowledge.benchmarks().compareModels(model(12),model(13)).isEmpty());
        assertTrue(knowledge.benchmarks().compareModels("UNKNOWN_MODEL_999",model(13)).isEmpty());
        var answer=withLocalKnowledge(()->new ChatBotService(products)).reply(
                "Сравни "+productsById.get(12).getName()+" и "+productsById.get(13).getName());
        assertInstanceOf(ChatBotService.SpecificationsContent.class,answer.content().getFirst());
        assertTrue(answer.text().contains("нет проверенных измерений для NVIDIA GeForce RTX 4060 8GB"));
        assertFalse(answer.text().contains("%"));
    }

    @Test void chatResolvesFullCatalogCpuAndGpuNamesIntoPublishedComparisons() {
        for(int[] pair:List.of(new int[]{3,2},new int[]{14,13})) {
            var bot=withLocalKnowledge(()->new ChatBotService(products));
            var answer=bot.reply("Сравни "+productsById.get(pair[0]).getName()+" и "+productsById.get(pair[1]).getName());
            assertEquals(1,answer.content().size());
            var content=assertInstanceOf(ChatBotService.BenchmarksContent.class,answer.content().getFirst());
            assertEquals(2,content.comparisons().size());
            assertTrue(content.comparisons().stream().allMatch(c->c.left().modelId().equals(model(pair[0])) &&
                    c.right().modelId().equals(model(pair[1]))));
            assertTrue(answer.text().contains("(левое − правое) / правое"));
            assertTrue(answer.text().contains("опубликовано"));
            assertTrue(answer.text().contains("точная дата измерения не указана"));
            assertTrue(answer.text().contains("Общая производительность и FPS других задач из результата не следуют"));
            assertFalse(answer.text().contains("null"));
        }
    }

    private static String model(int productId) {
        return java.util.Objects.requireNonNull(modelByProduct.get(productId),"Missing verified MODEL_ID for "+productId);
    }
    private static void assertScores(BenchmarkService.Comparison comparison,String left,String right,String difference) {
        assertEquals(0,new BigDecimal(left).compareTo(comparison.left().value()));
        assertEquals(0,new BigDecimal(right).compareTo(comparison.right().value()));
        assertEquals(new BigDecimal(difference),comparison.differencePercent());
    }
    private static <T>T withLocalKnowledge(Supplier<T> action) {
        String previous=System.getProperty("techstore.knowledge.dir");
        try {System.setProperty("techstore.knowledge.dir",KNOWLEDGE.toString());return action.get();}
        finally {if(previous==null)System.clearProperty("techstore.knowledge.dir");else System.setProperty("techstore.knowledge.dir",previous);}
    }
    private static JsonObject json(Path path) throws IOException {
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {return JsonParser.parseReader(reader).getAsJsonObject();}
    }
    private static String string(JsonObject row,String key) {
        return row.has(key) && !row.get(key).isJsonNull()?row.get(key).getAsString():"";
    }
}
