package com.example.authapp.domain;

import com.example.authapp.services.CompatibilityService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.authapp.domain.ComponentSpecs.*;

class CompatibilityServiceTest {
    private final CompatibilityService service=new CompatibilityService();
    @Test void emptySpecsNeverGuaranteeCompatibility() {
        var parts=DomainFixtures.verifiedBuild().stream().map(c->new CatalogComponent(c.productId(),c.name(),c.category(),c.price(),c.stock(),empty())).toList();
        assertEquals(CompatibilityService.Status.INSUFFICIENT_DATA,service.assess(parts).status());
    }
    @Test void completeVerifiedFixturePassesImplementedChecks() {
        assertEquals(CompatibilityService.Status.COMPATIBLE,service.assess(DomainFixtures.verifiedBuild()).status());
    }
    @Test void missingCategoriesRemainUnknownEvenWhenSelectedPartsMatch() {
        assertEquals(CompatibilityService.Status.INSUFFICIENT_DATA,service.assess(DomainFixtures.verifiedBuild().subList(0,2)).status());
        assertEquals(CompatibilityService.Status.COMPATIBLE,service.assess(DomainFixtures.verifiedBuild().subList(0,2),false).status());
    }
    @Test void verifiedSocketConflictCannotBeHiddenByOtherMissingData() {
        var parts=new ArrayList<>(DomainFixtures.verifiedBuild());
        parts.set(0,DomainFixtures.part(1,ComponentCategory.CPU,DomainFixtures.set(builder(),SOCKET,"LGA1700").build()));
        assertEquals(CompatibilityService.Status.INCOMPATIBLE,service.assess(parts).status());
    }
    @Test void socketAloneDoesNotConfirmCpuBiosSupport() {
        var result=service.assess(List.of(DomainFixtures.part(1,ComponentCategory.CPU,DomainFixtures.set(builder(),SOCKET,"AM5").build()),
                DomainFixtures.part(2,ComponentCategory.MOTHERBOARD,DomainFixtures.set(builder(),SOCKET,"AM5").build())),false);
        assertEquals(CompatibilityService.Status.INSUFFICIENT_DATA,result.status());
        assertTrue(result.findings().stream().anyMatch(f->f.code().equals("cpu_support")));
    }
    @Test void psuMarginAndManufacturerMinimumAreEnforced() {
        var parts=new ArrayList<>(DomainFixtures.verifiedBuild());var b=builder();DomainFixtures.set(b,PSU_WATTS,450);
        parts.set(5,DomainFixtures.part(6,ComponentCategory.PSU,b.build()));
        var result=service.assess(parts);
        assertEquals(CompatibilityService.Status.INCOMPATIBLE,result.status());
        assertTrue(result.findings().stream().anyMatch(f->f.code().equals("power_budget")&&f.status()==CompatibilityService.Status.INCOMPATIBLE));
    }
    @Test void duplicateCategoriesRejected() {
        var cpu=DomainFixtures.verifiedBuild().getFirst();
        assertEquals(CompatibilityService.Status.INCOMPATIBLE,service.assess(List.of(cpu,cpu),false).status());
    }
    private CatalogComponent chassis() {
        return DomainFixtures.part(90,ComponentCategory.CASE,DomainFixtures.set(builder(),MAX_COOLER_HEIGHT_MM,165).build());
    }
    private CatalogComponent cooler(String kind,Verification verification,int height) {
        var specs=builder();DomainFixtures.set(specs,HEIGHT_MM,height);
        if(kind!=null)specs.set(COOLER_KIND,kind,"https://example.test/synthetic-cooler",verification);
        return new CatalogComponent(91,"Synthetic AIO 360 liquid cooler",ComponentCategory.COOLER,
                java.math.BigDecimal.ONE,1,specs.build());
    }
    @Test void verifiedAirTypeUsesHeightLimitAndCanReportARealConflict() {
        assertEquals(CompatibilityService.Status.COMPATIBLE,service.assess(List.of(chassis(),cooler("AIR",Verification.VERIFIED,155)),false).status());
        var result=service.assess(List.of(chassis(),cooler("AIR",Verification.VERIFIED,170)),false);
        assertEquals(CompatibilityService.Status.INCOMPATIBLE,result.status());
        assertTrue(result.findings().stream().anyMatch(f->f.code().equals("cooler_height")&&f.status()==CompatibilityService.Status.INCOMPATIBLE));
    }
    @Test void unknownOrUnverifiedCoolerKindNeverAssumesAirEvenFromTheProductName() {
        for(var part:List.of(cooler(null,Verification.UNKNOWN,360),cooler("AIR",Verification.IMPORTED,155),cooler("AIO",Verification.UNKNOWN,360))) {
            var result=service.assess(List.of(chassis(),part),false);
            assertEquals(CompatibilityService.Status.INSUFFICIENT_DATA,result.status());
            assertTrue(result.findings().stream().anyMatch(f->f.code().equals("cooler_kind")&&f.status()==CompatibilityService.Status.INSUFFICIENT_DATA));
            assertTrue(result.findings().stream().noneMatch(f->f.code().equals("cooler_height")));
        }
    }
    @Test void aioNominalClassDoesNotBecomeAirHeightOrConfirmRadiatorFit() {
        var specs=builder();DomainFixtures.set(specs,COOLER_KIND,"AIO");DomainFixtures.set(specs,RADIATOR_NOMINAL_MM,360);
        DomainFixtures.set(specs,HEIGHT_MM,360); // Deliberately wrong legacy interpretation must not drive the air-height check.
        var result=service.assess(List.of(chassis(),DomainFixtures.part(91,ComponentCategory.COOLER,specs.build())),false);
        assertEquals(CompatibilityService.Status.INSUFFICIENT_DATA,result.status());
        var fit=result.findings().stream().filter(f->f.code().equals("radiator_fit")).findFirst().orElseThrow();
        assertEquals(CompatibilityService.Status.INSUFFICIENT_DATA,fit.status());
        assertTrue(fit.message().contains("360"));assertTrue(fit.message().contains("позиции крепления"));assertTrue(fit.message().contains("с вентиляторами"));
        assertTrue(result.findings().stream().noneMatch(f->f.code().equals("cooler_height")));
        var unverified=builder();DomainFixtures.set(unverified,COOLER_KIND,"AIO");
        unverified.set(RADIATOR_NOMINAL_MM,360,"https://example.test/synthetic-cooler",Verification.IMPORTED);
        var unknown=service.assess(List.of(chassis(),DomainFixtures.part(91,ComponentCategory.COOLER,unverified.build())),false);
        assertTrue(unknown.findings().stream().filter(f->f.code().equals("radiator_fit")).noneMatch(f->f.message().contains("360")));
    }
    @Test void radiatorUncertaintyDoesNotHideIndependentSocketAndCoolingPowerConflicts() {
        var cpu=builder();DomainFixtures.set(cpu,SOCKET,"AM5");DomainFixtures.set(cpu,MAX_POWER_WATTS,120);
        var aio=builder();DomainFixtures.set(aio,COOLER_KIND,"AIO");DomainFixtures.set(aio,SUPPORTED_SOCKETS,Set.of("LGA1700"));DomainFixtures.set(aio,COOLING_WATTS,50);
        var result=service.assess(List.of(chassis(),DomainFixtures.part(1,ComponentCategory.CPU,cpu.build()),DomainFixtures.part(91,ComponentCategory.COOLER,aio.build())),false);
        assertEquals(CompatibilityService.Status.INCOMPATIBLE,result.status());
        for(String code:List.of("cooler_socket","cooler_power"))assertTrue(result.findings().stream().anyMatch(f->f.code().equals(code)&&f.status()==CompatibilityService.Status.INCOMPATIBLE));
        assertTrue(result.findings().stream().anyMatch(f->f.code().equals("radiator_fit")&&f.status()==CompatibilityService.Status.INSUFFICIENT_DATA));
    }

}
