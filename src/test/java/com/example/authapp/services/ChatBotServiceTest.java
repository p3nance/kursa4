package com.example.authapp.services;

import com.example.authapp.models.Product;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChatBotServiceTest {
    @Test void russianBudgetFormats() {
        assertEquals(new BigDecimal("100000"), ChatBotService.budgetFrom("Собери ПК до 100 000 рублей для игр").orElseThrow());
        assertEquals(new BigDecimal("100000"), ChatBotService.budgetFrom("до 100к").orElseThrow());
        assertEquals(new BigDecimal("100500.0"), ChatBotService.budgetFrom("бюджет 100,5 тыс.").orElseThrow());
        assertEquals(new BigDecimal("150000"), ChatBotService.budgetFrom("150000").orElseThrow());
        assertTrue(ChatBotService.budgetFrom("RTX 4090").isEmpty());
        assertEquals(new BigDecimal("100000"), ChatBotService.budgetFrom("Собери ПК для игр на 1440p до 100к").orElseThrow());
        assertEquals(new BigDecimal("100000"), ChatBotService.budgetFrom("до 100\u202f000 рублей").orElseThrow());
        assertTrue(ChatBotService.budgetFrom("до 100 долларов").isEmpty());
    }
    @Test void asksForMissingBudgetAndKeepsConversationContext() {
        var bot = new ChatBotService(List.of());
        assertTrue(bot.reply("Собери ПК для игр").text().contains("бюджет"));
        assertTrue(bot.reply("100к").text().contains("нет комплектующих"));
        bot.reset();
        assertTrue(bot.reply("100к").text().contains("каких задач"));
    }
    @Test void glossaryDoesNotGetHijackedByPendingBuild() {
        var bot = new ChatBotService(List.of());
        bot.reply("Собери ПК");
        assertTrue(bot.reply("Что такое сокет?").text().contains("Источник:"));
    }
    @Test void catalogSearchNeverManufacturesProducts() {
        var bot = new ChatBotService(List.of(new Product(1,"Example CPU","",12345,2,"","Процессоры","Example")));
        assertTrue(bot.reply("Найди Example CPU").text().contains("Example CPU"));
        assertTrue(bot.reply("Найди RTX 9999").text().contains("ничего не найдено"));
    }
    @Test void noBenchmarkScoresWithoutMeasurements() {
        var answer = new ChatBotService(List.of()).reply("Сравни Example A и Example B").text();
        assertTrue(answer.contains("не найдена в текущем каталоге"));
        assertFalse(answer.contains("%"));
    }
    @Test void oversizedInputAndAmbiguousComparisonAreHandled() {
        var bot = new ChatBotService(List.of());
        assertTrue(bot.reply("x".repeat(2001)).text().contains("2000"));
        assertTrue(bot.reply("Сравни видеокарты").text().contains("две модели"));
    }
    @Test void producesEightPartDraftWithoutInventingCompatibility() {
        var catalog = java.util.Arrays.stream(com.example.authapp.domain.ComponentCategory.values())
                .map(c -> new Product(c.ordinal()+1,"Тест " + c.name(),"",1000,1,"",c.displayName(),"" )).toList();
        var reply = new ChatBotService(catalog).reply("Собери ПК до 10000 рублей для игр");
        assertEquals(8, reply.build().size());
        assertTrue(reply.text().contains("совместимость ещё не подтверждена"));
    }
    @Test void typedTermsAndProductsDoNotRequireParsingText() {
        var product = new Product(1, "Example CPU", "", 12345, 2, "", "Процессоры", "Example");
        var bot = new ChatBotService(List.of(product));
        var terms = bot.reply("Что такое сокет?");
        assertTrue(terms.content().getFirst() instanceof ChatBotService.TermContent);
        var search = bot.reply("Найди Example CPU");
        var found = (ChatBotService.ProductsContent) search.content().getFirst();
        assertEquals("Example CPU", found.items().getFirst().name());
        product.setName("Changed later");
        assertEquals("Example CPU", found.items().getFirst().name());
    }
    @Test void typedBuildKeepsSnapshotAndUnknownStatus() {
        var catalog = java.util.Arrays.stream(com.example.authapp.domain.ComponentCategory.values())
                .map(c -> new Product(c.ordinal()+1,"Тест " + c.name(),"",1000,1,"",c.displayName(),"" )).toList();
        var bot = new ChatBotService(catalog);
        var reply = bot.reply("Собери ПК до 10000 рублей\nдля игр");
        var build = (ChatBotService.BuildContent) reply.content().getFirst();
        assertEquals(8,build.parts().size());
        assertFalse(build.verified());
        assertFalse(build.findings().isEmpty());
        catalog.getFirst().setPrice(99999);
        assertEquals(new BigDecimal("1000.0"),build.parts().getFirst().price());
        reply.build().getFirst().setName("Changed by UI");
        assertNotEquals("Changed by UI",reply.build().getFirst().getName());
    }
    @Test void catalogRefreshPreservesBudgetAndUsage() {
        var bot = new ChatBotService(List.of());
        bot.reply("Собери ПК до 100000 рублей для игр");
        var before = bot.summary();
        bot.updateCatalog(List.of(new Product(1,"New CPU","",1000,1,"","Процессоры","")));
        assertEquals(before,bot.summary());
        assertTrue(bot.reply("Найди New CPU").content().getFirst() instanceof ChatBotService.ProductsContent);
    }
}
