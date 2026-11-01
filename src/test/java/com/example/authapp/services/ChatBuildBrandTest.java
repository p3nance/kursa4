package com.example.authapp.services;

import com.example.authapp.models.Product;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatBuildBrandTest {
    private List<Product> products;
    @BeforeEach void load() throws Exception {products=ChatQualityProbe.loadCatalog();}
    private ChatBotService bot(){var bot=new ChatBotService(products);bot.reply("Собери ПК до 200000 рублей для игр");return bot;}
    @Test void bothScreenshotPhrasesOfferOnlyAvailableAmdCpusAndKeepTheBudget() {
        for(String vendor:List.of("амд","AMD","АмД","амд райзен","Ryzen")) {
            var bot=bot();var before=bot.summary();var reply=bot.reply("поменяй процессор на "+vendor);
            assertTrue(reply.content().isEmpty());assertFalse(reply.suggestions().isEmpty(),vendor);assertTrue(reply.text().contains("Выберите конкретную модель AMD"));
            for(String option:reply.suggestions())assertTrue(products.stream().anyMatch(p->p.getName().equals(option) && p.getManufacturer().equals("AMD") && p.getCategory().equals("Процессоры") && p.getStock()>0));
            assertEquals(before,bot.summary());
        }
    }
    @Test void cpuAbbreviationsAreCommandsAndNegationKeepsTheBuild() {
        for(String word:List.of("проц","проца","цп","цпу","CPU")) {
            var bot=bot();var before=bot.summary();var reply=bot.reply("поменяй "+word+" на амд");
            assertTrue(reply.content().isEmpty());assertTrue(reply.text().contains("Выберите конкретную модель AMD"),word);
            assertTrue(bot.reply("не меняй "+word).text().contains("без изменений"));assertEquals(before,bot.summary());
        }
        assertFalse(ChatBuildEditor.isCommand("поменяй процент скидки на 20"));
        assertFalse(ChatBuildEditor.isCommand("поменяй процесс оформления заказа"));
    }
    @Test void partialNumbersKeepCategoryMakerFamilyAndTierAndNeverDiscardExplicitSuffixes() {
        for(String query:List.of("амд 7800","AMD7800","Ryzen 7800","AMD Ryzen 7 7800")) {
            var bot=bot();var reply=bot.reply("поменяй проц на "+query);
            assertTrue(reply.content().isEmpty());assertEquals(List.of("AMD Ryzen 7 7800X3D"),reply.suggestions(),query);
        }
        var gpu=bot().reply("поменяй видеокарту на AMD7800");
        assertEquals(List.of("AMD Radeon RX 7800 XT 16GB"),gpu.suggestions());
        for(String query:List.of("Intel7800","NVIDIA7800","AMD Ryzen 5 7800","AMD Radeon RX7800","AMD7800X","AMD17800")) {
            var reply=bot().reply("поменяй проц на "+query);
            assertTrue(reply.content().isEmpty());assertTrue(reply.suggestions().isEmpty(),query);
        }
        assertTrue(bot().reply("поменяй видеокарту на RTX7800").suggestions().isEmpty());
    }
    @Test void confirmedPartialCpuChangesOnlyTheCpuAndPreservesTheExactTotalAndConflict() {
        var bot=new ChatBotService(products);var before=assertInstanceOf(ChatBotService.BuildContent.class,
                bot.reply("Собери ПК до 150000 рублей для монтажа").content().getFirst());
        bot.reply("поменяй проц на амд 7800");
        var reply=bot.reply("AMD Ryzen 7 7800X3D");var after=assertInstanceOf(ChatBotService.BuildContent.class,reply.content().getFirst());
        assertEquals(before.parts().stream().filter(p->!p.category().equals("Процессоры")).toList(),after.parts().stream().filter(p->!p.category().equals("Процессоры")).toList());
        assertTrue(after.parts().stream().anyMatch(p->p.id()==8));assertEquals(before.budget(),after.budget());
        assertEquals(0,after.total().compareTo(after.parts().stream().map(ChatBotService.ProductInfo::price).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add)));
        assertTrue(reply.lead().contains("конфликты"));assertFalse(after.verified());
        var exact=bot.reply("поменяй проц на Интел14900К");
        assertTrue(assertInstanceOf(ChatBotService.BuildContent.class,exact.content().getFirst()).parts().stream().anyMatch(p->p.id()==4));
    }
    @Test void choosingARussianNameChangesOnlyTheCpuAndShowsActualCompatibility() {
        var bot=new ChatBotService(products);var initial=bot.reply("Собери ПК до 200000 рублей для игр");
        var before=assertInstanceOf(ChatBotService.BuildContent.class,initial.content().getFirst());
        bot.reply("поменяй процессор на амд");
        var reply=bot.reply("АМД Райзен 9 7950Икс");var after=assertInstanceOf(ChatBotService.BuildContent.class,reply.content().getFirst());
        assertTrue(after.parts().stream().anyMatch(p->p.id()==10));
        assertEquals(before.parts().stream().filter(p->!p.category().equals("Процессоры")).toList(),after.parts().stream().filter(p->!p.category().equals("Процессоры")).toList());
        assertFalse(after.verified());assertFalse(after.findings().isEmpty());
    }
    @Test void fifthModelIsSelectableAndGenericYesDoesNotPickOneOfSeveral() {
        var bot=bot();var question=bot.reply("Поменяй процессор на AMD");assertEquals(5,question.suggestions().size());
        assertTrue(bot.reply("Да").content().isEmpty());
        var fifth=bot.reply("пятый");var build=assertInstanceOf(ChatBotService.BuildContent.class,fifth.content().getFirst());
        assertTrue(build.parts().stream().anyMatch(p->p.name().equals(question.suggestions().get(4))));
    }
    @Test void cancellingOrRefreshingBrandChoiceCannotApplyItLater() {
        var bot=bot();bot.reply("Поменяй процессор на AMD");assertTrue(bot.reply("Отмена").text().contains("без изменений"));
        bot.reply("Поменяй процессор на AMD");var draft=bot.fork();bot.updateCatalog(products);bot.commitConversation(draft);
        assertTrue(bot.reply("Поменяй процессор на AMD").text().contains("Сначала"));
    }
    @Test void aCpuFamilyCannotBecomeAGpuAndUnavailableVendorsDoNotInventProducts() {
        var bot=bot();
        assertTrue(bot.reply("Поменяй видеокарту на Ryzen").text().contains("другой категории"));
        assertTrue(bot.reply("Поменяй процессор на NVIDIA").text().contains("в наличии нет"));
        assertTrue(bot.reply("Поменяй процессор на амд").text().contains("Выберите конкретную модель AMD"));
    }
}
