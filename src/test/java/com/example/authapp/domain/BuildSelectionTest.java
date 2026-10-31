package com.example.authapp.domain;

import com.example.authapp.models.Product;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BuildSelectionTest {
    @Test void ownedPartsStayInCompositionButNeverInPurchaseTotalOrRows() {
        var build=new BuildSelection();build.select(ComponentCategory.CPU,part(1,"CPU","10.05",ComponentCategory.CPU));
        build.select(ComponentCategory.GPU,part(2,"GPU","20.10",ComponentCategory.GPU));build.setOwned(ComponentCategory.GPU,true);
        assertEquals(2,build.allProducts().size());assertEquals(1,build.purchases().size());assertEquals(new BigDecimal("10.05"),build.purchaseTotal());
        build.setOwned(ComponentCategory.CPU,true);assertEquals(new BigDecimal("0.00"),build.purchaseTotal());assertTrue(build.purchases().isEmpty());
    }
    @Test void replacementRemovalAndNewBuildCannotInheritOwnership() {
        var build=new BuildSelection();var cpu=part(1,"CPU","10.05",ComponentCategory.CPU);build.select(ComponentCategory.CPU,cpu);build.setOwned(ComponentCategory.CPU,true);
        build.select(ComponentCategory.CPU,part(3,"Another CPU","1.01",ComponentCategory.CPU));assertFalse(build.isOwned(ComponentCategory.CPU));
        build.setOwned(ComponentCategory.CPU,true);build.remove(ComponentCategory.CPU);assertTrue(build.ownedCategories().isEmpty());
        build.select(ComponentCategory.CPU,cpu);build.setOwned(ComponentCategory.CPU,true);build.clear();assertTrue(build.products().isEmpty());assertTrue(build.ownedCategories().isEmpty());
    }
    @Test void stockAndPriceRefreshPreserveOwnershipButAReusedIdDoesNot() {
        var build=new BuildSelection();var cpu=part(1,"CPU   Alpha","10.05",ComponentCategory.CPU);build.select(ComponentCategory.CPU,cpu);build.setOwned(ComponentCategory.CPU,true);
        var refreshed=part(1,"cpu alpha","99.99",ComponentCategory.CPU);refreshed.setStock(0);build.refresh(Map.of(1,refreshed));
        assertTrue(build.isOwned(ComponentCategory.CPU));assertEquals(1,build.ownedProducts().size());assertEquals(new BigDecimal("0.00"),build.purchaseTotal());
        assertTrue(build.refresh(Map.of()).isEmpty());assertEquals(1,build.ownedProducts().size());
        assertEquals(java.util.Set.of(ComponentCategory.CPU),build.refresh(Map.of(1,part(1,"A different CPU","1.00",ComponentCategory.CPU))));
        assertTrue(build.products().isEmpty());assertTrue(build.ownedCategories().isEmpty());
    }
    @Test void onlyMatchingCategoriesAndSelectedOwnershipAreAllowedAndInputsAreDetached() {
        var build=new BuildSelection();var cpu=part(1,"CPU","10.05",ComponentCategory.CPU);
        assertThrows(IllegalArgumentException.class,()->build.setOwned(ComponentCategory.CPU,true));
        assertThrows(IllegalArgumentException.class,()->build.select(ComponentCategory.GPU,cpu));
        build.select(ComponentCategory.CPU,cpu);cpu.setPrice(999);assertEquals(new BigDecimal("10.05"),build.purchaseTotal());
        build.purchases().getFirst().setPrice(888);assertEquals(new BigDecimal("10.05"),build.purchaseTotal());
        assertThrows(UnsupportedOperationException.class,()->build.products().clear());
    }
    @Test void identityKeepsPeripheralCategoriesDistinctWhileNormalizingWhitespaceAndCase() {
        var monitor=new Product(99,"Test peripheral","",new BigDecimal("10.00"),2,"","Мониторы","");
        var keyboard=new Product(99,"Test peripheral","",new BigDecimal("10.00"),2,"","Клавиатуры","");
        assertFalse(BuildSelection.sameIdentity(monitor,keyboard));
        keyboard.setCategory("  МОНИТОРЫ  ");assertTrue(BuildSelection.sameIdentity(monitor,keyboard));
        var cpu=part(1,"CPU","10.00",ComponentCategory.CPU);
        var alias=part(1,"CPU","10.00",ComponentCategory.CPU);alias.setCategory("cpu");
        assertTrue(BuildSelection.sameIdentity(cpu,alias));
    }
    private static Product part(int id,String name,String price,ComponentCategory category) {
        return new Product(id,name,"",new BigDecimal(price),2,"",category.displayName(),"");
    }
}
