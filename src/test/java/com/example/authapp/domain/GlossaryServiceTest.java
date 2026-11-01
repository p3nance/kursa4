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
    @Test void memoryTermsKeepDifferentMeaningsAndManufacturerSources() {
        var glossary=new GlossaryService();
        var ddr=glossary.search("Что означает DDR5?",1).getFirst();
        assertEquals("DDR5",ddr.term());assertEquals("www.kingston.com",ddr.source().getHost());
        assertTrue(ddr.definition().contains("не взаимозаменяемы"));
        var vram=glossary.search("Хочу понять термин VRAM",1).getFirst();
        assertEquals("VRAM",vram.term());assertEquals("www.nvidia.com",vram.source().getHost());
        assertNotEquals(glossary.search("RAM",1).getFirst().term(),vram.term());
    }
}
