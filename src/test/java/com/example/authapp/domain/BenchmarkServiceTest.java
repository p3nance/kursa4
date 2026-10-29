package com.example.authapp.domain;

import com.example.authapp.services.BenchmarkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkServiceTest {
    @TempDir Path directory;
    private BenchmarkService.Context context(String resolution,BenchmarkService.Direction direction) {
        return new BenchmarkService.Context("SYNTHETIC_FIXTURE","1","test-game","average fps","fps",direction,
                Map.of("resolution",resolution,"preset","test","testSystem","fixture-rig","os","test","driver","test"));
    }
    private BenchmarkService.Result result(String model,int value,BenchmarkService.Context context) {
        return new BenchmarkService.Result(model,context,BigDecimal.valueOf(value),URI.create("https://example.test/synthetic-fixture"),LocalDate.of(2026,1,1));
    }
    @Test void onlyIdenticalContextIsCompared() {
        var service=new BenchmarkService();
        assertTrue(service.compare(result("A",100,context("1080p",BenchmarkService.Direction.HIGHER_IS_BETTER)),
                result("B",150,context("1440p",BenchmarkService.Direction.HIGHER_IS_BETTER))).isEmpty());
        var c=context("1080p",BenchmarkService.Direction.HIGHER_IS_BETTER);
        var comparison=service.compare(result("A",120,c),result("B",100,c)).orElseThrow();
        assertEquals("A",comparison.betterModelId());assertEquals(new BigDecimal("20.00"),comparison.differencePercent());
    }
    @Test void lowerIsBetterAndTiesAreExplicit() {
        var c=context("fixture",BenchmarkService.Direction.LOWER_IS_BETTER);var service=new BenchmarkService();
        assertEquals("B",service.compare(result("A",120,c),result("B",100,c)).orElseThrow().betterModelId());
        assertEquals("",service.compare(result("A",100,c),result("B",100,c)).orElseThrow().betterModelId());
    }
    @Test void rejectsMissingSourceAndZero() {
        var c=context("1080p",BenchmarkService.Direction.HIGHER_IS_BETTER);
        assertThrows(IllegalArgumentException.class,()->new BenchmarkService.Result("A",c,BigDecimal.ONE,URI.create("file:///tmp/fixture"),LocalDate.now()));
        assertThrows(IllegalArgumentException.class,()->result("A",0,c));
    }
    @Test void malformedImportIsAtomicAndNoScoresAreBundled() throws Exception {
        var path=Files.writeString(directory.resolve("benchmarks.json"),"{\"version\":1,\"results\":[{}]}");
        assertThrows(IOException.class,()->new BenchmarkService().load(path));
        assertTrue(new BenchmarkService().resultsFor("anything").isEmpty());
    }
    @Test void legacyMeasuredDateRemainsSupported() throws Exception {
        var imported=new BenchmarkService().load(file(row("A","\"measuredAt\":\"2026-01-01\"")));
        assertEquals(LocalDate.of(2026,1,1),imported.getFirst().measuredAt());
        assertNull(imported.getFirst().publishedAt());
        assertEquals("измерено 2026-01-01",imported.getFirst().dateDescription());
        assertEquals("измерено 2026-01-01",result("A",100,context("fixture",BenchmarkService.Direction.HIGHER_IS_BETTER)).dateDescription());
    }
    @Test void publicationDateDoesNotBecomeAnInventedMeasurementDate() throws Exception {
        var imported=new BenchmarkService().load(file(
                row("A","\"publishedAt\":\"2026-01-03\""),
                row("B","\"measuredAt\":null,\"publishedAt\":\"2026-01-03\"")));
        for(var item:imported) {
            assertNull(item.measuredAt());
            assertEquals(LocalDate.of(2026,1,3),item.publishedAt());
            assertEquals("опубликовано 2026-01-03; точная дата измерения не указана",item.dateDescription());
        }
        var both=new BenchmarkService.Result("A",context("fixture",BenchmarkService.Direction.HIGHER_IS_BETTER),
                BigDecimal.ONE,URI.create("https://example.test/synthetic-fixture"),LocalDate.of(2026,1,1),LocalDate.of(2026,1,3));
        assertEquals("измерено 2026-01-01; опубликовано 2026-01-03",both.dateDescription());
    }
    @Test void missingOrInvalidDatesRejectTheEntireImport() throws Exception {
        var valid=row("A","\"measuredAt\":\"2026-01-01\"");
        for(String dates:List.of("\"measuredAt\":null", "\"measuredAt\":null,\"publishedAt\":null",
                "\"publishedAt\":\"2026-02-30\"")) {
            assertThrows(IOException.class,()->new BenchmarkService().load(file(valid,row("B",dates))));
        }
        assertThrows(IllegalArgumentException.class,()->new BenchmarkService.Result("A",
                context("fixture",BenchmarkService.Direction.HIGHER_IS_BETTER),BigDecimal.ONE,
                URI.create("https://example.test/synthetic-fixture"),null,null));
        assertThrows(IOException.class,()->new BenchmarkService().load(file(row("B","\"note\":\"neither date supplied\""))));
    }
    @Test void duplicateIdentityIncludesBothDatesAndIgnoresModelCase() throws Exception {
        var first=row("A","\"measuredAt\":\"2026-01-01\",\"publishedAt\":\"2026-01-03\"");
        assertThrows(IOException.class,()->new BenchmarkService().load(file(first,first)));
        assertThrows(IOException.class,()->new BenchmarkService().load(file(first,
                row("a","\"measuredAt\":\"2026-01-01\",\"publishedAt\":\"2026-01-03\""))));
        assertEquals(2,new BenchmarkService().load(file(first,
                row("A","\"measuredAt\":\"2026-01-01\",\"publishedAt\":\"2026-01-04\""))).size());
    }
    @Test void differentReviewCohortsOrDriversCannotBeCompared() {
        var service=new BenchmarkService();
        var conditions=new HashMap<>(Map.of("reviewCohort","synthetic-batch-1","os","fixture-os","driver","fixture-driver-1"));
        var first=new BenchmarkService.Context("SYNTHETIC_FIXTURE","1","render","score","points",
                BenchmarkService.Direction.HIGHER_IS_BETTER,conditions);
        for(String key:List.of("reviewCohort","driver","os")) {
            var changed=new HashMap<>(conditions);changed.put(key,"different-value");
            var second=new BenchmarkService.Context("SYNTHETIC_FIXTURE","1","render","score","points",
                    BenchmarkService.Direction.HIGHER_IS_BETTER,changed);
            assertTrue(service.compare(result("A",120,first),result("B",100,second)).isEmpty(),key);
        }
    }
    private Path file(String... rows) throws IOException {
        return Files.writeString(directory.resolve("benchmarks.json"),"{\"version\":1,\"results\":["+String.join(",",rows)+"]}");
    }
    private String row(String model,String dates) {
        return """
                {"modelId":"%s","context":{"suite":"SYNTHETIC_FIXTURE","version":"1","workload":"render",
                "metric":"score","unit":"points","direction":"HIGHER_IS_BETTER",
                "conditions":{"reviewCohort":"synthetic-batch-1","os":"fixture-os","driver":"fixture-driver"}},
                "value":100,"source":"https://example.test/synthetic-fixture",%s}
                """.formatted(model,dates);
    }
}
