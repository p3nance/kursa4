package com.example.authapp.services;

import com.example.authapp.models.Product;
import com.example.authapp.domain.ComponentCategory;
import com.example.authapp.domain.ComponentSpecs;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BuildConstraintsConversationTest {
    private String previous;
    private List<Product> catalog;
    @BeforeEach void catalog() throws Exception {
        previous=System.getProperty("techstore.knowledge.dir");System.setProperty("techstore.knowledge.dir",Path.of("src/main/resources/knowledge").toAbsolutePath().toString());
        catalog=new ArrayList<>();
        try(var reader=Files.newBufferedReader(Path.of("src/test/resources/knowledge/catalog-reference.json"))) {
            for(var item:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("products")) {
                var row=item.getAsJsonObject();catalog.add(new Product(row.get("id").getAsInt(),row.get("name").getAsString(),row.get("description").getAsString(),
                        row.get("price").getAsBigDecimal(),row.get("stock").getAsInt(),"",row.get("category").getAsString(),row.get("manufacturer").getAsString()));
            }
        }
    }
    @AfterEach void restore(){if(previous==null)System.clearProperty("techstore.knowledge.dir");else System.setProperty("techstore.knowledge.dir",previous);}
    @Test void appliesVerifiedMinimumsAndKeepsThemForFollowUpBudget() {
        var bot=new ChatBotService(catalog);
        var answer=bot.reply("Собери ПК до 500000 рублей для игр, ОЗУ 32 ГБ и SSD 2 ТБ");
        assertEquals(32,bot.summary().minRamGb());assertEquals(2000,bot.summary().minStorageGb());
        assertCapacity(answer,ComponentCategory.RAM,32);assertCapacity(answer,ComponentCategory.STORAGE,2000);
        bot.reply("До 600000 рублей");assertEquals(32,bot.summary().minRamGb());assertEquals(2000,bot.summary().minStorageGb());
        answer=bot.reply("Теперь ОЗУ 64 ГБ и SSD 1 ТБ");
        assertEquals(64,bot.summary().minRamGb());assertEquals(1000,bot.summary().minStorageGb());
        assertTrue(answer.build().isEmpty());assertTrue(answer.text().contains("Оперативная память"));
        assertCapacity(bot.reply("ОЗУ 16 ГБ"),ComponentCategory.RAM,16);
    }
    @Test void invalidCapacityAndUncommittedDraftDoNotReplaceAcceptedState() {
        var bot=new ChatBotService(catalog);bot.reply("Собери ПК до 500000 рублей для игр, ОЗУ 32 ГБ");
        var before=bot.summary();bot.reply("ОЗУ 0 ГБ");assertEquals(before,bot.summary());
        var draft=bot.fork();draft.reply("ОЗУ 64 ГБ");assertEquals(before,bot.summary());
        bot.commitConversation(draft);assertEquals(64,bot.summary().minRamGb());
        bot.updateCatalog(catalog);assertEquals(64,bot.summary().minRamGb());
        bot.reset();assertNull(bot.summary().minRamGb());assertNull(bot.summary().budget());
    }
    @Test void strictRequestDoesNotInventVerifiedCompatibilityAndOtherIntentsStillWork() {
        var bot=new ChatBotService(catalog);
        var answer=bot.reply("Собери ПК до 500000 рублей для игр, ОЗУ 32 ГБ, только проверенная совместимость");
        assertTrue(bot.summary().requireVerified());assertTrue(answer.build().isEmpty());
        assertTrue(answer.text().contains("проверенных"));
        assertInstanceOf(ChatBotService.TermContent.class,bot.reply("Что такое ОЗУ?").content().getFirst());
        assertInstanceOf(ChatBotService.ProductsContent.class,bot.reply("Найди SSD").content().getFirst());
        assertTrue(bot.reply("Сравни 14700K и 14600K").content().stream().anyMatch(ChatBotService.BenchmarksContent.class::isInstance));
    }
    @Test void unverifiedCapacityCannotSatisfyAnExplicitMinimum() {
        var products=java.util.Arrays.stream(ComponentCategory.values()).map(c->new Product(10000+c.ordinal(),c.name(),"",1000,1,"",c.displayName(),"")).toList();
        var bot=new ChatBotService(products);var answer=bot.reply("Собери ПК до 10000 рублей для игр, ОЗУ 32 ГБ");
        assertTrue(answer.build().isEmpty());assertTrue(answer.text().contains("проверенные характеристики"));
    }
    @Test void alternativeCardsHonorCountBudgetAndAcceptedCapacities() {
        var bot=new ChatBotService(catalog);bot.reply("Собери ПК до 500000 рублей для игр, ОЗУ 32 ГБ, SSD 2 ТБ");
        var answer=bot.reply("Покажи 2 варианта сборки");
        assertEquals(2,answer.content().size());assertTrue(answer.lead().contains("включая базовый"));
        var cards=answer.content().stream().map(ChatBotService.BuildContent.class::cast).toList();
        assertEquals(2,cards.stream().map(c->c.parts().stream().map(ChatBotService.ProductInfo::id).toList()).distinct().count());
        assertTrue(cards.stream().allMatch(c->c.total().compareTo(new BigDecimal("500000"))<=0));
        for(var card:cards) {
            var data=new LocalKnowledgeRepository().load(card.parts().stream().map(ChatBotService.ProductInfo::toProduct).toList());
            assertTrue(data.components().stream().filter(c->c.category()==ComponentCategory.RAM).allMatch(c->c.specs().fact(ComponentSpecs.CAPACITY_GB).orElseThrow().value()>=32));
            assertTrue(data.components().stream().filter(c->c.category()==ComponentCategory.STORAGE).allMatch(c->c.specs().fact(ComponentSpecs.CAPACITY_GB).orElseThrow().value()>=2000));
        }
        assertTrue(new ChatBotService(List.of()).reply("Покажи варианты сборки").text().contains("бюджет"));
    }
    private void assertCapacity(ChatBotService.Reply answer,ComponentCategory category,int minimum) {
        assertFalse(answer.build().isEmpty(),answer.text());
        var snapshot=new LocalKnowledgeRepository().load(answer.build());
        var component=snapshot.components().stream().filter(c->c.category()==category).findFirst().orElseThrow();
        var capacity=component.specs().fact(ComponentSpecs.CAPACITY_GB).orElseThrow();
        assertTrue(capacity.verified());assertTrue(capacity.value()>=minimum);
    }
}
