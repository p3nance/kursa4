package com.example.authapp.services;

import com.example.authapp.domain.*;
import com.example.authapp.models.Product;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogNameAliasesTest {
    private List<Product> products;
    @BeforeEach void load() throws Exception {products=ChatQualityProbe.loadCatalog();}
    private static List<Integer> found(ChatBotService.Reply reply){return reply.content().stream().filter(ChatBotService.ProductsContent.class::isInstance).map(ChatBotService.ProductsContent.class::cast).flatMap(p->p.items().stream()).map(ChatBotService.ProductInfo::id).toList();}
    @Test void everyCurrentProductCanBeFoundByItsEnglishNameAndEveryCuratedRussianName() {
        var bot=new ChatBotService(products);int checked=0;
        for(var product:products) {
            var aliases=CatalogNameAliases.aliases(product.getId(),product.getName(),product.getCategory());assertFalse(aliases.isEmpty(),product.getName());
            assertEquals(List.of(product.getId()),found(bot.reply("Найди "+product.getName())),product.getName());
            for(String alias:aliases){assertEquals(List.of(product.getId()),found(bot.reply("Найди "+alias)),alias);checked++;}
        }
        assertEquals(89,products.size());assertEquals(117,checked);
    }
    @Test void allSystemBlockAliasesResolveToTheSameIdentityAsEnglishAndAreRecognizedInSentences() {
        var knowledge=new LocalKnowledgeRepository().load(products);var resolver=new ModelResolver(knowledge.components());
        for(var part:knowledge.components())for(var alias:CatalogNameAliases.forComponent(part)) {
            var result=resolver.resolve(alias);assertEquals(ModelResolver.Status.EXACT,result.status(),alias);
            assertEquals(List.of(part.productId()),result.candidates().stream().map(CatalogComponent::productId).toList(),alias);
            assertEquals(List.of(part.productId()),resolver.mentions("нужна модель "+alias+" для сборки").stream().map(CatalogComponent::productId).toList(),alias);
        }
    }
    @Test void namesDoNotTransferToReusedIdsRenamedProductsOrDifferentCategories() {
        var original=products.stream().filter(p->p.getId()==15).findFirst().orElseThrow();
        assertTrue(CatalogNameAliases.aliases(15,"Another model",original.getCategory()).isEmpty());
        assertTrue(CatalogNameAliases.aliases(15,original.getName(),"Процессоры").isEmpty());
        var changed=ChatBotService.ProductInfo.from(original).toProduct();changed.setName("Unrelated Device");
        assertTrue(CatalogNameAliases.exactProductIds(List.of(changed),"РТХ4080 Супер").isEmpty());
    }
    @Test void russianVariantsAndCapacityDoNotCollapseDistinctModels() {
        var resolver=new ModelResolver(new LocalKnowledgeRepository().load(products).components());
        for(String input:List.of("РТХ 4070 ти","РТХ 4060 ти 8ГБ","РТХ4060ти8ГБ","RTX4070 Ti12GB","РТХ4070 тиСупер12ГБ","РТХ 4060 ти 24ГБ","Райзен 5 7600х3д","интел ай5 12400к")) {
            assertNotEquals(ModelResolver.Status.EXACT,resolver.resolve(input).status(),input);
            assertTrue(resolver.mentions(input).isEmpty(),input);
        }
        assertEquals(5,resolver.resolve("Интел ай5 12400ф").candidates().getFirst().productId());
        assertEquals(20,resolver.resolve("РТХ4060 ти16ГБ").candidates().getFirst().productId());
        assertEquals(7,resolver.resolve("Райзен 7600х").candidates().getFirst().productId());
    }
    @Test void russianNamesAlsoWorkInComparisonAndInAnActualBuildEdit() {
        var bot=new ChatBotService(products);
        var comparison=bot.reply("Сравни Интел Кор ай7 14700К и Интел Кор ай5 14600К");
        assertInstanceOf(ChatBotService.BenchmarksContent.class,comparison.content().getFirst());
        bot.reply("Собери ПК до 200000 рублей для игр");
        var edited=bot.reply("Замени видеокарту на РТХ4080 Супер");
        var build=assertInstanceOf(ChatBotService.BuildContent.class,edited.content().getFirst());assertTrue(build.parts().stream().anyMatch(p->p.id()==15));
    }
}
