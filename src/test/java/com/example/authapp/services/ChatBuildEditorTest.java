package com.example.authapp.services;

import com.example.authapp.models.Product;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatBuildEditorTest {
    private List<Product> catalog;
    @BeforeEach void load() throws Exception {catalog=ChatQualityProbe.loadCatalog();}
    private ChatBotService bot(){return new ChatBotService(catalog);}
    private static ChatBotService.BuildContent build(ChatBotService.Reply r){return assertInstanceOf(ChatBotService.BuildContent.class,r.content().getFirst());}
    private static ChatBotService.ProductInfo gpu(ChatBotService.BuildContent b){return b.parts().stream().filter(p->p.category().equals("Видеокарты")).findFirst().orElseThrow();}
    private static void sameOtherParts(ChatBotService.BuildContent before,ChatBotService.BuildContent after,String category) {
        assertEquals(before.parts().stream().filter(p->!p.category().equals(category)).toList(),after.parts().stream().filter(p->!p.category().equals(category)).toList());
        assertEquals(8,after.parts().size());
        BigDecimal sum=after.parts().stream().filter(p->!after.ownedProductIds().contains(p.id())).map(ChatBotService.ProductInfo::price).reduce(BigDecimal.ZERO,BigDecimal::add);
        assertEquals(0,sum.compareTo(after.total()));
    }
    @Test void exactUser4080RequestRequiresConfirmationThenActuallyReplacesGpuAndReportsBudget() {
        var bot=bot();var old=build(bot.reply("Собери ПК до 100000 рублей для игр"));
        var question=bot.reply("замени видеокарту на 4080");
        assertTrue(question.content().isEmpty());assertTrue(question.text().contains("4080 SUPER"));
        assertEquals(new BigDecimal("100000"),bot.summary().budget());
        var reply=bot.reply(question.suggestions().getFirst());var updated=build(reply);
        assertEquals(15,gpu(updated).id());sameOtherParts(old,updated,"Видеокарты");
        assertTrue(updated.total().compareTo(updated.budget())>0);assertTrue(reply.lead().contains("Бюджет превышен"));assertFalse(updated.verified());
        assertEquals(new BigDecimal("100000"),bot.summary().budget());
    }
    @Test void exact4070ReplacementDoesNotRegenerateOtherSevenPartsAndKeepsCurrentMetadata() {
        var bot=bot();var old=build(bot.reply("Собери ПК до 200000 рублей для игр"));
        var updated=build(bot.reply("Замени видеокарту на RTX 4070"));
        assertEquals(13,gpu(updated).id());sameOtherParts(old,updated,"Видеокарты");
        var product=catalog.stream().filter(p->p.getId()==13).findFirst().orElseThrow();
        assertEquals(ChatBotService.ProductInfo.from(product),gpu(updated));
    }
    @Test void confirmedConflictsStayExplicitInTheEditedDraft() {
        var bot=bot();bot.reply("Собери ПК до 200000 рублей для игр");
        var response=bot.reply("Замени процессор на Intel Core i9-14900K");var draft=build(response);
        assertTrue(draft.parts().stream().anyMatch(p->p.id()==4));
        assertFalse(draft.verified());assertFalse(draft.findings().isEmpty());
        assertTrue(response.lead().contains("конфликты") || response.lead().contains("проверки"));
    }
    @Test void consecutiveEditsStartFromTheUpdatedComposition() {
        var bot=bot();bot.reply("Собери ПК до 200000 рублей для игр");
        var first=build(bot.reply("Замени видеокарту на RTX 4070"));
        var drive=catalog.stream().filter(p->p.getCategory().equals("SSD-накопители") && p.getStock()>0 && first.parts().stream().noneMatch(x->x.id()==p.getId())).findFirst().orElseThrow();
        var second=build(bot.reply("Замени SSD на "+drive.getName()));
        assertTrue(second.parts().stream().anyMatch(p->p.id()==drive.getId()));sameOtherParts(first,second,"SSD-накопители");
    }
    @Test void choosingOneOfSeveralProposedVariantsPreservesThatVariant() {
        var bot=bot();var reply=bot.reply("Собери ПК до 200000 рублей для игр, два варианта");
        var cards=reply.content().stream().filter(ChatBotService.BuildContent.class::isInstance).map(ChatBotService.BuildContent.class::cast).toList();assertEquals(2,cards.size());
        assertTrue(bot.reply("Замени видеокарту на RTX4070").text().contains("В какой"));
        var updated=build(bot.reply("Вариант 2"));assertEquals(13,gpu(updated).id());sameOtherParts(cards.get(1),updated,"Видеокарты");
    }
    @Test void cancellationAndNegationKeepTheComposition() {
        var bot=bot();var old=build(bot.reply("Собери ПК до 200000 рублей для игр"));
        bot.reply("Замени видеокарту на 4080");assertTrue(bot.reply("Отмена").text().contains("без изменений"));
        assertTrue(bot.reply("Не меняй видеокарту").text().contains("без изменений"));
        var drive=catalog.stream().filter(p->p.getCategory().equals("SSD-накопители") && p.getStock()>0).findFirst().orElseThrow();
        var updated=build(bot.reply("Замени SSD на "+drive.getName()));assertEquals(gpu(old),gpu(updated));
    }
    @Test void modelCategoryMismatchAndUnavailableModelDoNotChangeTheCurrentBuild() {
        var bot=bot();var old=build(bot.reply("Собери ПК до 200000 рублей для игр"));
        assertTrue(bot.reply("Замени процессор на RTX4070").text().contains("другой категории"));
        assertTrue(bot.reply("Замени видеокарту на RTX9990").text().contains("не найдена"));bot.reply("Отмена");
        var updated=build(bot.reply("Замени видеокарту на RTX4070"));sameOtherParts(old,updated,"Видеокарты");
    }
    @Test void replacementOfOwnedPartBecomesAPurchaseWithoutLosingOtherParts() {
        var bot=bot();bot.reply("У меня есть NVIDIA GeForce RTX 4060 8GB");
        var old=build(bot.reply("Собери ПК до 200000 рублей для игр"));assertTrue(old.ownedProductIds().contains(12));
        var updated=build(bot.reply("Замени видеокарту на RTX4070"));sameOtherParts(old,updated,"Видеокарты");
        assertTrue(updated.ownedProductIds().isEmpty());assertTrue(bot.summary().ownedParts().isEmpty());assertEquals(13,gpu(updated).id());
    }
    @Test void outOfStockAndCrossCategoryModelReplacementAreRejectedWithoutLosingTheBuild() {
        var modified=catalog.stream().map(p->{var fresh=ChatBotService.ProductInfo.from(p).toProduct();if(p.getId()==13)fresh.setStock(0);return fresh;}).toList();
        var bot=new ChatBotService(modified);var old=build(bot.reply("Собери ПК до 200000 рублей для игр"));
        assertTrue(bot.reply("Замени видеокарту на RTX4070").text().contains("недоступна"));
        bot.reply("Отмена");assertTrue(bot.reply("Замени RTX4060 на Intel Core i9-14900K").content().isEmpty());
        bot.reply("Отмена");var updated=build(bot.reply("Замени видеокарту на RTX4080 SUPER"));sameOtherParts(old,updated,"Видеокарты");
        assertEquals(15,gpu(updated).id());
    }
    @Test void draftsAreAtomicAndResetAndCatalogRefreshInvalidatePendingEdits() {
        var bot=bot();bot.reply("Собери ПК до 200000 рублей для игр");
        var draft=bot.fork();draft.reply("Замени видеокарту на 4080");draft.reply("Да");
        assertEquals(13,gpu(build(bot.reply("Замени видеокарту на RTX4070"))).id());
        bot.commitConversation(draft);var changed=build(bot.reply("Замени корпус на Fractal Design Pop Air RGB"));assertEquals(15,gpu(changed).id());
        bot.reply("Новый диалог");assertTrue(bot.reply("Замени видеокарту на RTX4070").text().contains("Сначала"));
        bot.reply("Собери ПК до 200000 рублей для игр");bot.reply("Замени видеокарту на 4080");
        var stale=bot.fork();bot.updateCatalog(catalog);bot.commitConversation(stale);
        assertTrue(bot.reply("Замени видеокарту на RTX4070").text().contains("Сначала"));
    }
}
