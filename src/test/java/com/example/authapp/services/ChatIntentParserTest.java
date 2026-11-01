package com.example.authapp.services;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.authapp.services.ChatIntentParser.Intent.*;

class ChatIntentParserTest {
    private final ChatIntentParser parser=new ChatIntentParser();
    @Test void explicitPairActionsAndExistingViewButtonsDoNotBecomeModelQueries() {
        for(String raw:List.of("Поменяй местами","Сравни наоборот","Наоборот, пожалуйста","Не меняй местами","Порядок не меняй")) {
            var intent=parser.parse(raw);assertEquals(COMPARE_ORDER,intent.intent(),raw);assertTrue(intent.pair().isEmpty());
        }
        for(String raw:List.of("Сравнить по бенчмаркам","Сравнить характеристики","Показать модели с общим тестом"))
            assertEquals(COMPARE_VIEW,parser.parse(raw).intent(),raw);
    }
    @Test void pairOrderGrammarDoesNotSwallowExtraActionsOrNamedHardwareRequests() {
        for(String raw:List.of("Поменяй местами и увеличь бюджет на1000рублей","Поменяй процессор на Ryzen7600",
                "Сравни наоборот RTX4060 и RX7600","Не меняй цену товара"))
            assertNotEquals(COMPARE_ORDER,parser.parse(raw).intent(),raw);
    }
    @Test void naturalComparisonPhrasesExtractTheSameTwoModelQueries() {
        for(String phrase:List.of("сравни 14900K и 7800X3D", "сравнить 14900K с 7800X3D",
                "сравнение 14900K и 7800X3D", "чем отличается 14900K от 7800X3D",
                "что лучше 14900K или 7800X3D", "что выбрать 14900K или 7800X3D",
                "можешь сравнить 14900K и 7800X3D", "можно сравнить 14900K и 7800X3D",
                "сравни, пожалуйста, 14900K и 7800X3D", "в чём разница между 14900K и 7800X3D",
                "14900K vs 7800X3D", "14900Kvs7800X3D", "14900K против 7800X3D")) {
            var result=parser.parse(phrase);
            assertEquals(COMPARE,result.intent(),phrase);
            assertEquals(List.of("14900K","7800X3D"),result.pair(),phrase);
        }
    }
    @Test void gamingPurposeIsKeptInBodyButDoesNotPolluteModelQueries() {
        var result=parser.parse("что лучше для игр 14900K или 7800X3D?");
        assertEquals(COMPARE,result.intent());
        assertTrue(result.body().contains("для игр"));
        assertEquals(List.of("14900K","7800X3D"),result.pair());
    }
    @Test void conjunctionsInBuildPurposeAnswersDoNotBecomeModelComparison() {
        assertEquals(OTHER,parser.parse("для учёбы и офиса").intent());
        assertEquals(OTHER,parser.parse("игры и работа").intent());
        assertEquals(OTHER,parser.parse("а для игр?").intent());
        assertEquals(BUILD,parser.parse("Собери ПК для учёбы и офиса до 100к").intent());
    }
    @Test void explicitUnknownModelsAndSingleSideRemainComparisonForClarification() {
        assertEquals(List.of("Example A","Example B"),parser.parse("Сравни Example A и Example B").pair());
        assertEquals(COMPARE,parser.parse("Сравни видеокарты").intent());
        assertEquals(List.of("видеокарты"),parser.parse("Сравни видеокарты").pair());
        assertEquals(COMPARE,parser.parse("сравни со вторым").intent());
    }
    @Test void searchDefinitionAndBuildHaveExplicitPrecedenceOverImplicitConjunctions() {
        for(String phrase:List.of("найди RTX 4070", "можешь найти RTX 4070", "покажи RTX 4070", "есть ли у вас RTX 4070")) {
            var result=parser.parse(phrase);assertEquals(SEARCH,result.intent(),phrase);assertEquals("RTX 4070",result.body(),phrase);
        }
        assertEquals(SEARCH,parser.parse("найди 4070 или 4080").intent());
        assertEquals(DEFINE,parser.parse("Что такое DDR4 и DDR5?").intent());
        assertEquals("сокет",parser.parse("Объясни сокет").body());
        assertEquals(BUILD,parser.parse("Хочу компьютер до 100к").intent());
        assertEquals(BUILD,parser.parse("Подбери комплектующие для работы").intent());
    }
    @Test void ownershipAndVariantWordsCannotHijackAnExplicitSearchOrDefinition() {
        assertEquals(SEARCH,parser.parse("Найди RTX 4070, у меня есть Ryzen 5 7600").intent());
        assertEquals(DEFINE,parser.parse("Что значит фраза «у меня есть RTX 4070»?").intent());
        assertEquals(DEFINE,parser.parse("Что такое варианты сборки?").intent());
        assertEquals(SEARCH,parser.parse("Найди RTX 4070 и варианты сборки").intent());
        assertEquals(BUILD,parser.parse("Покажи 2 варианта сборки").intent());
        assertEquals(BUILD,parser.parse("Можешь показать варианты сборки?").intent());
    }
    @Test void helpDoesNotDemandUserMeasurementsAndControlIntentsAreWholePhrases() {
        assertEquals(COMPARE_HELP,parser.parse("Как сравнить комплектующие по бенчмаркам?").intent());
        assertEquals(COMPARE_HELP,parser.parse("бенчмарки").intent());
        assertEquals(RESET,parser.parse("Начнём заново!").intent());
        assertEquals(RESET,parser.parse("Новый диалог").intent());
        assertEquals(GREETING,parser.parse("Добрый день").intent());
        assertEquals(THANKS,parser.parse("Спасибо!").intent());
        assertEquals(COMPARE,parser.parse("Привет, сравни 14900K и 7800X3D").intent());
        assertEquals(OTHER,parser.parse(null).intent());
    }
    @Test void boundedCategoryAndModeWrappersDoNotBecomePartOfModelIdentity() {
        for(String phrase:List.of("сравни процессоры 14700K и процессор 14600K",
                "сравни CPU 14700K и CPU 14600K по бенчмаркам",
                "сравни по характеристикам 14700K и 14600K",
                "сравни модели 14700K и 14600K по цене")) {
            var result=parser.parse(phrase);assertEquals(COMPARE,result.intent(),phrase);
            assertEquals(List.of("14700K","14600K"),result.pair(),phrase);
        }
        assertEquals(List.of("4070 SUPER Ti","4070"),parser.parse("сравни видеокарты 4070 SUPER Ti и 4070 по бенчмаркам").pair());
        assertEquals(List.of("4060 Ti 16GB","4070"),parser.parse("сравни видеокарты 4060 Ti 16GB и 4070").pair());
    }
    @Test void aTrailingWorkPurposeKeepsItsScenarioOutsideModelQueries() {
        for(String purpose:List.of("для работы в Blender", "для монтажа в DaVinci Resolve", "для учёбы в CAD")) {
            var result=parser.parse("сравни 14700K и 14600K "+purpose);
            assertEquals(List.of("14700K","14600K"),result.pair(),purpose);
            assertTrue(result.body().contains(purpose),purpose);
        }
    }

}
