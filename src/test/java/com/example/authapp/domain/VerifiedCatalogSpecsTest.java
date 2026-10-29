package com.example.authapp.domain;

import com.example.authapp.models.Product;
import com.example.authapp.services.BuildRecommendationService;
import com.example.authapp.services.CompatibilityService;
import com.example.authapp.services.LocalKnowledgeRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.authapp.domain.ComponentSpecs.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offline integration checks for the authored data and the actual public catalog snapshot. */
class VerifiedCatalogSpecsTest {
    private static final Path KNOWLEDGE = Path.of("knowledge").toAbsolutePath();
    private static LocalKnowledgeRepository.Snapshot snapshot;
    private static Map<Integer, CatalogComponent> components;
    private static Map<Integer, Product> products;
    private static ComponentSpecsLoader.Dataset dataset;
    private final CompatibilityService compatibility = new CompatibilityService();

    @BeforeAll
    static void loadRealLocalCatalog() throws IOException {
        List<Product> catalog = new ArrayList<>();
        for (var item : readJson(KNOWLEDGE.resolve("catalog-reference.json")).getAsJsonArray("products")) {
            JsonObject row = item.getAsJsonObject();
            catalog.add(new Product(row.get("id").getAsInt(), row.get("name").getAsString(),
                    string(row, "description"), row.get("price").getAsDouble(), row.get("stock").getAsInt(),
                    "", row.get("category").getAsString(), string(row, "manufacturer")));
        }
        products = catalog.stream().collect(Collectors.toUnmodifiableMap(Product::getId, Function.identity()));
        dataset = new ComponentSpecsLoader().loadDataset(KNOWLEDGE.resolve("component-specs.json"));
        String previous = System.getProperty("techstore.knowledge.dir");
        try {
            System.setProperty("techstore.knowledge.dir", KNOWLEDGE.toString());
            snapshot = new LocalKnowledgeRepository().load(catalog);
        } finally {
            if (previous == null) System.clearProperty("techstore.knowledge.dir");
            else System.setProperty("techstore.knowledge.dir", previous);
        }
        components = snapshot.components().stream().collect(
                Collectors.toUnmodifiableMap(CatalogComponent::productId, Function.identity()));
    }

    @Test
    void all67SystemBlockProductsHaveValidatedIdentitiesWithoutWarnings() {
        assertEquals(67, snapshot.components().size(), "Only the eight system-block categories belong in selection");
        assertTrue(snapshot.warnings().isEmpty(), () -> String.join("\n", snapshot.warnings()));
        assertEquals(components.keySet(), dataset.specs().keySet(), "Each recognized catalog ID needs a data record");
        assertEquals(components.keySet(), dataset.identities().keySet(), "No authored facts may follow an unbound ID");
        for (var component : snapshot.components()) {
            var identity = dataset.identities().get(component.productId());
            assertEquals(products.get(component.productId()).getName(), identity.catalogName());
            assertEquals(component.category(), identity.category(), "Category is part of the identity binding");
            assertEquals(dataset.specs().get(component.productId()), component.specs(),
                    "A successful load must apply the authored record, including intentional empty facts");
        }
    }

    @Test
    void verifiedFactsRetainHttpsSourcesAndIsoCheckDates() throws IOException {
        var records = readJson(KNOWLEDGE.resolve("component-specs.json")).getAsJsonArray("products");
        assertEquals(67, records.size());
        int verifiedFacts = 0;
        for (var item : records) {
            var row = item.getAsJsonObject();
            for (var entry : row.getAsJsonObject("facts").entrySet()) {
                var fact = entry.getValue().getAsJsonObject();
                if (!"VERIFIED".equals(fact.get("verification").getAsString())) continue;
                verifiedFacts++;
                String context = "Product " + row.get("productId") + ", " + entry.getKey();
                URI source = assertDoesNotThrow(() -> URI.create(fact.get("source").getAsString()), context);
                assertEquals("https", source.getScheme(), context);
                assertNotNull(source.getHost(), context);
                assertNull(source.getUserInfo(), context);
                assertTrue(fact.has("checkedAt"), context + " lacks a check date");
                assertDoesNotThrow(() -> LocalDate.parse(fact.get("checkedAt").getAsString()), context);
            }
        }
        assertTrue(verifiedFacts > 0, "The offline dataset must actually contain researched facts");
    }

    @Test
    void actualCatalogPairsExposeVerifiedCompatibilityConflicts() {
        assertConflict("socket", 6, 26);           // AM5 CPU and LGA1700 motherboard
        assertConflict("memory_type", 26, 34);    // DDR4 motherboard and DDR5 memory kit
        assertConflict("gpu_min_psu", 16, 45);    // RTX 4090 reference minimum 850W and a 750W PSU
        assertConflict("cooler_height", 62, 69);  // O11 Dynamic 155mm and Dark Rock Pro 4 163mm
    }

    @Test
    void genericGpuNamesDoNotAcquireBoardSpecificDimensionsOrElectricalMaxima() {
        for (var gpu : snapshot.components().stream().filter(c -> c.category() == ComponentCategory.GPU).toList()) {
            assertNotVerified(gpu, LENGTH_MM);
            assertNotVerified(gpu, MAX_POWER_WATTS);
            assertNotVerified(gpu, REQUIRED_POWER_CONNECTORS);
        }
        assertFinding(compatibility.assess(List.of(part(16), part(62)), false),
                "gpu_length", CompatibilityService.Status.INSUFFICIENT_DATA);
        assertFinding(compatibility.assess(List.of(part(6), part(16), part(46)), false),
                "power_budget", CompatibilityService.Status.INSUFFICIENT_DATA);
    }

    @Test
    void unresolvedBiosListsCaseRevisionsAndCoolerRevisionRemainUnknown() {
        for (var board : snapshot.components().stream()
                .filter(c -> c.category() == ComponentCategory.MOTHERBOARD).toList()) {
            assertNotVerified(board, SUPPORTED_CPU_MODELS);
        }
        assertFinding(compatibility.assess(List.of(part(6), part(29)), false),
                "cpu_support", CompatibilityService.Status.INSUFFICIENT_DATA);
        for (int caseId : List.of(55, 56)) {
            assertNotVerified(part(caseId), MAX_GPU_LENGTH_MM);
            assertFinding(compatibility.assess(List.of(part(12), part(caseId)), false),
                    "gpu_length", CompatibilityService.Status.INSUFFICIENT_DATA);
        }
        assertNotVerified(part(55), MAX_COOLER_HEIGHT_MM);
        assertNotVerified(part(68), HEIGHT_MM); // NH-D15 catalog name does not identify the 165/168mm revision
        assertFinding(compatibility.assess(List.of(part(62), part(68)), false),
                "cooler_height", CompatibilityService.Status.INSUFFICIENT_DATA);
    }

    @Test
    void completeRealBuildStillRequiresVerification() {
        var result = compatibility.assess(List.of(part(6), part(29), part(12), part(34),
                part(40), part(45), part(53), part(69)));
        assertFalse(result.hasConflict(), () -> result.findings().toString());
        assertEquals(CompatibilityService.Status.INSUFFICIENT_DATA, result.status());
        assertFinding(result, "cpu_support", CompatibilityService.Status.INSUFFICIENT_DATA);
        assertFinding(result, "power_budget", CompatibilityService.Status.INSUFFICIENT_DATA);
    }

    @Test
    void workstationSelectionUses32GbAndAllEightCategoriesWithinBudgetWithoutVerifiedConflicts() {
        BigDecimal budget = new BigDecimal("250000");
        var request = BuildRecommendationService.Request.forUsage(budget, BuildRecommendationService.Usage.WORKSTATION);
        var selected = new BuildRecommendationService().recommend(snapshot.components(), request);
        assertEquals(BuildRecommendationService.Status.NEEDS_VERIFICATION, selected.status(),
                "Unresolved board BIOS support and exact GPU power/dimensions must prevent READY");
        assertEquals(8, selected.components().size());
        assertEquals(EnumSet.allOf(ComponentCategory.class), selected.components().stream()
                .map(CatalogComponent::category).collect(Collectors.toSet()));
        assertFalse(selected.compatibility().hasConflict(), () -> selected.compatibility().findings().toString());
        assertTrue(selected.purchaseTotal().compareTo(budget) <= 0);
        assertEquals(0, selected.components().stream().map(CatalogComponent::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(selected.purchaseTotal()));
        var ram = selected.components().stream().filter(c -> c.category() == ComponentCategory.RAM).findFirst().orElseThrow();
        var capacity = ram.specs().fact(CAPACITY_GB).orElseThrow();
        assertTrue(capacity.verified());
        assertTrue(capacity.value() >= 32, () -> "Workstation selected insufficient RAM: " + ram.name());
        for (var drive : snapshot.components().stream().filter(c -> c.category() == ComponentCategory.STORAGE).toList()) {
            drive.specs().fact(STORAGE_INTERFACE).filter(Fact::verified).ifPresent(f ->
                    assertTrue(Set.of("NVMe", "SATA").contains(f.value()), "Storage protocols must use shared canonical values"));
        }
    }

    private void assertConflict(String code, int firstId, int secondId) {
        var result = compatibility.assess(List.of(part(firstId), part(secondId)), false);
        assertEquals(CompatibilityService.Status.INCOMPATIBLE, result.status(), code);
        assertFinding(result, code, CompatibilityService.Status.INCOMPATIBLE);
    }

    private static void assertFinding(CompatibilityService.Result result, String code, CompatibilityService.Status status) {
        assertTrue(result.findings().stream().anyMatch(f -> f.code().equals(code) && f.status() == status),
                () -> "Missing " + code + "=" + status + ": " + result.findings());
    }

    private static <T> void assertNotVerified(CatalogComponent component, Attribute<T> attribute) {
        assertFalse(component.specs().fact(attribute).map(Fact::verified).orElse(false),
                () -> "Unresolved " + attribute.name() + " was promoted for " + component.name());
    }

    private static CatalogComponent part(int id) {
        var component = components.get(id);
        assertNotNull(component, "Missing catalog component " + id);
        return component;
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static String string(JsonObject row, String key) {
        return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsString() : "";
    }
}
