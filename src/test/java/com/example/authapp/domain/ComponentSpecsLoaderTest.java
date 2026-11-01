package com.example.authapp.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.authapp.domain.ComponentSpecs.*;

class ComponentSpecsLoaderTest {
    @TempDir Path directory;
    private Path file(String body) throws IOException {return Files.writeString(directory.resolve("specs.json"),body);}
    @Test void loadsTypedVerifiedFactWithSource() throws Exception {
        var facts=new ComponentSpecsLoader().load(file("""
                {"version":1,"products":[{"productId":1,"facts":{
                  "socket":{"value":"AM5","source":"https://example.test/fixture","verification":"VERIFIED"},
                  "max_power_watts":{"value":120,"source":"https://example.test/fixture","verification":"VERIFIED"}
                }}]}
                """));
        assertEquals("AM5",facts.get(1).value(SOCKET).orElseThrow());
        assertEquals(120,facts.get(1).value(MAX_POWER_WATTS).orElseThrow());
        assertTrue(facts.get(1).fact(SOCKET).orElseThrow().verified());
    }
    @Test void rejectsWrongTypeUnknownAttributeAndMissingProvenance() {
        assertThrows(IOException.class,()->new ComponentSpecsLoader().load(file("""
                {"version":1,"products":[{"productId":1,"facts":{"psu_watts":{"value":"650","source":"manual","verification":"VERIFIED"}}}]}
                """)));
        assertThrows(IOException.class,()->new ComponentSpecsLoader().load(file("""
                {"version":1,"products":[{"productId":1,"facts":{"imagined_field":{"value":1,"source":"manual","verification":"VERIFIED"}}}]}
                """)));
        assertThrows(IOException.class,()->new ComponentSpecsLoader().load(file("""
                {"version":1,"products":[{"productId":1,"facts":{"socket":{"value":"AM5","source":"","verification":"VERIFIED"}}}]}
                """)));
    }
    @Test void rejectsDuplicateProductAndFractionalInteger() {
        assertThrows(IOException.class,()->new ComponentSpecsLoader().load(file("""
                {"version":1,"products":[{"productId":1,"facts":{}},{"productId":1,"facts":{}}]}
                """)));
        assertThrows(IOException.class,()->new ComponentSpecsLoader().load(file("""
                {"version":1,"products":[{"productId":1,"facts":{"psu_watts":{"value":650.5,"source":"manual","verification":"IMPORTED"}}}]}
                """)));
    }
    @Test void renamedIdDoesNotReceiveFactsFromAnotherModel() throws Exception {
        var loader=new ComponentSpecsLoader();
        var data=loader.loadDataset(file("""
                {"version":1,"products":[{"productId":6,"catalogName":"AMD Ryzen 5 7600","catalogCategory":"CPU","facts":{
                "socket":{"value":"AM5","source":"https://example.test/fixture","verification":"VERIFIED"}}}]}
                """));
        var current=new CatalogComponent(6,"Intel Core i5-12400F",ComponentCategory.CPU,BigDecimal.ONE,1,empty());
        var result=loader.applyValidated(List.of(current),data);
        assertTrue(result.components().getFirst().specs().fact(SOCKET).isEmpty());
        assertEquals(1,result.warnings().size());
        var wrongCategory=new CatalogComponent(6,"AMD Ryzen 5 7600",ComponentCategory.GPU,BigDecimal.ONE,1,empty());
        assertFalse(loader.applyValidated(List.of(wrongCategory),data).warnings().isEmpty());
    }
    @Test void identityAllowsOnlyCaseAndWhitespaceNormalization() throws Exception {
        var loader=new ComponentSpecsLoader();
        var data=loader.loadDataset(file("""
                {"version":1,"products":[{"productId":6,"catalogName":"AMD Ryzen 5 7600","catalogCategory":"Процессоры","facts":{
                "socket":{"value":"AM5","source":"https://example.test/fixture","verification":"VERIFIED"}}}]}
                """));
        var matching=new CatalogComponent(6,"  amd   Ryzen 5 7600  ",ComponentCategory.CPU,BigDecimal.ONE,1,empty());
        var result=loader.applyValidated(List.of(matching),data);
        assertTrue(result.warnings().isEmpty());assertEquals("AM5",result.components().getFirst().specs().value(SOCKET).orElseThrow());
        var differentSku=new CatalogComponent(6,"AMD Ryzen 5 7600X",ComponentCategory.CPU,BigDecimal.ONE,1,empty());
        assertFalse(loader.applyValidated(List.of(differentSku),data).warnings().isEmpty());
    }
    @Test void variantDescriptionBindingFailsClosedWhenMissingOrChanged() throws Exception {
        var loader=new ComponentSpecsLoader();
        var data=loader.loadDataset(file("""
                {"version":2,"products":[{"productId":43,"catalogName":"Example 1TB SATA","catalogCategory":"SSD-накопители",
                "catalogDescription":"2.5-inch SATA SSD","facts":{"form_factor":{"value":"2.5-inch","source":"https://example.test/manual","verification":"VERIFIED"}}}]}
                """));
        var component=new CatalogComponent(43,"Example 1TB SATA",ComponentCategory.STORAGE,BigDecimal.ONE,1,empty());
        assertTrue(loader.applyValidated(List.of(component),data).components().getFirst().specs().fact(FORM_FACTOR).isEmpty());
        var matching=loader.applyValidated(List.of(component),data,java.util.Map.of(43,"  2.5-inch   SATA SSD  "));
        assertTrue(matching.warnings().isEmpty());assertEquals("2.5-inch",matching.components().getFirst().specs().value(FORM_FACTOR).orElseThrow());
        var changed=loader.applyValidated(List.of(component),data,java.util.Map.of(43,"M.2 Type 2280 SATA SSD"));
        assertFalse(changed.warnings().isEmpty());assertTrue(changed.components().getFirst().specs().fact(FORM_FACTOR).isEmpty());
    }
    @Test void variantDescriptionCannotBeSilentlyEncodedAsLegacyVersionOne() {
        for(String description:List.of("\"2.5-inch SATA SSD\"","null","25","[]","\"\"")) {
            String json="{\"version\":1,\"products\":[{\"productId\":43,\"catalogName\":\"Example\",\"catalogDescription\":"+description+",\"facts\":{}}]}";
            assertThrows(IOException.class,()->new ComponentSpecsLoader().load(file(json)));
        }
    }
    @Test void loadsExplicitCoolerKindAndNominalRadiatorClassWithoutInventingHeight() throws Exception {
        var facts=new ComponentSpecsLoader().load(file("""
                {"version":1,"products":[{"productId":1,"facts":{
                  "cooler_kind":{"value":"AIO","source":"https://example.test/fixture","verification":"VERIFIED"},
                  "radiator_nominal_mm":{"value":360,"source":"https://example.test/fixture","verification":"VERIFIED"}
                }}]}
                """));
        assertEquals("AIO",facts.get(1).value(COOLER_KIND).orElseThrow());
        assertEquals(360,facts.get(1).value(RADIATOR_NOMINAL_MM).orElseThrow());assertTrue(facts.get(1).fact(HEIGHT_MM).isEmpty());
    }
    @Test void invalidCoolerKindsAndUntypedRadiatorNumbersAreRejected() {
        for(String invalid:List.of("LIQUID","UNKNOWN","air","")) {
            String json="{\"version\":1,\"products\":[{\"productId\":1,\"facts\":{\"cooler_kind\":{\"value\":\""+invalid+"\",\"source\":\"fixture\",\"verification\":\"IMPORTED\"}}}]}";
            assertThrows(IOException.class,()->new ComponentSpecsLoader().load(file(json)),invalid);
        }
        for(String invalid:List.of("\"360\"","360.5","0","-360")) {
            String json="{\"version\":1,\"products\":[{\"productId\":1,\"facts\":{\"radiator_nominal_mm\":{\"value\":"+invalid+",\"source\":\"fixture\",\"verification\":\"IMPORTED\"}}}]}";
            assertThrows(IOException.class,()->new ComponentSpecsLoader().load(file(json)),invalid);
        }
        assertThrows(IllegalArgumentException.class,()->builder().set(COOLER_KIND,"LIQUID","fixture",Verification.VERIFIED));
    }
    @Test void legacyHeightOnlyDataDoesNotAcquireAnAirType() throws Exception {
        var facts=new ComponentSpecsLoader().load(file("""
                {"version":1,"products":[{"productId":1,"facts":{
                  "height_mm":{"value":155,"source":"https://example.test/fixture","verification":"VERIFIED"}
                }}]}
                """));
        assertEquals(155,facts.get(1).value(HEIGHT_MM).orElseThrow());assertTrue(facts.get(1).fact(COOLER_KIND).isEmpty());
    }

    private String matrixJson(String value) {
        return "{\"version\":1,\"products\":[{\"productId\":1,\"facts\":{\"radiator_mounts\":{\"value\":"+value
                +",\"source\":\"https://example.test/matrix\",\"verification\":\"VERIFIED\"}}}]}";
    }
    private static final String RULE="""
            {"position":"TOP","nominalSizesMm":[120,240],"maxWidthMm":121,"unresolvedConditions":["Check RAM and VRM"]}
            """.strip();
    @Test void mountingMatrixRetainsCoverageSeparateFromMissingPhysicalLimits() throws Exception {
        var specs=new ComponentSpecsLoader().load(file(matrixJson("{\"coverage\":\"COMPLETE\",\"rules\":["+RULE+"]}"))).get(1);
        var matrix=specs.fact(RADIATOR_MOUNTS).filter(Fact::verified).orElseThrow().value();
        assertEquals(RadiatorMountMatrix.Coverage.COMPLETE,matrix.coverage());
        var rule=matrix.rules().getFirst();assertEquals(121,rule.maxWidthMm());assertNull(rule.maxLengthMm());
        assertNull(rule.maxAssemblyThicknessMm());assertEquals(List.of("Check RAM and VRM"),rule.unresolvedConditions());
        assertThrows(UnsupportedOperationException.class,()->matrix.rules().clear());
        assertThrows(UnsupportedOperationException.class,()->rule.nominalSizesMm().clear());
    }
    @Test void mountingMatrixCannotDefaultMissingCoverageOrUnresolvedConditionsToSafe() {
        for(String value:List.of("{\"rules\":["+RULE+"]}",
                "{\"coverage\":\"COMPLETE\",\"rules\":["+RULE.replace(",\"unresolvedConditions\":[\"Check RAM and VRM\"]","")+"]}",
                "{\"coverage\":\"YES\",\"rules\":["+RULE+"]}"))
            assertThrows(IOException.class,()->new ComponentSpecsLoader().load(file(matrixJson(value))));
    }
    @Test void mountingMatrixRejectsAmbiguousRulesAndInvalidUnits() {
        for(String rule:List.of(RULE.replace("121","0"),RULE.replace("121","121.5"),RULE.replace("121","\"121\""),
                RULE.replace("[120,240]","[120,120]"),RULE.replace("[120,240]","[]"),RULE.replace("TOP","ROOF"),
                RULE.replace("Check RAM and VRM",""),RULE+","+RULE))
            assertThrows(IOException.class,()->new ComponentSpecsLoader().load(file(matrixJson("{\"coverage\":\"COMPLETE\",\"rules\":["+rule+"]}"))),rule);
    }
    @Test void bodyThicknessBoundRequiresVersionThreeAndKeepsAssemblyUnknown() throws Exception {
        String rule=RULE.replace("\"maxWidthMm\":121","\"maxWidthMm\":121,\"radiatorThicknessUpperBoundMm\":55");
        String json=matrixJson("{\"coverage\":\"COMPLETE\",\"rules\":["+rule+"]}");
        for(String version:List.of("1","2"))assertThrows(IOException.class,()->new ComponentSpecsLoader().load(file(json.replace("\"version\":1","\"version\":"+version))));
        var value=new ComponentSpecsLoader().load(file(json.replace("\"version\":1","\"version\":3"))).get(1).value(RADIATOR_MOUNTS).orElseThrow().rules().getFirst();
        assertEquals(55,value.radiatorThicknessUpperBoundMm());assertNull(value.maxAssemblyThicknessMm());
        for(String bad:List.of("0","-1","55.5","\"55\""))assertThrows(IOException.class,()->new ComponentSpecsLoader().load(file(json.replace("\"version\":1","\"version\":3").replace("\"radiatorThicknessUpperBoundMm\":55","\"radiatorThicknessUpperBoundMm\":"+bad))));
    }

}
