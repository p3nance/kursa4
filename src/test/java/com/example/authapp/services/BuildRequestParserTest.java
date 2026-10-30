package com.example.authapp.services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BuildRequestParserTest {
    private final BuildRequestParser parser=new BuildRequestParser();
    @Test void recognizesBothOrdersUnitsAndDecimalTerabytes() {
        for(String input:List.of("32 ГБ ОЗУ и SSD 2 ТБ","ОЗУ минимум 32 gb, 2 tb SSD","оперативной памяти 32 ГБ, накопитель от 2 ТБ")) {
            var parsed=parser.parse(input);assertTrue(parsed.problems().isEmpty(),()->input+parsed.problems());
            assertEquals(32,parsed.ram());assertEquals(2000,parsed.storage());
        }
        assertEquals(500,parser.parse("SSD 0,5 ТБ").storage());
        assertEquals(1000,parser.parse("1\u202f000 ГБ SSD").storage());
    }
    @Test void rejectsInvalidAmbiguousAndMaximumOnlyCapacitiesAtomically() {
        var previous=new BuildRequestParser.Preferences(16,1000,true);
        for(String input:List.of("ОЗУ 0 ГБ","ОЗУ -32 ГБ","32.5 ГБ ОЗУ","SSD 999999999999 ТБ","ОЗУ 32 ГБ и ОЗУ 64 ГБ","до 32 ГБ ОЗУ","ОЗУ не больше 32 ГБ","ОЗУ 32")) {
            var parsed=parser.parse(input);assertFalse(parsed.problems().isEmpty(),input);
            assertEquals(previous,parsed.apply(previous),input);
        }
    }
    @Test void handlesStrictDraftAndResetWithoutChangingOtherPreferences() {
        var preferences=parser.parse("ОЗУ 32 ГБ, SSD 2 ТБ, только проверенная совместимость").apply(BuildRequestParser.Preferences.defaults());
        assertTrue(preferences.requireVerified());
        var refined=parser.parse("теперь ОЗУ 64 ГБ, можно черновую сборку").apply(preferences);
        assertEquals(64,refined.minRamGb());assertEquals(2000,refined.minStorageGb());assertFalse(refined.requireVerified());
        assertEquals(BuildRequestParser.Preferences.defaults(),parser.parse("Сбрось ограничения").apply(refined));
        assertEquals(16,parser.parse("Собери ПК, сбрось ограничения, ОЗУ 16 ГБ").apply(refined).minRamGb());
        assertFalse(parser.parse("Только проверенная совместимость, можно черновую сборку").problems().isEmpty());
    }
    @Test void profileDefaultsDoNotOverrideAnExplicitMinimum() {
        var preferences=parser.parse("ОЗУ 64 ГБ").apply(BuildRequestParser.Preferences.defaults());
        var request=preferences.request(new BigDecimal("100000"),BuildRecommendationService.Usage.OFFICE);
        assertEquals(64,request.minRamGb());assertEquals(256,request.minStorageGb());
    }
    @Test void unsupportedOwnedPartsAndPlatformsAreNotPresentedAsApplied() {
        for(String input:List.of("У меня уже есть RTX 4070","Собери ПК только AMD","Собери ПК без видеокарты","Собери ПК на DDR5","Собери ПК с RTX 4070","Собери ПК с HDD 2 ТБ"))
            assertFalse(parser.parse(input).problems().isEmpty(),input);
        assertFalse(parser.parse("Для игр").hasChanges());
    }
}
