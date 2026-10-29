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
}
