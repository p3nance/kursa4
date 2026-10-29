package com.example.authapp.services;

import com.example.authapp.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.authapp.domain.ComponentCategory.*;
import static com.example.authapp.domain.ComponentSpecs.*;

class ModelResolverTest {
    private final List<CatalogComponent> catalog=List.of(
            part(1,"Intel Core i9-14900K",CPU,"intel-core-i9-14900k"),
            part(2,"AMD Ryzen 7 7800X3D",CPU,"amd-ryzen-7-7800x3d"),
            part(3,"AMD Ryzen 5 7600",CPU,"amd-ryzen-5-7600"),
            part(4,"AMD Ryzen 5 7600X",CPU,"amd-ryzen-5-7600x"),
            part(5,"Intel Core i9-14900KF",CPU,"intel-core-i9-14900kf"),
            part(10,"NVIDIA GeForce RTX 4070 12GB",GPU,"NVIDIA GeForce RTX 4070"),
            part(11,"NVIDIA GeForce RTX 4070 SUPER 12GB",GPU,"NVIDIA GeForce RTX 4070 SUPER"),
            part(12,"NVIDIA GeForce RTX 4060 Ti 8GB",GPU,"NVIDIA GeForce RTX 4060 Ti"),
            part(13,"NVIDIA GeForce RTX 4060 Ti 16GB",GPU,"NVIDIA GeForce RTX 4060 Ti"),
            part(14,"AMD Radeon RX 7600 8GB",GPU,"AMD Radeon RX 7600"));
    private final ModelResolver resolver=new ModelResolver(catalog);

    @Test void intelAliasesPreserveTierAndSuffixAcrossWhitespaceAndUnicodeDashes() {
        for(String name:List.of("i9-14900k","i9 14900K","14900K","Intel Core i9–14900K",
                "intel-core-i9-14900k","интел i9 14900k","IntelCorei914900K")) exact(name,1);
        exact("14900KF",5);
        assertEquals(ModelResolver.Status.NOT_FOUND,resolver.resolve("14900F").status());
        assertEquals(ModelResolver.Status.NOT_FOUND,resolver.resolve("i5 14900K").status());
    }
    @Test void ryzenAliasesSupportCompactAndCyrillicNamesWithoutMergingXAndX3d() {
        for(String name:List.of("Ryzen 7 7800X3D","Ryzen7800X3D","райзен 7800x3d","7800X3D","райзен 7800х3д")) exact(name,2);
        exact("Ryzen7600",3);exact("Ryzen 7600X",4);
        assertEquals(ModelResolver.Status.NOT_FOUND,resolver.resolve("Ryzen 7800X").status());
    }
    @Test void plainGpuAliasDoesNotSelectTheSuperAndCapacityDisambiguatesTi() {
        for(String name:List.of("4070","RTX4070","rtx 4070 12 гб")) exact(name,10);
        for(String name:List.of("4070 super","RTX4070Super","RTX 4070 SUPER 12 GB")) exact(name,11);
        var ambiguous=resolver.resolve("4060 Ti");
        assertEquals(ModelResolver.Status.AMBIGUOUS,ambiguous.status());
        assertEquals(List.of(12,13),ids(ambiguous.candidates()));
        exact("4060Ti16GB",13);exact("rtx4060ti 8gb",12);
        assertEquals(ModelResolver.Status.NOT_FOUND,resolver.resolve("4060 Ti 24GB").status());
        assertEquals(ModelResolver.Status.NOT_FOUND,resolver.resolve("4070 Ti").status());
    }
    @Test void unknownNumericModelsAreNotGuessedAndSpellingCorrectionsNeedConfirmation() {
        assertEquals(ModelResolver.Status.NOT_FOUND,resolver.resolve("RTX 9999").status());
        assertEquals(ModelResolver.Status.NOT_FOUND,resolver.resolve("i9 14999K").status());
        var typo=resolver.resolve("4070 supre");
        assertEquals(ModelResolver.Status.SUGGESTED,typo.status());
        assertEquals(List.of(11),ids(typo.candidates()));
        assertEquals(ModelResolver.Status.NOT_FOUND,resolver.resolve("рыба").status());
    }
    @Test void ambiguousNumericFamilyKeepsCpuAndGpuCandidatesInsteadOfGuessingCategory() {
        var result=resolver.resolve("7600");
        assertEquals(ModelResolver.Status.AMBIGUOUS,result.status());
        assertEquals(List.of(3,14),ids(result.candidates()));
        exact("Ryzen7600",3);exact("RX7600",14);
    }
    @Test void mentionsUseTextOrderAndConsumeLongVariantsBeforePlainNumbers() {
        assertEquals(List.of(11,10),ids(resolver.mentions("что лучше RTX4070Super или 4070 для игр?")));
        assertEquals(List.of(1,2),ids(resolver.mentions("сравни i9-14900k и ryzen 7 7800x3d")));
        assertEquals(List.of(13,12),ids(resolver.mentions("4060 Ti 16GB против 4060 Ti 8GB")));
        assertEquals(List.of(2,1),ids(resolver.mentions("7800x3d 14900k")));
        assertEquals(List.of(1),ids(resolver.mentions("14900k и еще раз 14900k")));
    }
    @Test void mentionsDoNotSalvageDifferentVariantsOrAmbiguousFragments() {
        assertTrue(resolver.mentions("RTX 4070 Ti").isEmpty());
        assertTrue(resolver.mentions("i5 14900K").isEmpty());
        assertTrue(resolver.mentions("4060 Ti").isEmpty());
        assertTrue(resolver.mentions("4060 Ti 24GB").isEmpty());
        assertTrue(resolver.mentions("14900KS").isEmpty());
        assertTrue(resolver.mentions("7600").isEmpty());
    }
    @Test void mentionsNeverDiscardAnExplicitManufacturerOrRyzenTier() {
        assertEquals(List.of(1),ids(resolver.mentions("Ryzen5 7800X3D 14900K")),
                "A Ryzen 5 query must not silently become the catalog Ryzen 7");
        assertEquals(List.of(1),ids(resolver.mentions("Intel7800X3D 14900K")),
                "An Intel label cannot identify an AMD CPU");
        assertEquals(List.of(2),ids(resolver.mentions("AMD14900K 7800X3D")),
                "An AMD label cannot identify an Intel CPU");
        assertEquals(List.of(2,1),ids(resolver.mentions("AMD Ryzen7 7800X3D IntelCorei914900K")));
    }
    @Test void mentionsKeepExactCapacityAndAttachedVariantTokens() {
        assertTrue(resolver.mentions("4060 Ti 6GB").isEmpty(),"6GB is not a substring alias for 16GB");
        assertTrue(resolver.mentions("4070 SUPER16GB").isEmpty(),"A missing SUPER16GB SKU must not become plain 4070");
        assertTrue(resolver.mentions("4060 Ti24GB").isEmpty());
        assertEquals(List.of(13),ids(resolver.mentions("4060 Ti16GB")));
    }
    @Test void catalogIdentityRatherThanUnverifiedDescriptionDefinesAliases() {
        var imported=builder().set(MODEL_ID,"WRONG_ALIAS", "description",Verification.IMPORTED).build();
        var c=new CatalogComponent(90,"Actual Device",CASE,BigDecimal.ONE,1,imported);
        var local=new ModelResolver(List.of(c));
        assertEquals(ModelResolver.Status.EXACT,local.resolve("Actual Device").status());
        assertEquals(ModelResolver.Status.NOT_FOUND,local.resolve("WRONG_ALIAS").status());
    }
    @Test void constructionAndResultsKeepImmutableCatalogSnapshots() {
        var mutable=new ArrayList<>(catalog);var local=new ModelResolver(mutable);mutable.clear();
        var result=local.resolve("14900K");
        assertEquals(1,result.candidates().getFirst().productId());
        assertThrows(UnsupportedOperationException.class,()->result.candidates().clear());
        assertTrue(local.resolve(null).candidates().isEmpty());
        assertTrue(local.mentions("x".repeat(2001)).isEmpty());
    }
    private void exact(String input,int id) {
        var result=resolver.resolve(input);
        assertEquals(ModelResolver.Status.EXACT,result.status(),input);
        assertEquals(List.of(id),ids(result.candidates()),input);
    }
    private static List<Integer> ids(List<CatalogComponent> values) {return values.stream().map(CatalogComponent::productId).toList();}
    private static CatalogComponent part(int id,String name,ComponentCategory category,String model) {
        return new CatalogComponent(id,name,category,BigDecimal.ONE,1,
                builder().set(MODEL_ID,model,"https://example.test/model-fixture",Verification.VERIFIED).build());
    }
}
