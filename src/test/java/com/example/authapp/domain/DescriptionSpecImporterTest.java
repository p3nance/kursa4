package com.example.authapp.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.authapp.domain.ComponentSpecs.*;

class DescriptionSpecImporterTest {
    @Test void acceptsWhitespaceButKeepsUnverifiedProvenance() {
        var specs=DescriptionSpecImporter.parse("socket: AM5; memory: DDR5; psu_power: 650; tdp: 65");
        assertEquals("AM5",specs.value(SOCKET).orElseThrow());
        assertEquals(650,specs.value(PSU_WATTS).orElseThrow());
        assertEquals(Verification.IMPORTED,specs.fact(SOCKET).orElseThrow().verification());
        assertTrue(specs.value(MAX_POWER_WATTS).isEmpty(),"TDP is not maximum power");
    }
    @Test void conflictingValuesAndAliasesAreDiscarded() {
        var specs=DescriptionSpecImporter.parse("socket:AM5 socket:LGA1700 memory:DDR4 memory_type:DDR5");
        assertTrue(specs.value(SOCKET).isEmpty());assertTrue(specs.value(MEMORY_TYPE).isEmpty());
    }
    @Test void invalidNumbersAndProseDoNotBecomeFacts() {
        var specs=DescriptionSpecImporter.parse("Отличная память 32 ГБ; psu_power:-5; gpu_power:300W");
        assertTrue(specs.attributes().isEmpty());assertTrue(DescriptionSpecImporter.parse(null).attributes().isEmpty());
    }
}
