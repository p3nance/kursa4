package com.example.authapp.domain;

import com.example.authapp.models.Product;
import com.example.authapp.services.BuildRecommendationService;
import com.example.authapp.services.ChatBotService;
import com.example.authapp.services.ComparisonService;
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
    private static final Path KNOWLEDGE = Path.of("src/main/resources/knowledge").toAbsolutePath();
    private static LocalKnowledgeRepository.Snapshot snapshot;
    private static Map<Integer, CatalogComponent> components;
    private static Map<Integer, Product> products;
    private static ComponentSpecsLoader.Dataset dataset;
    private final CompatibilityService compatibility = new CompatibilityService();

    @BeforeAll
    static void loadRealLocalCatalog() throws IOException {
        List<Product> catalog = new ArrayList<>();
        for (var item : readJson(Path.of("src/test/resources/knowledge/catalog-reference.json")).getAsJsonArray("products")) {
            JsonObject row = item.getAsJsonObject();
            catalog.add(new Product(row.get("id").getAsInt(), row.get("name").getAsString(),
                    string(row, "description"), row.get("price").getAsBigDecimal(), row.get("stock").getAsInt(),
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
    void mx500FactsAreBoundToTheTwoPointFiveInchCatalogDescription() {
        var drive=components.get(43);
        assertEquals("Crucial MX500 CT1000MX500SSD1",drive.specs().fact(MODEL_ID).filter(Fact::verified).orElseThrow().value());
        assertEquals(1000,drive.specs().fact(CAPACITY_GB).filter(Fact::verified).orElseThrow().value());
        assertEquals("SATA",drive.specs().fact(STORAGE_INTERFACE).filter(Fact::verified).orElseThrow().value());
        assertEquals("2.5-inch",drive.specs().fact(FORM_FACTOR).filter(Fact::verified).orElseThrow().value());
        var changed=products.values().stream().map(p->p.getId()==43?new Product(43,p.getName(),"M.2 Type 2280 SATA SSD 1TB",p.getPrice(),p.getStock(),p.getImageUrl(),p.getCategory(),p.getManufacturer()):p).toList();
        var loaded=new LocalKnowledgeRepository().load(changed);
        var other=loaded.components().stream().filter(c->c.productId()==43).findFirst().orElseThrow();
        assertFalse(other.specs().fact(FORM_FACTOR).filter(Fact::verified).isPresent());
        assertTrue(loaded.warnings().stream().anyMatch(w->w.contains("43")));
    }
    @Test
    void lancool215KeepsBodyBoundsSeparateFromUnknownAssemblyClearance() {
        var matrix=part(61).specs().fact(RADIATOR_MOUNTS).filter(Fact::verified).orElseThrow().value();
        assertEquals(RadiatorMountMatrix.Coverage.COMPLETE,matrix.coverage());assertEquals(4,matrix.rules().size());
        var top=matrix.rules().stream().filter(r->r.position()==RadiatorMountMatrix.Position.TOP&&r.nominalSizesMm().contains(280)).findFirst().orElseThrow();
        var front=matrix.rules().stream().filter(r->r.position()==RadiatorMountMatrix.Position.FRONT&&r.nominalSizesMm().contains(360)).findFirst().orElseThrow();
        assertEquals(55,top.radiatorThicknessUpperBoundMm());assertEquals(70,front.radiatorThicknessUpperBoundMm());
        assertTrue(matrix.rules().stream().allMatch(r->r.maxAssemblyThicknessMm()==null&&r.maxLengthMm()==null&&r.maxWidthMm()==null));
        assertTrue(matrix.rules().stream().filter(r->r.nominalSizesMm().contains(120)).allMatch(r->r.radiatorThicknessUpperBoundMm()==null));
        var result=compatibility.assess(List.of(part(61),part(71)),false);
        assertFinding(result,"radiator_fit",CompatibilityService.Status.INSUFFICIENT_DATA);
        assertTrue(result.findings().stream().anyMatch(f->f.code().equals("radiator_fit")&&f.message().toLowerCase(java.util.Locale.ROOT).contains("корзин")));
    }
    @Test
    void lancool215RejectsOnlyConfirmedBodyOrNominalExcesses() {
        var specs=builder();DomainFixtures.set(specs,COOLER_KIND,"AIO");DomainFixtures.set(specs,RADIATOR_NOMINAL_MM,280);
        DomainFixtures.set(specs,RADIATOR_THICKNESS_MM,71);
        var over=DomainFixtures.part(9001,ComponentCategory.COOLER,specs.build());
        assertFinding(compatibility.assess(List.of(part(61),over),false),"radiator_fit",CompatibilityService.Status.INCOMPATIBLE);
        DomainFixtures.set(specs,RADIATOR_THICKNESS_MM,50);DomainFixtures.set(specs,RADIATOR_FAN_THICKNESS_MM,50);
        var ambiguousStack=DomainFixtures.part(9001,ComponentCategory.COOLER,specs.build());
        assertFinding(compatibility.assess(List.of(part(61),ambiguousStack),false),"radiator_fit",CompatibilityService.Status.INSUFFICIENT_DATA);
        DomainFixtures.set(specs,RADIATOR_NOMINAL_MM,120);DomainFixtures.set(specs,RADIATOR_THICKNESS_MM,100);
        assertFinding(compatibility.assess(List.of(part(61),DomainFixtures.part(9001,ComponentCategory.COOLER,specs.build())),false),"radiator_fit",CompatibilityService.Status.INSUFFICIENT_DATA);
        DomainFixtures.set(specs,RADIATOR_NOMINAL_MM,420);
        assertFinding(compatibility.assess(List.of(part(61),DomainFixtures.part(9001,ComponentCategory.COOLER,specs.build())),false),"radiator_fit",CompatibilityService.Status.INCOMPATIBLE);
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
        assertEquals(232,verifiedFacts,"R39 adds eight scoped case mount matrices without inferring full physical fit");
    }

    @Test
    void actualCatalogPairsExposeVerifiedCompatibilityConflicts() {
        assertConflict("socket", 6, 26);           // AM5 CPU and LGA1700 motherboard
        assertConflict("memory_type", 26, 34);    // DDR4 motherboard and DDR5 memory kit
        assertConflict("gpu_min_psu", 16, 45);    // RTX 4090 reference minimum 850W and a 750W PSU
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
    void coolerTypeAndNominalClassLeaveUnconfirmedDimensionsAndMountingKitsUnknown() {
        assertEquals("AIR",part(68).specs().fact(COOLER_KIND).filter(Fact::verified).orElseThrow().value());
        assertEquals("AIO",part(72).specs().fact(COOLER_KIND).filter(Fact::verified).orElseThrow().value());
        assertEquals(360,part(72).specs().fact(RADIATOR_NOMINAL_MM).filter(Fact::verified).orElseThrow().value());
        for(int coolerId:List.of(68,72)) {
            assertNotVerified(part(coolerId),HEIGHT_MM);
            assertNotVerified(part(coolerId),SUPPORTED_SOCKETS);
            assertNotVerified(part(coolerId),COOLING_WATTS);
        }
        assertNotVerified(part(68),RADIATOR_NOMINAL_MM);
        var aio=compatibility.assess(List.of(part(62),part(72)),false);
        assertFinding(aio,"radiator_fit",CompatibilityService.Status.INSUFFICIENT_DATA);
        assertTrue(aio.findings().stream().noneMatch(f->f.code().equals("cooler_height")),
                "A360mm nominal class and ProductHeight must not be tested against air-cooler clearance");
        var unresolvedAir=compatibility.assess(List.of(part(62),part(68)),false);
        assertFinding(unresolvedAir,"cooler_height",CompatibilityService.Status.INSUFFICIENT_DATA);
    }

    @Test
    void originalCapellixHasDedicatedRadiatorDimensionsButNoInventedFanStack() {
        var aio=part(72);
        assertEquals(397,aio.specs().fact(RADIATOR_LENGTH_MM).filter(Fact::verified).orElseThrow().value());
        assertEquals(120,aio.specs().fact(RADIATOR_WIDTH_MM).filter(Fact::verified).orElseThrow().value());
        assertEquals(27,aio.specs().fact(RADIATOR_THICKNESS_MM).filter(Fact::verified).orElseThrow().value());
        assertEquals(360,aio.specs().fact(RADIATOR_NOMINAL_MM).filter(Fact::verified).orElseThrow().value());
        assertNotVerified(aio,RADIATOR_FAN_THICKNESS_MM);
        assertNotVerified(aio,HEIGHT_MM);
        assertNotVerified(aio,SUPPORTED_SOCKETS);
        assertNotVerified(aio,COOLING_WATTS);
        assertFinding(compatibility.assess(List.of(part(61),aio),false),"radiator_fit",CompatibilityService.Status.INSUFFICIENT_DATA);
        assertFinding(compatibility.assess(List.of(part(54),aio),false),"radiator_fit",CompatibilityService.Status.INCOMPATIBLE);
    }

    @Test
    void capellixUses397MillimeterBodyAndStillNeedsAFanStackAtTheBoundary() {
        for(int limit:List.of(396,397)) {
            var chassis=builder();
            DomainFixtures.set(chassis,RADIATOR_MOUNTS,new RadiatorMountMatrix(RadiatorMountMatrix.Coverage.COMPLETE,
                    List.of(new RadiatorMountMatrix.Rule(RadiatorMountMatrix.Position.FRONT,Set.of(360),limit,120,60,List.of()))));
            var result=compatibility.assess(List.of(DomainFixtures.part(9002,ComponentCategory.CASE,chassis.build()),part(72)),false);
            assertFinding(result,"radiator_fit",limit==396?CompatibilityService.Status.INCOMPATIBLE:CompatibilityService.Status.INSUFFICIENT_DATA);
        }
        var reply=new ComparisonService(snapshot,products.values()).compare(part(71),part(72),true,"").reply();
        var card=assertInstanceOf(ChatBotService.SpecificationsContent.class,reply.content().getFirst());
        assertEquals("397 мм",card.rows().stream().filter(r->r.label().equals("Фактическая длина радиатора")).findFirst().orElseThrow().right());
    }

    @Test
    void officiallyVerifiedAirTypesExposeRealHeightConflictsWithoutVerifyingMountingKits() {
        for(int coolerId:List.of(69,70)) {
            assertEquals("AIR",part(coolerId).specs().fact(COOLER_KIND).filter(Fact::verified).orElseThrow().value());
            assertTrue(part(coolerId).specs().fact(HEIGHT_MM).filter(Fact::verified).isPresent());
            assertNotVerified(part(coolerId),SUPPORTED_SOCKETS);
            assertNotVerified(part(coolerId),COOLING_WATTS);
            var result=compatibility.assess(List.of(part(62),part(coolerId)),false);
            assertFinding(result,"cooler_height",CompatibilityService.Status.INCOMPATIBLE);
            assertTrue(result.hasConflict(),"160/163mm air coolers exceed the O11 Dynamic's verified155mm limit");
            assertTrue(result.findings().stream().noneMatch(f->f.code().equals("cooler_kind")));
        }
    }

    @Test
    void krakenRgb2023IsAioButStillNeedsRadiatorMountingVerification() {
        assertEquals("AIO",part(71).specs().fact(COOLER_KIND).filter(Fact::verified).orElseThrow().value());
        assertNotVerified(part(71),HEIGHT_MM);
        assertEquals(360,part(71).specs().fact(RADIATOR_NOMINAL_MM).filter(Fact::verified).orElseThrow().value());
        assertEquals(394,part(71).specs().fact(RADIATOR_LENGTH_MM).filter(Fact::verified).orElseThrow().value());
        assertEquals(121,part(71).specs().fact(RADIATOR_WIDTH_MM).filter(Fact::verified).orElseThrow().value());
        assertEquals(30,part(71).specs().fact(RADIATOR_THICKNESS_MM).filter(Fact::verified).orElseThrow().value());
        assertEquals(26,part(71).specs().fact(RADIATOR_FAN_THICKNESS_MM).filter(Fact::verified).orElseThrow().value());
        assertNotVerified(part(71),COOLING_WATTS);
        var result=compatibility.assess(List.of(part(62),part(71)),false);
        assertFinding(result,"radiator_fit",CompatibilityService.Status.INSUFFICIENT_DATA);
        assertTrue(result.findings().stream().noneMatch(f->f.code().equals("cooler_height")));
        assertFalse(result.hasConflict());
    }

    @Test
    void popAirRejects360AtEveryStockLocationButDoesNotPretendToKnowFullClearances() {
        var matrix=part(54).specs().fact(RADIATOR_MOUNTS).filter(Fact::verified).orElseThrow().value();
        assertEquals(RadiatorMountMatrix.Coverage.COMPLETE,matrix.coverage());
        assertTrue(matrix.rules().stream().allMatch(r->r.maxLengthMm()==null && r.maxAssemblyThicknessMm()==null));
        assertTrue(matrix.rules().stream().allMatch(r->!r.nominalSizesMm().contains(360)));
        for(int coolerId:List.of(71,72)) {
            var result=compatibility.assess(List.of(part(54),part(coolerId)),false);
            assertFinding(result,"radiator_fit",CompatibilityService.Status.INCOMPATIBLE);
            assertTrue(result.findings().stream().noneMatch(f->f.code().equals("cooler_height")));
        }
        var changedKind=builder();changedKind.set(COOLER_KIND,"AIO","https://example.test/fixture",Verification.VERIFIED);
        changedKind.set(RADIATOR_NOMINAL_MM,240,"https://example.test/fixture",Verification.VERIFIED);
        var synthetic=DomainFixtures.part(999,ComponentCategory.COOLER,changedKind.build());
        assertFinding(compatibility.assess(List.of(part(54),synthetic),false),"radiator_fit",CompatibilityService.Status.INSUFFICIENT_DATA);
    }

    @Test
    void radiatorComparisonSeparatesActualGeometryFromNominalAndAirHeight() {
        var reply=new ComparisonService(snapshot,products.values()).compare(part(69),part(71),true,"").reply();
        var card=assertInstanceOf(ChatBotService.SpecificationsContent.class,reply.content().getFirst());
        var length=card.rows().stream().filter(r->r.label().equals("Фактическая длина радиатора")).findFirst().orElseThrow();
        assertEquals("Не применяется",length.left());assertEquals("394 мм",length.right());
        var nominal=card.rows().stream().filter(r->r.label().equals("Номинальный класс радиатора")).findFirst().orElseThrow();
        assertEquals("360 мм",nominal.right());
        var height=card.rows().stream().filter(r->r.label().equals("Высота воздушного кулера")).findFirst().orElseThrow();
        assertEquals("Не применяется к СЖО",height.right());
        var cases=new ComparisonService(snapshot,products.values()).compare(part(54),part(62),true,"").reply();
        var mounts=assertInstanceOf(ChatBotService.SpecificationsContent.class,cases.content().getFirst()).rows().stream()
                .filter(r->r.label().equals("Крепления радиаторов")).findFirst().orElseThrow();
        assertTrue(mounts.left().contains("Спереди: 120/140/240/280 мм"));
        assertTrue(mounts.left().contains("зазоры проверяются отдельно"));
        assertTrue(mounts.right().contains("Сверху: 280/360 мм"));assertTrue(mounts.right().contains("Перечень позиций частичный"));
    }

    @Test
    void reviewedCasesSeparateCompletePositionListsFromPartialAndUnknownVariants() {
        for(int id:List.of(53,59,60,63))assertEquals(RadiatorMountMatrix.Coverage.COMPLETE,
                part(id).specs().fact(RADIATOR_MOUNTS).filter(Fact::verified).orElseThrow().value().coverage());
        for(int id:List.of(57,58,62,64))assertEquals(RadiatorMountMatrix.Coverage.PARTIAL,
                part(id).specs().fact(RADIATOR_MOUNTS).filter(Fact::verified).orElseThrow().value().coverage());
        for(int id:List.of(55,56,65,66))assertNotVerified(part(id),RADIATOR_MOUNTS);
        for(int id:List.of(53,57,58,59,60,62,63,64))assertTrue(part(id).specs().value(RADIATOR_MOUNTS).orElseThrow().rules().stream()
                .allMatch(r->r.maxAssemblyThicknessMm()==null&&r.radiatorThicknessUpperBoundMm()==null),
                "RAM36/42mm and unqualified case clearances must not become radiator thickness limits");
    }

    @Test
    void meshifyWidthBoundIsScopedToTheTop140SeriesAndDoesNotCertifyA143MillimeterFit() {
        var matrix=part(53).specs().value(RADIATOR_MOUNTS).orElseThrow();
        var wide=matrix.rules().stream().filter(r->r.nominalSizesMm().contains(420)).findFirst().orElseThrow();
        assertEquals(143,wide.maxWidthMm());assertTrue(wide.unresolvedConditions().stream().anyMatch(c->c.contains("Open Layout")));
        assertTrue(matrix.rules().stream().filter(r->r.nominalSizesMm().contains(360)).allMatch(r->r.maxWidthMm()==null));
        var specs=builder();DomainFixtures.set(specs,COOLER_KIND,"AIO");DomainFixtures.set(specs,RADIATOR_NOMINAL_MM,420);
        DomainFixtures.set(specs,RADIATOR_WIDTH_MM,143);
        assertFinding(compatibility.assess(List.of(part(53),DomainFixtures.part(9003,ComponentCategory.COOLER,specs.build())),false),
                "radiator_fit",CompatibilityService.Status.INSUFFICIENT_DATA);
        DomainFixtures.set(specs,RADIATOR_WIDTH_MM,144);
        assertFinding(compatibility.assess(List.of(part(53),DomainFixtures.part(9003,ComponentCategory.COOLER,specs.build())),false),
                "radiator_fit",CompatibilityService.Status.INCOMPATIBLE);
    }

    @Test
    void completeCaseListsRejectUnsupportedClassesWhilePartialListsAndReal360PairsStayConservative() {
        var specs=builder();DomainFixtures.set(specs,COOLER_KIND,"AIO");DomainFixtures.set(specs,RADIATOR_NOMINAL_MM,420);
        var aio=DomainFixtures.part(9004,ComponentCategory.COOLER,specs.build());
        for(int id:List.of(59,63))assertFinding(compatibility.assess(List.of(part(id),aio),false),"radiator_fit",CompatibilityService.Status.INCOMPATIBLE);
        assertFinding(compatibility.assess(List.of(part(60),aio),false),"radiator_fit",CompatibilityService.Status.INSUFFICIENT_DATA);
        DomainFixtures.set(specs,RADIATOR_NOMINAL_MM,480);
        assertFinding(compatibility.assess(List.of(part(60),DomainFixtures.part(9004,ComponentCategory.COOLER,specs.build())),false),"radiator_fit",CompatibilityService.Status.INCOMPATIBLE);
        DomainFixtures.set(specs,RADIATOR_NOMINAL_MM,240);
        for(int id:List.of(62,64))assertFinding(compatibility.assess(List.of(part(id),DomainFixtures.part(9004,ComponentCategory.COOLER,specs.build())),false),
                "radiator_fit",CompatibilityService.Status.INSUFFICIENT_DATA);
        for(int id:List.of(53,57,58,59,60,62,63,64,65,66))for(int cooler:List.of(71,72))
            assertFinding(compatibility.assess(List.of(part(id),part(cooler)),false),"radiator_fit",CompatibilityService.Status.INSUFFICIENT_DATA);
    }

    @Test
    void coolerSpecificationComparisonShowsTranslatedTypeAndAnExplicitNominalRadiatorClass() {
        var reply=new ComparisonService(snapshot,products.values()).compare(part(68),part(72),true,"").reply();
        var card=assertInstanceOf(ChatBotService.SpecificationsContent.class,reply.content().getFirst());
        var kind=card.rows().stream().filter(r->r.label().equals("Тип охлаждения")).findFirst().orElseThrow();
        assertEquals("Воздушное",kind.left());assertEquals("Жидкостное (AIO)",kind.right());
        var nominal=card.rows().stream().filter(r->r.label().equals("Номинальный класс радиатора")).findFirst().orElseThrow();
        assertEquals("Не применяется",nominal.left());assertEquals("360 мм",nominal.right());
        var height=card.rows().stream().filter(r->r.label().equals("Высота воздушного кулера")).findFirst().orElseThrow();
        assertEquals("Не подтверждено",height.left());assertEquals("Не применяется к СЖО",height.right());
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
