package com.example.authapp.domain;

import com.example.authapp.services.GlossaryService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlossaryServiceTest {
    @Test void findsRussianAliasInsideQuestionWithSource() {
        var terms=new GlossaryService().search("Что такое оперативная память?",3);
        assertEquals("RAM",terms.getFirst().term());assertNotNull(terms.getFirst().source());
    }
    @Test void noSubstringFalsePositiveAndEmptyInput() {
        var service=new GlossaryService();
        assertTrue(service.search("myCPUsecret",3).isEmpty());assertTrue(service.search("",3).isEmpty());
        assertTrue(service.search(null,3).isEmpty());assertTrue(service.search("CPU",0).isEmpty());
    }
}
