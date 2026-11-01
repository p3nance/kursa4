package com.example.authapp.services;

import com.example.authapp.domain.*;
import java.util.*;
import java.util.function.BiPredicate;
import static com.example.authapp.domain.ComponentCategory.*;
import static com.example.authapp.domain.ComponentSpecs.*;

/** Checks explicit facts. COMPATIBLE means all implemented checks passed with verified facts. */
public final class CompatibilityService {
    private final RadiatorFitService radiatorFit=new RadiatorFitService();
    public enum Status { COMPATIBLE, INCOMPATIBLE, INSUFFICIENT_DATA }
    public record Finding(String code, Status status, String message) {}
    public record Result(Status status, List<Finding> findings, List<String> limitations) {
        public Result { findings = List.copyOf(findings); limitations = List.copyOf(limitations); }
        public boolean hasConflict() { return status == Status.INCOMPATIBLE; }
    }
    public Result assess(Collection<CatalogComponent> components) { return assess(components, true); }
    public Result assess(Collection<CatalogComponent> components, boolean requireComplete) {
        Objects.requireNonNull(components);
        EnumMap<ComponentCategory,CatalogComponent> parts = new EnumMap<>(ComponentCategory.class);
        List<Finding> findings = new ArrayList<>();
        for (var component : components) {
            if (parts.put(component.category(), component) != null)
                findings.add(new Finding("duplicate", Status.INCOMPATIBLE,
                        "Несколько компонентов в категории «" + component.category().displayName() + "»"));
        }
        if (components.isEmpty()) unknown(findings, "empty", "Компоненты не выбраны");
        if (requireComplete) for (var category : values()) if (!parts.containsKey(category))
            unknown(findings, "missing_" + category, "Не выбрано: " + category.displayName());
        var cpu=parts.get(CPU); var board=parts.get(MOTHERBOARD); var gpu=parts.get(GPU);
        var ram=parts.get(RAM); var drive=parts.get(STORAGE); var psu=parts.get(PSU);
        var chassis=parts.get(CASE); var cooler=parts.get(COOLER);
        if (cpu != null && board != null) {
            compare(findings,"socket",cpu,SOCKET,board,SOCKET,String::equalsIgnoreCase,"Сокеты CPU и платы различаются");
            compare(findings,"cpu_support",cpu,MODEL_ID,board,SUPPORTED_CPU_MODELS,
                    (model,supported)->contains(supported,model),"Модель CPU не указана в проверенном списке поддержки платы/BIOS");
        }
        if (board != null && ram != null) {
            compare(findings,"memory_type",board,MEMORY_TYPE,ram,MEMORY_TYPE,String::equalsIgnoreCase,"Поколение RAM не поддерживается платой");
            compare(findings,"memory_capacity",ram,CAPACITY_GB,board,MAX_MEMORY_GB,(a,b)->a<=b,"Объём RAM превышает лимит платы");
            compare(findings,"memory_slots",ram,MODULE_COUNT,board,MEMORY_SLOTS,(a,b)->a<=b,"Недостаточно слотов для комплекта RAM");
        }
        if (board != null && chassis != null)
            compare(findings,"board_case",board,FORM_FACTOR,chassis,SUPPORTED_FORM_FACTORS,
                    (factor,supported)->contains(supported,factor),"Форм-фактор платы не поддерживается корпусом");
        if (drive != null && board != null)
            compare(findings,"storage_interface",drive,STORAGE_INTERFACE,board,SUPPORTED_STORAGE_INTERFACES,
                    (bus,supported)->contains(supported,bus),"Интерфейс накопителя не поддерживается платой");
        if (gpu != null && chassis != null)
            compare(findings,"gpu_length",gpu,LENGTH_MM,chassis,MAX_GPU_LENGTH_MM,(a,b)->a<=b,"Видеокарта не помещается в корпус");
        if (cooler != null) {
            var kind=cooler.specs().fact(COOLER_KIND);
            if (kind.isEmpty() || !kind.get().verified()) {
                unknown(findings,"cooler_kind","Тип охлаждения не подтверждён: воздушное или жидкостное. Без этого нельзя выбрать проверку высоты кулера или размещения радиатора СЖО");
            } else if (kind.get().value().equals("AIR")) {
                if (chassis != null)
                    compare(findings,"cooler_height",cooler,HEIGHT_MM,chassis,MAX_COOLER_HEIGHT_MM,(a,b)->a<=b,"Воздушный кулер не помещается в корпус по высоте");
            } else if (kind.get().value().equals("AIO")) {
                findings.add(radiatorFit.assess(chassis,cooler));
            }
        }
        if (cpu != null && cooler != null) {
            compare(findings,"cooler_socket",cpu,SOCKET,cooler,SUPPORTED_SOCKETS,
                    (socket,supported)->contains(supported,socket),"Крепление кулера не подходит к сокету CPU");
            compare(findings,"cooler_power",cpu,MAX_POWER_WATTS,cooler,COOLING_WATTS,(a,b)->a<=b,"Заявленной мощности охлаждения недостаточно");
        }
        if (psu != null && chassis != null)
            compare(findings,"psu_case",psu,PSU_FORM_FACTOR,chassis,SUPPORTED_PSU_FORM_FACTORS,
                    (factor,supported)->contains(supported,factor),"Форм-фактор БП не поддерживается корпусом");
        if (gpu != null && psu != null) {
            compare(findings,"gpu_min_psu",gpu,MIN_PSU_WATTS,psu,PSU_WATTS,(a,b)->a<=b,"Мощность БП ниже рекомендации производителя GPU",
                    "Мощность БП соответствует рекомендации производителя GPU для эталонной системы; точную модель карты и питание сборки нужно проверить отдельно");
            compare(findings,"gpu_connectors",gpu,REQUIRED_POWER_CONNECTORS,psu,POWER_CONNECTORS,
                    CompatibilityService::containsAll,"У БП нет необходимых разъёмов GPU");
        }
        if (cpu != null && board != null && psu != null)
            compare(findings,"board_connectors",board,REQUIRED_POWER_CONNECTORS,psu,POWER_CONNECTORS,
                    CompatibilityService::containsAll,"У БП нет необходимых разъёмов платы/CPU");
        if (cpu != null && gpu != null && psu != null) {
            var cp=cpu.specs().fact(MAX_POWER_WATTS); var gp=gpu.specs().fact(MAX_POWER_WATTS); var pp=psu.specs().fact(PSU_WATTS);
            if (cp.isEmpty() || gp.isEmpty() || pp.isEmpty()) unknown(findings,"power_budget","Нет данных о максимальном потреблении CPU/GPU или мощности БП");
            else {
                long required = (long)Math.ceil((cp.get().value().longValue()+gp.get().value().longValue()+100L)*1.25);
                if (cp.get().verified() && gp.get().verified() && pp.get().verified())
                    findings.add(new Finding("power_budget",pp.get().value()<required?Status.INCOMPATIBLE:Status.COMPATIBLE,
                            "Расчётный минимум БП: " + required + " Вт (CPU + GPU + 100 Вт, запас 25%)"));
                else unknown(findings,"power_budget","Расчёт питания требует проверенных характеристик максимальной мощности");
            }
        }
        Status status = findings.stream().anyMatch(f->f.status()==Status.INCOMPATIBLE)?Status.INCOMPATIBLE:
                findings.stream().anyMatch(f->f.status()==Status.INSUFFICIENT_DATA)?Status.INSUFFICIENT_DATA:Status.COMPATIBLE;
        return new Result(status, findings, List.of(
                "Проверка охватывает только перечисленные характеристики, а не все возможные ограничения оборудования.",
                "Поддержка CPU должна соответствовать установленной версии BIOS; длина GPU и высота воздушного кулера — фактической конфигурации корпуса. Для СЖО отдельно проверяются позиции и размеры радиатора с вентиляторами.",
                "Рекомендация производителя GPU по мощности БП относится к эталонной системе и не заменяет проверку конкретной карты, кабелей и нагрузки.",
                "Тепловые рейтинги разных производителей не стандартизованы; расчёт БП — консервативная оценка, а не измерение."));
    }
    private static <A,B> void compare(List<Finding> findings, String code, CatalogComponent a, Attribute<A> ak,
                                      CatalogComponent b, Attribute<B> bk, BiPredicate<A,B> condition, String conflict) {
        compare(findings,code,a,ak,b,bk,condition,conflict,"Проверка пройдена: "+checkLabel(code));
    }
    private static <A,B> void compare(List<Finding> findings, String code, CatalogComponent a, Attribute<A> ak,
                                      CatalogComponent b, Attribute<B> bk, BiPredicate<A,B> condition, String conflict, String passedMessage) {
        var af=a.specs().fact(ak); var bf=b.specs().fact(bk);
        if (af.isEmpty() || bf.isEmpty()) { unknown(findings,code,"Нет данных для проверки: " + checkLabel(code)); return; }
        if (!af.get().verified() || !bf.get().verified()) {
            unknown(findings,code,"Непроверенные данные для проверки: " + checkLabel(code)); return;
        }
        boolean passed = condition.test(af.get().value(),bf.get().value());
        findings.add(new Finding(code,passed?Status.COMPATIBLE:Status.INCOMPATIBLE,passed?passedMessage:conflict));
    }
    private static boolean contains(Set<String> values,String value) {
        return values.stream().anyMatch(v->v.equalsIgnoreCase(value));
    }
    private static String checkLabel(String code) {
        return switch(code) {
            case "socket" -> "сокет процессора и платы";
            case "cpu_support" -> "поддержка точной модели процессора платой";
            case "memory_type" -> "поколение оперативной памяти";
            case "memory_capacity" -> "объём памяти и лимит платы";
            case "memory_slots" -> "число модулей памяти и слотов платы";
            case "board_case" -> "формат платы и крепления корпуса";
            case "storage_interface" -> "интерфейс накопителя и разъёмы платы";
            case "gpu_length" -> "длина видеокарты и место в корпусе";
            case "cooler_height" -> "высота кулера и место в корпусе";
            case "cooler_socket" -> "крепление кулера для сокета процессора";
            case "cooler_power" -> "тепловая нагрузка процессора и возможности охлаждения";
            case "psu_case" -> "формат блока питания и корпуса";
            case "gpu_min_psu" -> "мощность блока питания по рекомендации производителя видеокарты";
            case "gpu_connectors" -> "разъёмы питания видеокарты";
            case "board_connectors" -> "разъёмы питания материнской платы";
            default -> "совместимость выбранных комплектующих";
        };
    }
    private static boolean containsAll(Set<String> required,Set<String> available) {
        return required.stream().allMatch(v->contains(available,v));
    }
    private static void unknown(List<Finding> findings,String code,String message) {
        findings.add(new Finding(code,Status.INSUFFICIENT_DATA,message));
    }
}
