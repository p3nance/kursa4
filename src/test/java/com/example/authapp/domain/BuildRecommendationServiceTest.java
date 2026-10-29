package com.example.authapp.domain;

import com.example.authapp.services.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.authapp.domain.ComponentSpecs.*;

class BuildRecommendationServiceTest {
    private final BuildRecommendationService service=new BuildRecommendationService();
    @Test void selectsVerifiedBuildWithinExactBudgetAndIsDeterministic() {
        var request=BuildRecommendationService.Request.forBudget(new BigDecimal("800.00"));
        var first=service.recommend(DomainFixtures.verifiedBuild(),request);
        var reversed=new ArrayList<>(DomainFixtures.verifiedBuild());Collections.reverse(reversed);
        assertEquals(first,service.recommend(reversed,request));
        assertEquals(BuildRecommendationService.Status.READY,first.status());
        assertEquals(new BigDecimal("800.00"),first.purchaseTotal());
    }
    @Test void impossibleBudgetAndOutOfStockAreExplained() {
        assertEquals(BuildRecommendationService.Status.NO_MATCH,service.recommend(DomainFixtures.verifiedBuild(),
                BuildRecommendationService.Request.forBudget(new BigDecimal("799.99"))).status());
        var parts=DomainFixtures.verifiedBuild().stream().map(c->new CatalogComponent(c.productId(),c.name(),c.category(),c.price(),0,c.specs())).toList();
        assertEquals(BuildRecommendationService.Status.NO_MATCH,service.recommend(parts,
                BuildRecommendationService.Request.forBudget(new BigDecimal("800"))).status());
    }
    @Test void cheaperKnownConflictIsNotRecommended() {
        var catalog=new ArrayList<>(DomainFixtures.verifiedBuild());
        catalog.add(new CatalogComponent(9,"Conflicting board",ComponentCategory.MOTHERBOARD,BigDecimal.ONE,1,
                DomainFixtures.set(builder(),SOCKET,"LGA1700").build()));
        var result=service.recommend(catalog,BuildRecommendationService.Request.forBudget(new BigDecimal("800")));
        assertEquals(BuildRecommendationService.Status.READY,result.status());
        assertTrue(result.components().stream().noneMatch(c->c.productId()==9));
    }
    @Test void missingSpecsProduceDraftAndStrictRequestRejectsDraft() {
        var catalog=DomainFixtures.verifiedBuild().stream().map(c->new CatalogComponent(c.productId(),c.name(),c.category(),c.price(),1,empty())).toList();
        var request=BuildRecommendationService.Request.forBudget(new BigDecimal("800"));
        assertEquals(BuildRecommendationService.Status.NEEDS_VERIFICATION,service.recommend(catalog,request).status());
        var strict=new BuildRecommendationService.Request(request.budget(),request.usage(),16,500,Map.of(),true);
        assertEquals(BuildRecommendationService.Status.NO_MATCH,service.recommend(catalog,strict).status());
    }
    @Test void nodeLimitAppliesBeforeAnyCompleteCombination() {
        var bounded=new BuildRecommendationService(12,3);
        var result=bounded.recommend(DomainFixtures.verifiedBuild(),BuildRecommendationService.Request.forBudget(new BigDecimal("800")));
        assertEquals(BuildRecommendationService.Status.SEARCH_LIMIT,result.status());
        assertEquals(0,result.combinationsExamined());assertTrue(result.searchTruncated());
    }
    @Test void interruptedSearchCancelsBeforeExaminingCatalog() {
        boolean wasInterrupted=Thread.currentThread().isInterrupted();
        try {
            Thread.currentThread().interrupt();
            assertThrows(java.util.concurrent.CancellationException.class,()->service.recommend(
                    DomainFixtures.verifiedBuild(),BuildRecommendationService.Request.forBudget(new BigDecimal("800"))));
            assertTrue(Thread.currentThread().isInterrupted(),"Service must not swallow interrupt flag");
        } finally {
            Thread.interrupted();
            if(wasInterrupted) Thread.currentThread().interrupt();
        }
    }
    @Test void ownedComponentsAreNotChargedButMustMeetConstraints() {
        var parts=DomainFixtures.verifiedBuild();var request=new BuildRecommendationService.Request(new BigDecimal("700"),
                BuildRecommendationService.Usage.GAMING,16,500,Map.of(ComponentCategory.GPU,parts.get(2)),false);
        assertEquals(new BigDecimal("700.00"),service.recommend(parts,request).purchaseTotal());
        var workstation=BuildRecommendationService.Request.forUsage(new BigDecimal("800"),BuildRecommendationService.Usage.WORKSTATION);
        assertEquals(BuildRecommendationService.Status.NO_MATCH,service.recommend(parts,workstation).status());
    }
}
