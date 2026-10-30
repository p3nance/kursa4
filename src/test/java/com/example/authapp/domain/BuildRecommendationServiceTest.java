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
    @Test void alternativesAreDistinctRankedAndDeterministicInOneTraversal() {
        var catalog=alternativeCatalog();
        var request=BuildRecommendationService.Request.forBudget(new BigDecimal("1000.00"));
        var choices=service.recommendAlternatives(catalog,request,3);
        assertEquals(3,choices.size());assertEquals(List.of(new BigDecimal("800.00"),new BigDecimal("800.01"),new BigDecimal("800.02")),choices.stream().map(BuildRecommendationService.Recommendation::purchaseTotal).toList());
        assertEquals(3,choices.stream().map(r->r.components().stream().map(CatalogComponent::productId).toList()).distinct().count());
        assertTrue(choices.stream().allMatch(r->r.status()==BuildRecommendationService.Status.READY));
        Collections.reverse(catalog);assertEquals(choices,service.recommendAlternatives(catalog,request,3));
        assertEquals(service.recommend(catalog,request),choices.getFirst());
    }
    @Test void alternativeBudgetsOwnedPartsAndNodeLimitsRemainAuthoritative() {
        var catalog=alternativeCatalog();var gpu=catalog.stream().filter(c->c.category()==ComponentCategory.GPU).findFirst().orElseThrow();
        var request=new BuildRecommendationService.Request(new BigDecimal("700.01"),BuildRecommendationService.Usage.GAMING,16,500,Map.of(ComponentCategory.GPU,gpu),true);
        var choices=service.recommendAlternatives(catalog,request,3);
        assertEquals(2,choices.size());assertEquals(new BigDecimal("700.00"),choices.getFirst().purchaseTotal());
        assertTrue(choices.stream().allMatch(r->r.purchaseTotal().compareTo(request.budget())<=0&&r.components().contains(gpu)));
        var bounded=new BuildRecommendationService(12,3).recommendAlternatives(catalog,request,3);
        assertEquals(BuildRecommendationService.Status.SEARCH_LIMIT,bounded.getFirst().status());assertTrue(bounded.getFirst().searchTruncated());
        assertThrows(IllegalArgumentException.class,()->service.recommendAlternatives(catalog,request,4));
        assertThrows(IllegalArgumentException.class,()->service.recommendAlternatives(catalog,request,0));
    }
    private static ArrayList<CatalogComponent> alternativeCatalog() {
        var catalog=new ArrayList<>(DomainFixtures.verifiedBuild());
        for(var category:List.of(ComponentCategory.CPU,ComponentCategory.RAM)) {
            var base=catalog.stream().filter(c->c.category()==category).findFirst().orElseThrow();
            catalog.add(new CatalogComponent(base.productId()+100,base.name()+" alternative",category,
                    base.price().add(category==ComponentCategory.CPU?new BigDecimal("0.01"):new BigDecimal("0.02")),1,base.specs()));
        }
        catalog.add(catalog.getFirst());return catalog;
    }
}
