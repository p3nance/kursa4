package com.example.authapp.ui;

import com.example.authapp.models.Product;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogQueryTest {
    private final List<Product> products=List.of(
            new Product(3,"CPU Alpha","",10000,2,"","Процессоры","AMD"),
            new Product(2,"CPU Beta","",15000,0,"","Процессоры","Intel"),
            new Product(1,"GPU Alpha","",20000,1,"","Видеокарты","AMD"));
    @Test void combinesSearchCategoryStockAndPriceWithoutInventingMatches(){
        var query=new CatalogQuery("ALPHA","процессоры",Set.of("AMD"),new BigDecimal("10000"),new BigDecimal("20000"),true,CatalogQuery.Sort.PRICE_DESC);
        assertEquals(List.of(3),query.apply(products).stream().map(Product::getId).toList());
    }
    @Test void sortIsDeterministicAndStockFilterIsOptional(){
        var query=new CatalogQuery("","Все",Set.of(),null,null,false,CatalogQuery.Sort.PRICE_ASC);
        assertEquals(List.of(3,2,1),query.apply(products).stream().map(Product::getId).toList());
        assertEquals(2,new CatalogQuery("","Все",Set.of(),null,null,true,CatalogQuery.Sort.DEFAULT).apply(products).size());
    }
    @Test void unknownQueryAndInvalidRangeAreExplicit(){
        assertTrue(new CatalogQuery("несуществующий","Все",Set.of(),null,null,false,CatalogQuery.Sort.DEFAULT).apply(products).isEmpty());
        assertThrows(IllegalArgumentException.class,()->new CatalogQuery("","Все",Set.of(),BigDecimal.TEN,BigDecimal.ONE,false,CatalogQuery.Sort.DEFAULT));
    }
    @Test void currencyFormattingKeepsFractionAndRubles(){
        assertEquals("1\u00a0234,5 ₽",Ui.money(new BigDecimal("1234.50")));
    }
}
