package com.example.authapp.services;

import com.example.authapp.dto.ProductDTO;
import com.example.authapp.domain.*;
import com.example.authapp.models.Product;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OwnedChatBotTest {
    private String previous;
    private List<Product> catalog;
    @BeforeEach void setup() throws Exception {
        previous=System.getProperty("techstore.knowledge.dir");System.setProperty("techstore.knowledge.dir",Path.of("src/main/resources/knowledge").toAbsolutePath().toString());
        catalog=new ArrayList<>();var gson=new Gson();
        try(var reader=Files.newBufferedReader(Path.of("src/test/resources/knowledge/catalog-reference.json"))) {
            for(var item:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("products")) {
                var p=gson.fromJson(item,ProductDTO.class);catalog.add(new Product(p.id,p.name,p.description,p.price,p.stock,"",p.category,p.manufacturer));
            }
        }
    }
    @AfterEach void restore(){if(previous==null)System.clearProperty("techstore.knowledge.dir");else System.setProperty("techstore.knowledge.dir",previous);}
    @Test void ownedStockZeroGpuIsIncludedInCompatibilityButNotThePurchase() {
        int id=id("RTX 4070");var gpu=catalog.stream().filter(p->p.getId()==id).findFirst().orElseThrow();gpu.setStock(0);
        var bot=new ChatBotService(catalog);var answer=bot.reply("У меня уже есть RTX 4070, собери остальное до 80 тысяч для игр");
        var card=card(answer);assertEquals(8,card.parts().size());assertEquals(Set.of(id),card.ownedProductIds());
        var total=card.parts().stream().filter(p->!card.ownedProductIds().contains(p.id())).map(ChatBotService.ProductInfo::price).reduce(new BigDecimal("0.00"),BigDecimal::add);
        assertEquals(total,card.total());assertTrue(card.total().compareTo(new BigDecimal("80000"))<=0);
        assertEquals(List.of(id),bot.summary().ownedParts().stream().map(ChatBotService.ProductInfo::id).toList());assertTrue(answer.text().contains("уже есть, не покупаем"));
    }
    @Test void metadataDuringAChoiceDoesNotBecomeOwnershipAndChoiceNumberDoesNotChangeBudgetOrVariantCount() {
        var options=ambiguous();var bot=new ChatBotService(options);
        var answer=bot.reply("У меня есть RTX 4070, собери ПК до 100000 рублей для игр, покажи 2 варианта сборки");
        assertTrue(answer.content().isEmpty());assertTrue(bot.summary().ownedParts().isEmpty());assertEquals(new BigDecimal("100000"),bot.summary().budget());
        answer=bot.reply("До 120000 рублей");assertTrue(answer.content().isEmpty());assertEquals(new BigDecimal("120000"),bot.summary().budget());
        answer=bot.reply("2");assertEquals(new BigDecimal("120000"),bot.summary().budget());assertEquals(2,answer.content().size());
        assertEquals("Palit GeForce RTX 4070 Dual 12GB",bot.summary().ownedParts().getFirst().name());
    }
    @Test void ownedRamModelIsNotMistakenForAPlatformConstraintAndExplicitMinimumStillApplies() {
        var ram=catalog.stream().filter(p->p.getCategory().equals("Оперативная память")&&p.getName().contains("G.Skill")).findFirst().orElseThrow();
        var bot=new ChatBotService(catalog);var answer=bot.reply("У меня есть "+ram.getName()+", собери ПК до 200000 рублей для игр");
        assertEquals(Set.of(ram.getId()),card(answer).ownedProductIds());assertNull(bot.summary().minRamGb());
        answer=bot.reply("ОЗУ 64 ГБ");assertTrue(answer.build().isEmpty());assertTrue(answer.text().contains("не подтверждают заданный минимум"));
    }
    @Test void forkResetAndCurrentCatalogRebindingCannotRestoreAReusedOwnedId() {
        var bot=new ChatBotService(catalog);bot.reply("У меня есть RTX 4070");var draft=bot.fork();draft.reply("У меня есть Ryzen 5 7600");
        assertEquals(1,bot.summary().ownedParts().size());bot.commitConversation(draft);assertEquals(2,bot.summary().ownedParts().size());
        int gpu=id("RTX 4070");var changed=new ArrayList<Product>();
        for(var p:catalog)changed.add(p.getId()==gpu?new Product(p.getId(),"A different GPU",p.getDescription(),p.getPrice(),p.getStock(),"",p.getCategory(),p.getManufacturer()):p);
        bot.updateCatalog(changed);assertTrue(bot.summary().ownedParts().stream().noneMatch(p->p.id()==gpu));
        bot.commitConversation(draft);var answer=bot.reply("Собери ПК до 200000 рублей для игр");assertTrue(answer.build().isEmpty());assertTrue(answer.text().contains("Каталог изменился"));
        bot.reset();assertTrue(bot.summary().ownedParts().isEmpty());assertNull(bot.summary().budget());
    }
    @Test void clearAndOtherExplicitIntentsDoNotChooseAnUnconfirmedOwnedModel() {
        var bot=new ChatBotService(ambiguous());bot.reply("У меня есть RTX 4070");
        assertInstanceOf(ChatBotService.TermContent.class,bot.reply("Что такое сокет?").content().getFirst());
        bot.reply("2");assertTrue(bot.summary().ownedParts().isEmpty());
        bot=new ChatBotService(catalog);bot.reply("У меня есть RTX 4070, собери ПК до 200000 рублей для игр");
        var answer=bot.reply("Сбросить имеющиеся детали; Собери ПК до 200000 рублей для игр");assertTrue(card(answer).ownedProductIds().isEmpty());assertTrue(bot.summary().ownedParts().isEmpty());
    }
    @Test void x3dInAnOwnedCpuNameDoesNotChangeTheUsageToWorkstation() {
        var bot=new ChatBotService(catalog);bot.reply("Собери ПК до 200000 рублей для игр");bot.reply("У меня есть Ryzen 7 7800X3D");
        assertEquals(BuildRecommendationService.Usage.GAMING,bot.summary().usage());
    }
    @Test void explicitSearchAndDefinitionTakePrecedenceAndDoNotRecordOwnership() {
        var bot=new ChatBotService(catalog);
        bot.reply("Найди RTX 4070, у меня есть Ryzen 5 7600");assertTrue(bot.summary().ownedParts().isEmpty());assertNull(bot.summary().budget());
        bot.reply("Что значит фраза «у меня есть RTX 4070»?");assertTrue(bot.summary().ownedParts().isEmpty());
    }
    @Test void denialAfterAcceptedOwnershipRemovesTheFreePartBeforeAnyLaterBuild() {
        var bot=new ChatBotService(catalog);bot.reply("У меня есть RTX 4070, собери ПК до 200000 рублей для игр");
        assertEquals(1,bot.summary().ownedParts().size());var response=bot.reply("У меня нет RTX 4070");
        assertTrue(response.build().isEmpty());assertTrue(bot.summary().ownedParts().isEmpty());
        assertTrue(card(bot.reply("Собери ПК до 200000 рублей для игр")).ownedProductIds().isEmpty());
    }
    @Test void naturalDeclarationClarificationKeepsTheAlreadyRecognizedGpuFree() {
        var bot=new ChatBotService(catalog);bot.reply("У меня есть RTX 4070 и Ryzen 9 9990X, собери ПК до 150000 рублей для игр");
        var response=bot.reply("У меня есть Ryzen 5 7600");var card=card(response);
        assertEquals(2,card.ownedProductIds().size());assertTrue(card.ownedProductIds().contains(id("RTX 4070")));
    }
    private int id(String query){return new ModelResolver(new LocalKnowledgeRepository().load(catalog).components()).resolve(query).candidates().getFirst().productId();}
    private static ChatBotService.BuildContent card(ChatBotService.Reply reply) {
        assertFalse(reply.content().isEmpty(),reply.text());return assertInstanceOf(ChatBotService.BuildContent.class,reply.content().getFirst());
    }
    private List<Product> ambiguous() {
        var list=new ArrayList<>(catalog);list.removeIf(p->p.getCategory().equals("Видеокарты"));
        list.add(new Product(9001,"ASUS GeForce RTX 4070 Dual 12GB","",50000,0,"","Видеокарты","ASUS"));
        list.add(new Product(9002,"Palit GeForce RTX 4070 Dual 12GB","",51000,0,"","Видеокарты","Palit"));return list;
    }
}
