package com.example.authapp.domain;

import com.example.authapp.models.Product;
import com.example.authapp.services.BenchmarkService;
import com.example.authapp.services.BenchmarkPresentation;
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
        assertEquals(51,results.size());
        assertEquals(14,results.stream().map(BenchmarkService.Result::context).distinct().count());
        assertEquals(19,results.stream().map(BenchmarkService.Result::modelId).distinct().count());
        Set<String> verifiedModels=Set.copyOf(modelByProduct.values());
        for(var result:results) {
            assertTrue(verifiedModels.contains(result.modelId()),result.modelId());
            assertEquals("https",result.source().getScheme());
            assertTrue(Set.of("www.pugetsystems.com","pugetsystems.com","gamersnexus.net","www.tomshardware.com")
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
        assertEquals(4,compared.size());
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
        var vray=results.stream().filter(r->r.context().suite().equals("V-Ray Benchmark") && r.source().getHost().contains("pugetsystems.com")).toList();
        assertEquals(4,vray.size());
        assertTrue(vray.stream().allMatch(r->r.context().version().equals("5.02") &&
                r.context().workload().equals("GPU rendering (RTX Mode)")));
    }

    @Test void missingModelsAndUncovered12400fDoNotReceiveManufacturedMeasurements() {
        assertTrue(knowledge.benchmarks().resultsFor(model(5)).isEmpty());
        assertTrue(knowledge.benchmarks().compareModels(model(5),model(11)).isEmpty());
        assertTrue(knowledge.benchmarks().compareModels("UNKNOWN_MODEL_999",model(13)).isEmpty());
        var answer=withLocalKnowledge(()->new ChatBotService(products)).reply(
                "Сравни "+productsById.get(5).getName()+" и "+productsById.get(11).getName());
        assertInstanceOf(ChatBotService.SpecificationsContent.class,answer.content().getFirst());
        assertTrue(answer.text().contains("нет проверенных измерений для Intel Core i5-12400F"));
        assertFalse(answer.text().contains("%"));
    }

    @Test void chatResolvesFullCatalogCpuAndGpuNamesIntoPublishedComparisons() {
        for(int[] pair:List.of(new int[]{3,2,2},new int[]{14,13,4})) {
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
        var gaming=results.stream().filter(r->r.context().conditions().getOrDefault("review_cohort","")
                .equals("gn-7600x3d-2024-11-19-amd-f1-24-1080p-high")).toList();
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
        var crossPlatform=knowledge.benchmarks().compareModels(model(8),model(4));
        assertEquals(1,crossPlatform.size());
        assertFalse(knowledge.benchmarks().compare(comparison.left(),crossPlatform.getFirst().right()).isPresent(),
                "Equal 7800X3D labels in different published reviews must not join their cohorts");
        assertTrue(knowledge.benchmarks().compareModels(model(8),model(13)).isEmpty());
    }

    @Test void chatShowsRealGamingFpsAndKeepsDifferentTestsAsACharacteristicsFallback() {
        var bot=withLocalKnowledge(()->new ChatBotService(products));
        var answer=bot.reply("Сравни Ryzen 7 7800X3D и Ryzen 5 7600X");
        var content=assertInstanceOf(ChatBotService.BenchmarksContent.class,answer.content().getFirst());
        assertEquals(1,content.comparisons().size());
        assertScores(content.comparisons().getFirst(),"438.3","311.5","40.71");
        assertTrue(answer.text().contains("FPS"));assertTrue(answer.text().contains("F1 24"));
        var fallback=bot.reply("Сравни i9-14900K и Ryzen 5 7600X");
        assertInstanceOf(ChatBotService.SpecificationsContent.class,fallback.content().getFirst());
        assertTrue(fallback.text().contains("есть измерения"));
        assertTrue(fallback.text().contains("нет общего теста"));
        assertFalse(fallback.text().contains("нет проверенных измерений для"));
        assertFalse(fallback.text().contains("%"));
        var byId=knowledge.components().stream().collect(Collectors.toMap(CatalogComponent::productId,Function.identity()));
        assertEquals(ComparisonService.Status.NO_COMMON_CONTEXT,
                new ComparisonService(knowledge,products).compare(byId.get(4),byId.get(7),false,"").status());
    }

    @Test void cpuPlatformComparisonKeepsExactBaselineRowsAndExposesItsLimitedScope() {
        String cohort="gn-285k-2024-10-29-f1-24-1080p-high-baseline-two-platforms";
        var samples=results.stream().filter(r->cohort.equals(r.context().conditions().get("review_cohort"))).toList();
        assertEquals(2,samples.size());assertEquals(Set.of(model(4),model(8)),samples.stream()
                .map(BenchmarkService.Result::modelId).collect(Collectors.toSet()));
        assertEquals(samples.getFirst().context(),samples.getLast().context());
        var compared=knowledge.benchmarks().compareModels(model(4),model(8));assertEquals(1,compared.size());
        assertScores(compared.getFirst(),"384.5","438.3","-12.27");
        assertScores(knowledge.benchmarks().compareModels(model(8),model(4)).getFirst(),"438.3","384.5","13.99");
        assertEquals(model(8),compared.getFirst().betterModelId());
        assertTrue(samples.stream().noneMatch(r->r.value().compareTo(new BigDecimal("389.3"))==0),"APO ON is a separate excluded row");
        for(var sample:samples) {
            assertNull(sample.measuredAt());assertEquals(java.time.LocalDate.of(2024,10,29),sample.publishedAt());
            var conditions=sample.context().conditions();
            assertEquals("PUBLISHED_PLATFORM_COMPARISON",conditions.get("comparison_kind"));
            assertEquals("[10/24] (published chart tag; exact measurement date not reported)",conditions.get("chart_row_tag"));
            assertTrue(conditions.get("ram").contains("per-CPU kit assignment and capacity not reported"));
            assertTrue(conditions.get("motherboard").startsWith("Not reported per gaming sample"));
            assertFalse(conditions.get("motherboard").contains("Z790 Hero"));
            assertTrue(conditions.get("bios").contains("exact per-sample versions not reported"));
            assertTrue(conditions.get("power_policy").contains("not independently established"));
            assertTrue(BenchmarkPresentation.scopeNote(sample.context()).contains("двух тестовых платформ"));
        }
        var reply=withLocalKnowledge(()->new ChatBotService(products)).reply("сравни i9-14900k и ryzen 7 7800x3d");
        var card=assertInstanceOf(ChatBotService.BenchmarksContent.class,reply.content().getFirst());
        assertEquals(1,card.comparisons().size());assertTrue(reply.lead().contains("двух тестовых платформ"));
        assertTrue(reply.text().contains("распределение памяти по CPU"));assertTrue(reply.text().contains("не прогноз FPS готовой сборки"));
    }

    @Test void starfieldGpuScoresPreserveTheExactSampleScopeAndRawPublishedTag() {
        var gaming=starfieldCohort("gn-4070super-2024-02-13-starfield-4k-high-gpu");
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
        assertEquals(4,comparisons.size(),"Two rendering contexts plus two separate Starfield resolutions");
        var game=comparisons.stream().filter(c->c.left().context().conditions().getOrDefault("review_cohort","")
                .equals("gn-4070super-2024-02-13-starfield-4k-high-gpu")).findFirst().orElseThrow();
        assertScores(game,"52.9","46.5","13.76");assertEquals(model(14),game.betterModelId());
        for(int excluded:List.of(12,15,20))
            assertTrue(knowledge.benchmarks().resultsFor(model(excluded)).stream()
                    .noneMatch(row->row.context().conditions().getOrDefault("review_cohort","")
                            .equals("gn-4070super-2024-02-13-starfield-4k-high-gpu")),
                    "Do not substitute another chart4060 row,4080 non-SUPER or4060 Ti8GB into the4K cohort for catalogue model "+excluded);
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
        assertEquals(4,content.comparisons().size());
        var game=content.comparisons().stream().filter(c->c.left().context().conditions().getOrDefault("review_cohort","")
                .equals("gn-4070super-2024-02-13-starfield-4k-high-gpu")).findFirst().orElseThrow();
        assertScores(game,"52.9","46.5","13.76");
        assertTrue(answer.text().contains("Starfield"));assertTrue(answer.text().contains("FPS"));
        assertTrue(answer.text().contains("точная дата измерения не указана"));
    }

    @Test void starfield1440pScoresRetainSixExactSamplesAndUnpublishedDetails() {
        var gaming=starfieldCohort("gn-7600xt-2024-03-07-starfield-1440p-high-gpu");
        assertEquals(6,gaming.size());
        var expected=Map.of(model(12),new BigDecimal("48.5"),model(13),new BigDecimal("78.7"),
                model(14),new BigDecimal("86.4"),model(17),new BigDecimal("46.6"),
                model(18),new BigDecimal("66.3"),model(19),new BigDecimal("84.6"));
        assertEquals(expected.keySet(),gaming.stream().map(BenchmarkService.Result::modelId).collect(Collectors.toSet()));
        for(var result:gaming) {
            assertEquals(0,expected.get(result.modelId()).compareTo(result.value()));
            assertEquals(gaming.getFirst().context(),result.context());
            assertEquals("GPU gaming benchmark (1440p/High/No Upscaling)",result.context().workload());
            assertEquals("FPS",result.context().unit());assertEquals("Average framerate",result.context().metric());
            assertEquals("Not reported",result.context().version());assertNull(result.measuredAt());
            assertEquals(java.time.LocalDate.of(2024,3,7),result.publishedAt());
            assertTrue(result.source().getPath().endsWith("amd-radeon-rx-7600-xt-gpu-benchmarks-review-power-efficiency-gaming"));
            var conditions=result.context().conditions();
            assertEquals("1440p (chart title; exact pixel dimensions not stated)",conditions.get("resolution"));
            assertEquals("(1/24) (published chart tag; exact measurement date not reported)",conditions.get("chart_row_tag"));
            assertFalse(conditions.containsKey("chart_measurement_month"));
            assertEquals("High",conditions.get("preset"));assertEquals("No Upscaling (chart title)",conditions.get("upscaling"));
            assertEquals("MSI Z690 Unify",conditions.get("motherboard"));
            assertTrue(conditions.get("cpu").contains("4.9 GHz P-cores / 3.9 GHz E-cores"));
            for(String key:List.of("bios","gpu_driver","storage","benchmark_patch","frame_generation"))
                assertTrue(conditions.get(key).startsWith("Not reported"),key);
            var samples=conditions.get("tested_samples");
            for(String sample:List.of("ASUS RTX 4060 Dual 8GB","NVIDIA RTX 4070 FE 12GB", "AMD RX 7600 Ref 8GB",
                    "XFX QICK 319 RX 7700 XT Black 12GB","NVIDIA RTX 4070 Super 12GB (FE/AIB not specified)",
                    "AMD RX 7800 XT 16GB (AIB not specified)","not the identified store AIB/SKU"))assertTrue(samples.contains(sample),sample);
            assertTrue(conditions.get("power_policy").contains("does not establish reference clocks"));
        }
        assertTrue(knowledge.benchmarks().resultsFor(model(20)).stream().noneMatch(r->r.source().getHost().equals("gamersnexus.net")),"The GN chart4060Ti8GB is not the store4060Ti16GB");
    }

    @Test void starfieldResolutionsRemainSeparateAndNewCoverageDoesNotInventOtherScenarios() {
        var compared=knowledge.benchmarks().compareModels(model(14),model(13));
        var at1440=compared.stream().filter(c->c.left().context().conditions().getOrDefault("review_cohort","")
                .equals("gn-7600xt-2024-03-07-starfield-1440p-high-gpu")).findFirst().orElseThrow();
        var at4k=compared.stream().filter(c->c.left().context().conditions().getOrDefault("review_cohort","")
                .equals("gn-4070super-2024-02-13-starfield-4k-high-gpu")).findFirst().orElseThrow();
        assertScores(at1440,"86.4","78.7","9.78");assertScores(at4k,"52.9","46.5","13.76");
        assertTrue(knowledge.benchmarks().compare(at1440.left(),at4k.right()).isEmpty(),"Matching raw tags do not merge reviews or resolutions");
        assertTrue(knowledge.benchmarks().compare(at4k.left(),at1440.right()).isEmpty());
        var answer=withLocalKnowledge(()->new ChatBotService(products)).reply("Сравни RTX 4060 и RX 7600 для игр в Starfield 1440p");
        var content=assertInstanceOf(ChatBotService.BenchmarksContent.class,answer.content().getFirst());
        assertEquals(1,content.comparisons().size());assertScores(content.comparisons().getFirst(),"48.5","46.6","4.08");
        for(String resolution:List.of("1080p","4K")) {
            var unavailable=withLocalKnowledge(()->new ChatBotService(products)).reply(
                    "Сравни RTX 4060 и RX 7600 для игр в Starfield "+resolution);
            assertTrue(unavailable.content().isEmpty());assertFalse(unavailable.text().contains("%"));
            assertTrue(unavailable.suggestions().contains("Для игр в Starfield 1440p"));
        }
    }

    @Test void blackMythRetainsOneChartAndExplicitPlatformConflictWithoutAssigningThe4060TiCapacity() {
        var samples=starfieldCohort("gn-5060-2025-07-21-black-myth-1440p-high-12-24");
        assertEquals(5,samples.size());
        assertEquals(Set.of(model(12),model(13),model(17),model(18),model(19)),samples.stream()
                .map(BenchmarkService.Result::modelId).collect(Collectors.toSet()));
        var comparison=knowledge.benchmarks().compareModels(model(12),model(17)).stream()
                .filter(c->c.left().context().suite().equals("Black Myth: Wukong")).findFirst().orElseThrow();
        assertScores(comparison,"36.9","31.6","16.77");
        assertScores(knowledge.benchmarks().compare(comparison.right(),comparison.left()).orElseThrow(),"31.6","36.9","-14.36");
        for(var result:samples) {
            assertEquals(samples.getFirst().context(),result.context());
            assertEquals(java.time.LocalDate.of(2025,7,21),result.publishedAt());assertNull(result.measuredAt());
            var conditions=result.context().conditions();
            assertEquals("SOURCE_PLATFORM_CONFLICT",conditions.get("comparison_kind"));
            assertTrue(conditions.get("test_system").contains("9800X3D"));assertTrue(conditions.get("test_system").contains("12700KF"));
            assertTrue(conditions.get("upscaling").contains("Не опубликовано"));
            assertTrue(conditions.get("chart_row_tag").contains("(12/24)"));
            assertTrue(BenchmarkPresentation.scopeNote(result.context()).contains("не совпадает"));
        }
        assertTrue(knowledge.benchmarks().resultsFor(model(20)).stream().noneMatch(r->r.source().getHost().equals("gamersnexus.net")),"A prose mention16GB must not assign an unspecified-capacity GN FE chart row");
        var starfield=starfieldCohort("gn-7600xt-2024-03-07-starfield-1440p-high-gpu").getFirst();
        assertTrue(knowledge.benchmarks().compare(comparison.left(),starfield).isEmpty());
    }

    @Test void blackMythFreeDialogueKeepsGameResolutionAndUnknownSettingsSeparate() {
        var bot=withLocalKnowledge(()->new ChatBotService(products));
        bot.reply("Сравни RTX4060 и RX7600");
        var result=bot.reply("Для игр в Блэк Миф: Вуконг1440p");
        var card=assertInstanceOf(ChatBotService.BenchmarksContent.class,result.content().getFirst());
        assertEquals(1,card.comparisons().size());assertScores(card.comparisons().getFirst(),"36.9","31.6","16.77");
        assertTrue(result.lead().contains("не совпадает"));
        assertFalse(result.lead().contains("двух тестовых платформ"),"A documentation conflict is not an intentional CPU platform comparison");
        for(String unknown:List.of("А в Black Myth1440p DLSS?","Для игр в Black Myth1080p","Для игр в Starfield и Black Myth1440p")) {
            var answer=bot.reply(unknown);assertTrue(answer.content().isEmpty(),unknown);assertFalse(answer.text().contains("%"),unknown);
            assertTrue(answer.suggestions().contains("Для игр в Black Myth: Wukong 1440p"));
        }
        var resumed=bot.reply("Для игр в Black Myth1440p");
        assertInstanceOf(ChatBotService.BenchmarksContent.class,resumed.content().getFirst());
    }

    @Test void f1TwentyFiveAddsTheExact7600WithoutBorrowingTheXVariantOrARecommendedBuild() {
        var samples=starfieldCohort("gn-guide-2026-02-02-f1-25-1080p-high-amd");
        assertEquals(3,samples.size());
        var expected=Map.of(model(6),new BigDecimal("217.0"),model(8),new BigDecimal("302.3"),model(10),new BigDecimal("253.8"));
        assertEquals(expected.keySet(),samples.stream().map(BenchmarkService.Result::modelId).collect(Collectors.toSet()));
        for(var sample:samples) {
            assertEquals(0,expected.get(sample.modelId()).compareTo(sample.value()));
            assertEquals("25",sample.context().version());assertNull(sample.measuredAt());
            assertEquals(java.time.LocalDate.of(2026,2,2),sample.publishedAt());
            assertEquals(samples.getFirst().context(),sample.context());
            assertTrue(sample.context().conditions().get("motherboard").contains("не является тестовым стендом"));
            assertTrue(sample.context().conditions().get("chart_row_tag").contains("[1/26]"));
        }
        assertEquals(1,knowledge.benchmarks().resultsFor(model(6)).size());
        assertTrue(knowledge.benchmarks().resultsFor(model(7)).stream().noneMatch(r->r.context().version().equals("25")));
        for(int id:List.of(5,11,20))assertTrue(knowledge.benchmarks().resultsFor(model(id)).stream().noneMatch(r->r.context().suite().equals("F1") && r.context().version().equals("25")));
        var compared=knowledge.benchmarks().compareModels(model(6),model(8));assertEquals(1,compared.size());
        assertScores(compared.getFirst(),"217.0","302.3","-28.22");
        assertScores(knowledge.benchmarks().compareModels(model(8),model(6)).getFirst(),"302.3","217.0","39.31");
    }

    @Test void f1VersionSwitchNeverCombinesVersionsOrSilentlyChoosesOneOfSeveralGames() {
        var bot=withLocalKnowledge(()->new ChatBotService(products));
        var newer=bot.reply("Сравни Ryzen9 7950X и Ryzen7 7800X3D для игр в F1 25 1080p");
        var card=assertInstanceOf(ChatBotService.BenchmarksContent.class,newer.content().getFirst());
        assertEquals(1,card.comparisons().size());assertScores(card.comparisons().getFirst(),"253.8","302.3","-16.04");
        assertEquals("25",card.comparisons().getFirst().left().context().version());
        var older=bot.reply("Для игр в F1 24 1080p");
        var previous=assertInstanceOf(ChatBotService.BenchmarksContent.class,older.content().getFirst());
        assertEquals(1,previous.comparisons().size());assertScores(previous.comparisons().getFirst(),"363.4","438.3","-17.09");
        assertTrue(knowledge.benchmarks().compare(card.comparisons().getFirst().left(),previous.comparisons().getFirst().right()).isEmpty());
        for(String scenario:List.of("F1 1080p","F1 24 F1 25 1080p","F1 25 1440p","Starfield F1 25 1080p","F1 25 1080p с RTX4070")) {
            var answer=bot.reply("Для игр в "+scenario);assertTrue(answer.content().isEmpty(),scenario);assertFalse(answer.text().contains("%"),scenario);
        }
    }

    @Test void exact14100fDdr4HasItsOwnPublishedPlatformComparisonAndDoesNotMergePuget() {
        var compared=knowledge.benchmarks().compareModels(model(11),model(7));
        assertEquals(1,compared.size());var c=compared.getFirst();
        assertScores(c,"486","781","-37.77");
        assertScores(knowledge.benchmarks().compareModels(model(7),model(11)).getFirst(),"781","486","60.70");
        assertEquals("PUBLISHED_PLATFORM_COMPARISON",c.left().context().conditions().get("comparison_kind"));
        assertTrue(c.left().context().conditions().get("ram").contains("не опубликована"));
        assertTrue(BenchmarkPresentation.scopeNote(c.left().context()).contains("различия платформ"));
        var puget=results.stream().filter(r->r.modelId().equals(model(7)) && r.source().getHost().contains("pugetsystems.com")).findFirst().orElseThrow();
        assertTrue(knowledge.benchmarks().compare(c.left(),puget).isEmpty());
    }

    @Test void exact16GbVrayRtxSamplesRetainUnknownVersionDriverAndAibLimits() {
        var compared=knowledge.benchmarks().compareModels(model(20),model(13));assertEquals(1,compared.size());var c=compared.getFirst();
        assertScores(c,"1831","2481","-26.20");
        assertScores(knowledge.benchmarks().compareModels(model(13),model(20)).getFirst(),"2481","1831","35.50");
        assertScores(knowledge.benchmarks().compareModels(model(20),model(12)).getFirst(),"1831","1682","8.86");
        assertEquals("Not reported",c.left().context().version());
        assertEquals("GPU rendering (RTX Mode)",c.left().context().workload());
        assertEquals("vrays",c.left().context().unit());
        assertTrue(c.left().context().conditions().get("platform").contains("12900K"));
        assertTrue(BenchmarkPresentation.scopeNote(c.left().context()).contains("одинаковые драйверы не подтверждены"));
        var puget=results.stream().filter(r->r.modelId().equals(model(13)) && r.context().suite().equals("V-Ray Benchmark") && r.source().getHost().contains("pugetsystems.com")).findFirst().orElseThrow();
        assertTrue(knowledge.benchmarks().compare(c.left(),puget).isEmpty());
        var bot=withLocalKnowledge(()->new ChatBotService(products));
        bot.reply("Сравни RTX4060 Ti16GB и RTX4070");
        var gaming=bot.reply("Для игр в Starfield1440p");
        assertTrue(gaming.content().isEmpty());assertFalse(gaming.text().contains("%"));
    }

    private static List<BenchmarkService.Result> starfieldCohort(String cohort) {
        return results.stream().filter(r->r.context().conditions().getOrDefault("review_cohort","").equals(cohort)).toList();
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
