package com.example.authapp.domain;

import java.math.BigDecimal;
import java.util.*;
import static com.example.authapp.domain.ComponentSpecs.*;
import static com.example.authapp.domain.ComponentCategory.*;

final class DomainFixtures {
    private static final String SOURCE="https://example.test/synthetic-fixture";
    static <T> Builder set(Builder b,Attribute<T> attribute,T value) {
        return b.set(attribute,value,SOURCE,Verification.VERIFIED);
    }
    static CatalogComponent part(int id,ComponentCategory category,ComponentSpecs specs) {
        return new CatalogComponent(id,"TEST "+category,category,new BigDecimal("100.00"),1,specs);
    }
    static List<CatalogComponent> verifiedBuild() {
        var cpu=builder();set(cpu,SOCKET,"AM5");set(cpu,MODEL_ID,"TEST_CPU_A");set(cpu,MAX_POWER_WATTS,100);
        var board=builder();set(board,SOCKET,"AM5");set(board,SUPPORTED_CPU_MODELS,Set.of("TEST_CPU_A"));
        set(board,MEMORY_TYPE,"DDR5");set(board,MAX_MEMORY_GB,64);set(board,MEMORY_SLOTS,4);
        set(board,FORM_FACTOR,"ATX");set(board,SUPPORTED_STORAGE_INTERFACES,Set.of("NVME_PCIE"));
        set(board,REQUIRED_POWER_CONNECTORS,Set.of("ATX24x1","EPS8x1"));
        var gpu=builder();set(gpu,LENGTH_MM,300);set(gpu,MAX_POWER_WATTS,200);set(gpu,MIN_PSU_WATTS,550);
        set(gpu,REQUIRED_POWER_CONNECTORS,Set.of("PCIE8x1"));
        var ram=builder();set(ram,MEMORY_TYPE,"DDR5");set(ram,CAPACITY_GB,16);set(ram,MODULE_COUNT,2);
        var drive=builder();set(drive,STORAGE_INTERFACE,"NVME_PCIE");set(drive,CAPACITY_GB,1000);
        var psu=builder();set(psu,PSU_WATTS,650);set(psu,PSU_FORM_FACTOR,"ATX");
        set(psu,POWER_CONNECTORS,Set.of("PCIE8x1","ATX24x1","EPS8x1"));
        var chassis=builder();set(chassis,SUPPORTED_FORM_FACTORS,Set.of("ATX","MATX"));
        set(chassis,MAX_GPU_LENGTH_MM,330);set(chassis,MAX_COOLER_HEIGHT_MM,165);set(chassis,SUPPORTED_PSU_FORM_FACTORS,Set.of("ATX"));
        var cooler=builder();set(cooler,COOLER_KIND,"AIR");set(cooler,SUPPORTED_SOCKETS,Set.of("AM5"));set(cooler,HEIGHT_MM,155);set(cooler,COOLING_WATTS,150);
        return List.of(part(1,CPU,cpu.build()),part(2,MOTHERBOARD,board.build()),part(3,GPU,gpu.build()),
                part(4,RAM,ram.build()),part(5,STORAGE,drive.build()),part(6,PSU,psu.build()),
                part(7,CASE,chassis.build()),part(8,COOLER,cooler.build()));
    }
}
