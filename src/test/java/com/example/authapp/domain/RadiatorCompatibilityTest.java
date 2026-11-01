package com.example.authapp.domain;

import com.example.authapp.services.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static com.example.authapp.domain.ComponentSpecs.*;
import static com.example.authapp.domain.RadiatorMountMatrix.*;
import static com.example.authapp.services.CompatibilityService.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class RadiatorCompatibilityTest {
    private final CompatibilityService service=new CompatibilityService();
    private Rule rule(Position position,Integer length,Integer width,Integer stack,String... conditions) {
        return new Rule(position,Set.of(360),length,width,stack,List.of(conditions));
    }
    private CatalogComponent chassis(Coverage coverage,Rule... rules) {
        var b=builder();DomainFixtures.set(b,RADIATOR_MOUNTS,new RadiatorMountMatrix(coverage,List.of(rules)));
        return DomainFixtures.part(90,ComponentCategory.CASE,b.build());
    }
    private Builder aio() {
        var b=builder();DomainFixtures.set(b,COOLER_KIND,"AIO");DomainFixtures.set(b,RADIATOR_NOMINAL_MM,360);
        DomainFixtures.set(b,RADIATOR_LENGTH_MM,394);DomainFixtures.set(b,RADIATOR_WIDTH_MM,121);
        DomainFixtures.set(b,RADIATOR_THICKNESS_MM,30);DomainFixtures.set(b,RADIATOR_FAN_THICKNESS_MM,26);
        DomainFixtures.set(b,HEIGHT_MM,10); // A low pump block cannot establish radiator fit.
        DomainFixtures.set(b,SUPPORTED_SOCKETS,Set.of("AM5"));DomainFixtures.set(b,COOLING_WATTS,150);
        return b;
    }
    private CatalogComponent cooler(Builder specs) { return DomainFixtures.part(91,ComponentCategory.COOLER,specs.build()); }
    private CompatibilityService.Finding fit(CatalogComponent chassis,CatalogComponent cooler) {
        return service.assess(List.of(chassis,cooler),false).findings().stream()
                .filter(f->f.code().equals("radiator_fit")).findFirst().orElseThrow();
    }
    @Test void matchingNominalClassCannotSubstituteForTheActualRadiatorLength() {
        var result=fit(chassis(Coverage.COMPLETE,rule(Position.TOP,360,125,60)),cooler(aio()));
        assertEquals(INCOMPATIBLE,result.status());assertTrue(result.message().contains("394 мм превышает 360 мм"));
    }
    @Test void widthAndCombinedThicknessCanIndependentlyExcludeAMount() {
        assertEquals(INCOMPATIBLE,fit(chassis(Coverage.COMPLETE,rule(Position.TOP,400,120,60)),cooler(aio())).status());
        var thickness=fit(chassis(Coverage.COMPLETE,rule(Position.TOP,400,125,55)),cooler(aio()));
        assertEquals(INCOMPATIBLE,thickness.status());assertTrue(thickness.message().contains("56 мм превышает 55 мм"));
    }
    @Test void exactPhysicalLimitsPassOnlyWithAllVerifiedGeometryAndNoUnresolvedConditions() {
        assertEquals(COMPATIBLE,fit(chassis(Coverage.COMPLETE,rule(Position.TOP,394,121,56)),cooler(aio())).status());
        for(var rule:List.of(rule(Position.TOP,null,121,56),rule(Position.TOP,394,null,56),rule(Position.TOP,394,121,null),
                rule(Position.TOP,394,121,56,"Нужно проверить RAM/VRM")))
            assertEquals(INSUFFICIENT_DATA,fit(chassis(Coverage.COMPLETE,rule),cooler(aio())).status());
    }
    @Test void anUndocumentedAlternativeLocationPreventsAGlobalRejection() {
        var top=rule(Position.TOP,390,125,60);
        assertEquals(INSUFFICIENT_DATA,fit(chassis(Coverage.PARTIAL,top),cooler(aio())).status());
        assertEquals(INCOMPATIBLE,fit(chassis(Coverage.COMPLETE,top),cooler(aio())).status());
        assertEquals(COMPATIBLE,fit(chassis(Coverage.COMPLETE,top,rule(Position.FRONT,400,125,60)),cooler(aio())).status());
    }
    @Test void anUnresolvedAlternativePreventsRejectionButDoesNotPromiseFit() {
        var result=fit(chassis(Coverage.COMPLETE,rule(Position.TOP,390,125,60),rule(Position.FRONT,null,null,null)),cooler(aio()));
        assertEquals(INSUFFICIENT_DATA,result.status());assertTrue(result.message().contains("Спереди"));
    }
    @Test void importedGeometryAndMountListsRemainUnknown() {
        var documented=chassis(Coverage.COMPLETE,rule(Position.TOP,394,121,56));
        for(var attribute:List.of(RADIATOR_LENGTH_MM,RADIATOR_WIDTH_MM,RADIATOR_THICKNESS_MM,RADIATOR_FAN_THICKNESS_MM,RADIATOR_NOMINAL_MM)) {
            var b=aio();b.set(attribute,20,"https://example.test/unverified",Verification.IMPORTED);
            assertEquals(INSUFFICIENT_DATA,fit(documented,cooler(b)).status(),attribute.name());
        }
        var b=builder();b.set(RADIATOR_MOUNTS,new RadiatorMountMatrix(Coverage.COMPLETE,List.of(rule(Position.TOP,1,1,1))),
                "https://example.test/unverified",Verification.IMPORTED);
        assertEquals(INSUFFICIENT_DATA,fit(DomainFixtures.part(90,ComponentCategory.CASE,b.build()),cooler(aio())).status());
    }
    @Test void thicknessAdditionCannotOverflowIntoAPassingResult() {
        var b=aio();DomainFixtures.set(b,RADIATOR_THICKNESS_MM,Integer.MAX_VALUE);DomainFixtures.set(b,RADIATOR_FAN_THICKNESS_MM,Integer.MAX_VALUE);
        assertEquals(INCOMPATIBLE,fit(chassis(Coverage.COMPLETE,rule(Position.TOP,400,125,Integer.MAX_VALUE)),cooler(b)).status());
    }
    @Test void aBareRadiatorBoundExcludesOnlyTheBodyAndCannotCertifyTheFanStack() {
        var limit=new Rule(Position.FRONT,Set.of(360),null,null,null,List.of(),55);
        var b=aio();DomainFixtures.set(b,RADIATOR_THICKNESS_MM,50);DomainFixtures.set(b,RADIATOR_FAN_THICKNESS_MM,30);
        assertEquals(INSUFFICIENT_DATA,fit(chassis(Coverage.COMPLETE,limit),cooler(b)).status(),"80 mm stack cannot be compared with a body-only 55 mm bound");
        DomainFixtures.set(b,RADIATOR_THICKNESS_MM,56);
        assertEquals(INCOMPATIBLE,fit(chassis(Coverage.COMPLETE,limit),cooler(b)).status(),"Body alone exceeds the confirmed bound");
        b.set(RADIATOR_THICKNESS_MM,56,"https://example.test/unverified",Verification.IMPORTED);
        assertEquals(INSUFFICIENT_DATA,fit(chassis(Coverage.COMPLETE,limit),cooler(b)).status());
        var fullyKnown=new Rule(Position.FRONT,Set.of(360),394,121,56,List.of(),55);
        assertEquals(COMPATIBLE,fit(chassis(Coverage.COMPLETE,fullyKnown),cooler(aio())).status(),"Independent complete geometry still proves a mount");
    }
    @Test void aFitDoesNotHideAnIndependentSocketConflict() {
        var cpu=DomainFixtures.part(1,ComponentCategory.CPU,DomainFixtures.set(builder(),SOCKET,"LGA1700").build());
        var result=service.assess(List.of(cpu,chassis(Coverage.COMPLETE,rule(Position.TOP,394,121,56)),cooler(aio())),false);
        assertEquals(INCOMPATIBLE,result.status());
        assertTrue(result.findings().stream().anyMatch(f->f.code().equals("radiator_fit")&&f.status()==COMPATIBLE));
        assertTrue(result.findings().stream().anyMatch(f->f.code().equals("cooler_socket")&&f.status()==INCOMPATIBLE));
    }
    @Test void recommendationRejectsImpossibleAioAndStrictModeRejectsUnresolvedMounting() {
        var base=DomainFixtures.verifiedBuild();var catalog=new ArrayList<>(base);
        catalog.set(7,cooler(aio()));
        var caseSpecs=builder();
        DomainFixtures.set(caseSpecs,SUPPORTED_FORM_FACTORS,Set.of("ATX"));DomainFixtures.set(caseSpecs,MAX_GPU_LENGTH_MM,330);
        DomainFixtures.set(caseSpecs,MAX_COOLER_HEIGHT_MM,165);
        DomainFixtures.set(caseSpecs,SUPPORTED_PSU_FORM_FACTORS,Set.of("ATX"));
        DomainFixtures.set(caseSpecs,RADIATOR_MOUNTS,new RadiatorMountMatrix(Coverage.COMPLETE,List.of(rule(Position.TOP,390,125,60))));
        catalog.set(6,DomainFixtures.part(90,ComponentCategory.CASE,caseSpecs.build()));
        var recommender=new BuildRecommendationService();var request=BuildRecommendationService.Request.forBudget(new BigDecimal("1000"));
        assertEquals(BuildRecommendationService.Status.NO_MATCH,recommender.recommend(catalog,request).status());
        DomainFixtures.set(caseSpecs,RADIATOR_MOUNTS,new RadiatorMountMatrix(Coverage.PARTIAL,List.of(rule(Position.TOP,390,125,60))));
        catalog.set(6,DomainFixtures.part(90,ComponentCategory.CASE,caseSpecs.build()));
        assertEquals(BuildRecommendationService.Status.NEEDS_VERIFICATION,recommender.recommend(catalog,request).status());
        var strict=new BuildRecommendationService.Request(request.budget(),request.usage(),16,500,Map.of(),true);
        assertEquals(BuildRecommendationService.Status.NO_MATCH,recommender.recommend(catalog,strict).status());
        catalog.add(base.get(7));
        assertEquals(BuildRecommendationService.Status.READY,recommender.recommend(catalog,strict).status());
        assertTrue(recommender.recommend(catalog,strict).components().contains(base.get(7)));
    }
}
