package com.example.authapp.services;

import com.example.authapp.models.Product;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatCatalogGroupTest {
    private static ChatBotService.ProductInfo product(int id,String price,int stock) {
        return ChatBotService.ProductInfo.from(new Product(id,"Test CPU "+id,"",new BigDecimal(price),stock,"","Процессоры",""));
    }
    private static ChatBotService.Reply group(ChatBotService.ProductInfo... products) {
        return new ChatBotService.Reply("","",List.of(),List.of(new ChatBotService.ProductsContent(List.of(products),products.length)),new ChatBotService.RequestSummary(new BigDecimal("100000"),BuildRecommendationService.Usage.GAMING),List.of());
    }
    private static List<Integer> ids(ChatProductContext.Result result) {
        return result.reply().content().stream().flatMap(c->c instanceof ChatBotService.ProductsContent p?p.items().stream():
                c instanceof ChatBotService.PriceComparisonContent p?List.of(p.left(),p.right()).stream():java.util.stream.Stream.<ChatBotService.ProductInfo>empty()).map(ChatBotService.ProductInfo::id).toList();
    }
    @Test void equalPriceKeepsEveryCandidateAndDoesNotChangeBudget() {
        var group=group(product(1,"1000.25",0),product(2,"1000.25",4),product(3,"2000",1));
        var result=ChatCatalogGroup.resolve(group,"Кто из них дешевле?",group.request());
        assertEquals(List.of(1,2),ids(result));assertTrue(result.reply().text().contains("нескольких"));
        assertEquals(List.of(3),ids(ChatCatalogGroup.resolve(group,"Кто из них дороже?",group.request())));
        assertEquals(List.of(1,2),ids(ChatCatalogGroup.resolve(group,"Покажи самый недорогой из них",group.request())));
        assertEquals(group.request(),result.reply().request());assertTrue(result.reply().text().contains("производительность"));
    }
    @Test void stockFilterKeepsOnlyActualAvailableOrAbsentItems() {
        var group=group(product(1,"1000",0),product(2,"2000",4),product(3,"3000",1));
        assertEquals(List.of(2,3),ids(ChatCatalogGroup.resolve(group,"Что из них в наличии?",group.request())));
        assertEquals(List.of(1),ids(ChatCatalogGroup.resolve(group,"Кто из них не в наличии?",group.request())));
    }
    @Test void cheapestAvailableDoesNotSelectTheCheapestUnavailableProduct() {
        var group=group(product(1,"1000",0),product(2,"2000",4),product(3,"3000",1));
        assertEquals(List.of(2),ids(ChatCatalogGroup.resolve(group,"Покажи самый дешёвый из них в наличии",group.request())));
    }
    @Test void differenceUsesExactCentsAndRequiresAnUnambiguousPair() {
        var group=group(product(1,"1000.25",2),product(2,"2000.50",4),product(3,"3000",1));
        var result=ChatCatalogGroup.resolve(group,"Насколько второй дороже первого?",group.request());
        assertEquals(List.of(2,1),ids(result));assertTrue(result.reply().text().contains("1\u00a0000,25 ₽"));
        var card=assertInstanceOf(ChatBotService.PriceComparisonContent.class,result.reply().content().getFirst());
        assertEquals(new BigDecimal("1000.25"),card.difference());
        assertTrue(ChatGroundedExplanation.explain(result.reply(),group.request()).orElseThrow().text().contains("1\u00a0000,25 ₽"));
        assertFalse(ChatCatalogGroup.resolve(group,"Какова разница в цене?",group.request()).resolved());
        assertFalse(ChatCatalogGroup.resolve(group,"Насколько первый дороже первого?",group.request()).resolved());
        assertFalse(ChatCatalogGroup.resolve(group,"Насколько четвёртый дороже первого?",group.request()).resolved());
        assertEquals(List.of(1),ids(ChatCatalogGroup.resolve(group,"Покажи самый дорогой из них до 1500 рублей",group.request())));
        assertEquals(List.of(2,1),ids(ChatCatalogGroup.resolve(group,"Насколько 2 дороже 1?",group.request())));
    }
    @Test void hardwareSpeedAndBenchmarkPercentagesAreNotPriceQuestions() {
        assertFalse(ChatCatalogGroup.question("Как получились эти проценты?"));
        assertFalse(ChatCatalogGroup.question("Насколько второй быстрее первого?"));
        assertFalse(ChatCatalogGroup.question("Собери самый дешёвый ПК до 100000 рублей"));
        assertTrue(ChatCatalogGroup.question("Насколько 2 дороже 1?"));
    }
}
