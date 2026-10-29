package com.example.authapp.services;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.authapp.services.ChatIntentParser.Intent.*;

class ChatIntentParserTest {
    private final ChatIntentParser parser=new ChatIntentParser();
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
}
