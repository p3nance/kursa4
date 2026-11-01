package com.example.authapp.services;

import com.example.authapp.models.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatCatalogFilterTest {
    private static ChatBotService.ProductInfo product(String price,int stock){return ChatBotService.ProductInfo.from(new Product(1,"Test CPU","",new BigDecimal(price),stock,"","Процессоры",""));}
    @Test void inclusiveAndStrictBoundsKeepTheCurrencyAndCents() {
        var p=product("22990",1);
        assertTrue(ChatCatalogFilter.parse("Ryzen до 22990 рублей").accepts(p));
        assertFalse(ChatCatalogFilter.parse("Ryzen дешевле 22990 рублей").accepts(p));
        assertTrue(ChatCatalogFilter.parse("Ryzen не дороже 22990 рублей").accepts(p));
        assertFalse(ChatCatalogFilter.parse("Ryzen дороже 22990 рублей").accepts(p));
        assertTrue(ChatCatalogFilter.parse("Ryzen от 22,99 тысяч").accepts(p));
        assertFalse(ChatCatalogFilter.parse("Ryzen до 22989,99 рублей").accepts(p));
        assertEquals("Ryzen",ChatCatalogFilter.parse("Ryzen до 30тысрублей в наличии").query());
        assertEquals("Ryzen",ChatCatalogFilter.parse("Ryzen до 30 тысяч, пожалуйста").query());
        assertEquals("Ryzen",ChatCatalogFilter.parse("Ryzen до 25 тысяч, не меняя бюджет ПК").query());
    }
    @Test void rangeAndStockAreCombinedWithoutChangingTheCatalog() {
        var f=ChatCatalogFilter.parse("Ryzen от 25 тысяч до 55 тысяч в наличии");assertNull(f.problem());
        assertFalse(f.accepts(product("22990",1)));assertTrue(f.accepts(product("25990",2)));
        assertFalse(f.accepts(product("25990",0)));assertFalse(f.accepts(product("67990",5)));
        assertTrue(ChatCatalogFilter.parse("Ryzen не в наличии").accepts(product("1000",0)));
    }
    @Test void ambiguousOrUnsupportedBoundsCannotBeSilentlyIgnored() {
        for(String raw:new String[]{"Ryzen от 60 тысяч до 30 тысяч","Ryzen либо до 25 тысяч либо дороже 50 тысяч",
                "Ryzen до -1 рубля","Ryzen до 300 долларов","Ryzen до 1,001 рубля","Ryzen до 10","SSD до 2 ТБ"})
            assertNotNull(ChatCatalogFilter.parse(raw).problem(),raw);
        assertNull(ChatCatalogFilter.parse("Насколько 2 дороже 1?").problem());
    }
}
