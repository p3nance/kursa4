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
}
