package com.example.authapp.domain;

import com.example.authapp.models.Product;
import com.example.authapp.services.BenchmarkService;
import com.example.authapp.services.ChatBotService;
import com.example.authapp.services.ComparisonService;
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
                    string(row,"description"),row.get("price").getAsBigDecimal(),row.get("stock").getAsInt(),
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
        assertEquals(30,results.size());
        assertEquals(13,results.stream().map(BenchmarkService.Result::modelId).distinct().count());
        Set<String> verifiedModels=Set.copyOf(modelByProduct.values());
        for(var result:results) {
            assertTrue(verifiedModels.contains(result.modelId()),result.modelId());
            assertEquals("https",result.source().getScheme());
            assertTrue(Set.of("www.pugetsystems.com","pugetsystems.com","gamersnexus.net")
                    .contains(result.source().getHost()));
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
        assertEquals(3,compared.size());
        var cinebench=compared.stream().filter(c->c.left().context().suite().equals("Cinebench")).findFirst().orElseThrow();
        assertScores(cinebench,"19279","17271","11.63");
        var vray=compared.stream().filter(c->c.left().context().suite().equals("V-Ray Benchmark")).findFirst().orElseThrow();
        assertEquals("5.02",vray.left().context().version());
        assertEquals("GPU rendering (RTX Mode)",vray.left().context().workload());
        assertScores(vray,"3112","2605","19.46");
        var amdNvidia=knowledge.benchmarks().compareModels(model(21),model(16));
        assertEquals(2,amdNvidia.size());
        assertEquals(Set.of("Cinebench","Starfield"),amdNvidia.stream()
                .map(c->c.left().context().suite()).collect(Collectors.toSet()));
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
        for(int[] pair:List.of(new int[]{3,2,2},new int[]{14,13,3})) {
            var bot=withLocalKnowledge(()->new ChatBotService(products));
            var answer=bot.reply("Сравни "+productsById.get(pair[0]).getName()+" и "+productsById.get(pair[1]).getName());
            assertEquals(1,answer.content().size());
            var content=assertInstanceOf(ChatBotService.BenchmarksContent.class,answer.content().getFirst());
            assertEquals(pair[2],content.comparisons().size());
            assertTrue(content.comparisons().stream().allMatch(c->c.left().modelId().equals(model(pair[0])) &&
                    c.right().modelId().equals(model(pair[1]))));
            assertTrue(answer.text().contains("(левое − правое) / правое"));
            assertTrue(answer.text().contains("опубликовано"));
            assertTrue(answer.text().contains("точная дата измерения не указана"));
            assertTrue(answer.text().contains("Общая производительность и FPS других задач из результата не следуют"));
            assertFalse(answer.text().contains("null"));
        }
    }

    @Test void gamingMeasurementsRetainThePublishedAmdCohortWithoutInventingMissingSettings() {
        var gaming=results.stream().filter(r->r.context().suite().equals("F1")).toList();
        assertEquals(4,gaming.size());
        assertEquals(Set.of(model(7),model(8),model(9),model(10)),
                gaming.stream().map(BenchmarkService.Result::modelId).collect(Collectors.toSet()));
        assertEquals(21,results.stream().filter(r->r.source().getHost().endsWith("pugetsystems.com")).count(),
                "The existing rendering measurements must remain present");
        var expected=Map.of(model(7),new BigDecimal("311.5"),model(8),new BigDecimal("438.3"),
                model(9),new BigDecimal("331.8"),model(10),new BigDecimal("363.4"));
        for(var result:gaming) {
            assertEquals(0,expected.get(result.modelId()).compareTo(result.value()));
            assertEquals(gaming.getFirst().context(),result.context());
            assertEquals("24",result.context().version());
            assertEquals("Average framerate",result.context().metric());
            assertEquals("FPS",result.context().unit());
            assertEquals(BenchmarkService.Direction.HIGHER_IS_BETTER,result.context().direction());
            assertEquals("gamersnexus.net",result.source().getHost());
            assertNull(result.measuredAt(),"The raw chart tag must not become an inferred measurement date");
            assertEquals(java.time.LocalDate.of(2024,11,19),result.publishedAt());
            var conditions=result.context().conditions();
            assertEquals("gn-7600x3d-2024-11-19-amd-f1-24-1080p-high",conditions.get("review_cohort"));
            assertEquals("1080p",conditions.get("resolution"));assertEquals("High",conditions.get("preset"));
            assertEquals("[10/24] (published chart tag; exact measurement date not reported)",conditions.get("chart_row_tag"));
            assertFalse(conditions.containsKey("chart_measurement_month"));
            for(String key:List.of("motherboard","bios","gpu_driver","os","psu","storage","benchmark_patch"))
                assertTrue(conditions.get(key).startsWith("Not reported"),key);
            assertTrue(conditions.get("power_policy").contains("not published"));
        }
    }

    @Test void actualF1AverageFpsAreComparedOnlyWithinTheirSingleGamingContext() {
        var comparisons=knowledge.benchmarks().compareModels(model(8),model(7));
        assertEquals(1,comparisons.size());var comparison=comparisons.getFirst();
        assertEquals("F1",comparison.left().context().suite());
        assertScores(comparison,"438.3","311.5","40.71");
        assertEquals(model(8),comparison.betterModelId());
        assertTrue(knowledge.benchmarks().compareModels(model(8),model(4)).isEmpty(),
                "An Intel rendering result cannot be joined to the AMD-only gaming cohort");
        assertTrue(knowledge.benchmarks().compareModels(model(8),model(13)).isEmpty());
    }

    @Test void chatShowsRealGamingFpsAndKeepsDifferentTestsAsACharacteristicsFallback() {
        var bot=withLocalKnowledge(()->new ChatBotService(products));
        var answer=bot.reply("Сравни Ryzen 7 7800X3D и Ryzen 5 7600X");
        var content=assertInstanceOf(ChatBotService.BenchmarksContent.class,answer.content().getFirst());
        assertEquals(1,content.comparisons().size());
        assertScores(content.comparisons().getFirst(),"438.3","311.5","40.71");
        assertTrue(answer.text().contains("FPS"));assertTrue(answer.text().contains("F1 24"));
        var fallback=bot.reply("Сравни i9-14900K и Ryzen 7 7800X3D");
        assertInstanceOf(ChatBotService.SpecificationsContent.class,fallback.content().getFirst());
        assertTrue(fallback.text().contains("есть измерения"));
        assertTrue(fallback.text().contains("нет общего теста"));
        assertFalse(fallback.text().contains("нет проверенных измерений для"));
        assertFalse(fallback.text().contains("%"));
        var byId=knowledge.components().stream().collect(Collectors.toMap(CatalogComponent::productId,Function.identity()));
        assertEquals(ComparisonService.Status.NO_COMMON_CONTEXT,
                new ComparisonService(knowledge,products).compare(byId.get(4),byId.get(8),false,"").status());
    }

    @Test void starfieldGpuScoresPreserveTheExactSampleScopeAndRawPublishedTag() {
        var gaming=results.stream().filter(r->r.context().suite().equals("Starfield")).toList();
        assertEquals(5,gaming.size());
        var expected=Map.of(model(13),new BigDecimal("46.5"),model(14),new BigDecimal("52.9"),
                model(16),new BigDecimal("91.8"),model(19),new BigDecimal("51.0"),model(21),new BigDecimal("83.0"));
        assertEquals(expected.keySet(),gaming.stream().map(BenchmarkService.Result::modelId).collect(Collectors.toSet()));
        for(var result:gaming) {
            assertEquals(0,expected.get(result.modelId()).compareTo(result.value()));
            assertEquals(gaming.getFirst().context(),result.context());
            assertEquals("Not reported",result.context().version());
            assertEquals("GPU gaming benchmark (4K/High/No Upscaling)",result.context().workload());
            assertEquals("Average framerate",result.context().metric());assertEquals("FPS",result.context().unit());
            assertNull(result.measuredAt());assertEquals(java.time.LocalDate.of(2024,2,13),result.publishedAt());
            var conditions=result.context().conditions();
            assertEquals("gn-4070super-2024-02-13-starfield-4k-high-gpu",conditions.get("review_cohort"));
            assertEquals("(1/24) (published chart tag; exact measurement date not reported)",conditions.get("chart_row_tag"));
            assertFalse(conditions.containsKey("chart_measurement_month"));
            assertEquals("MSI Z690 Unify",conditions.get("motherboard"));
            assertTrue(conditions.get("cpu").contains("4.9 GHz P-cores / 3.9 GHz E-cores"));
            assertEquals("High",conditions.get("preset"));assertEquals("No Upscaling (chart title)",conditions.get("upscaling"));
            for(String key:List.of("bios","gpu_driver","storage","benchmark_patch","frame_generation"))
                assertTrue(conditions.get(key).startsWith("Not reported"),key);
            var samples=conditions.get("tested_samples");
            assertTrue(samples.contains("NVIDIA RTX 4070 FE 12GB Stock"));
            assertTrue(samples.contains("NVIDIA RTX 4070 Super 12GB Stock (FE/AIB not specified)"));
            assertTrue(samples.contains("RTX 4090 24GB Stock (FE/AIB not specified)"));
            assertTrue(samples.contains("AMD RX 7800 XT 16GB Stock (AIB not specified)"));
            assertTrue(samples.contains("Sapphire RX 7900 XTX Nitro+ 24GB Stock"));
            assertTrue(samples.contains("not an identification or performance guarantee for the store SKU"));
            assertTrue(conditions.get("power_policy").contains("does not establish reference clocks"));
        }
    }

    @Test void starfieldGpuComparisonRejectsDifferentVariantsAndForeignChartTags() {
        var comparisons=knowledge.benchmarks().compareModels(model(14),model(13));
        assertEquals(3,comparisons.size(),"Two rendering contexts plus the separate Starfield context");
        var game=comparisons.stream().filter(c->c.left().context().suite().equals("Starfield")).findFirst().orElseThrow();
        assertScores(game,"52.9","46.5","13.76");assertEquals(model(14),game.betterModelId());
        for(int excluded:List.of(12,15,20))
            assertTrue(knowledge.benchmarks().resultsFor(model(excluded)).stream()
                    .noneMatch(row->row.context().suite().equals("Starfield")),
                    "Do not substitute a legacy4060 row,4080 non-SUPER or4060 Ti8GB for catalogue model "+excluded);
        var right=game.right();var context=right.context();
        var conditions=new java.util.HashMap<>(context.conditions());
        conditions.put("chart_row_tag","(8/23) (different published chart tag)");
        var differentTag=new BenchmarkService.Result(right.modelId(),new BenchmarkService.Context(
                context.suite(),context.version(),context.workload(),context.metric(),context.unit(),context.direction(),conditions),
                right.value(),right.source(),right.measuredAt(),right.publishedAt());
        assertTrue(knowledge.benchmarks().compare(game.left(),differentTag).isEmpty());
        assertTrue(knowledge.benchmarks().compareModels(model(8),model(19)).isEmpty(),
                "CPU F1 FPS and GPU Starfield FPS do not share a test just because their unit matches");
    }

    @Test void chatAddsThePublishedStarfieldFpsWithoutReplacingRenderingComparisons() {
        var answer=withLocalKnowledge(()->new ChatBotService(products)).reply("Сравни RTX 4070 SUPER и RTX 4070");
        var content=assertInstanceOf(ChatBotService.BenchmarksContent.class,answer.content().getFirst());
        assertEquals(3,content.comparisons().size());
        var game=content.comparisons().stream().filter(c->c.left().context().suite().equals("Starfield")).findFirst().orElseThrow();
        assertScores(game,"52.9","46.5","13.76");
        assertTrue(answer.text().contains("Starfield"));assertTrue(answer.text().contains("FPS"));
        assertTrue(answer.text().contains("точная дата измерения не указана"));
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
