package com.example.authapp.services;

import java.util.*;

/** Human-readable labels shared by the plain-text bot and the JavaFX cards. */
public final class BenchmarkPresentation {
    private BenchmarkPresentation() {}

    public static String modelName(String id, Map<String,String> names) {
        return names.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(id))
                .map(Map.Entry::getValue).findFirst().orElse(id);
    }

    public static String workload(BenchmarkService.Context context) {
        return switch(context.workload().toLowerCase(Locale.ROOT)) {
            case "cpu multi core" -> "Рендеринг на процессоре · все ядра";
            case "cpu single core" -> "Рендеринг на процессоре · одно ядро";
            case "gpu rendering" -> "Рендеринг на видеокарте";
            case "gpu rendering (rtx mode)" -> "Рендеринг на видеокарте · режим RTX";
            case "gpu rendering (optix)" -> "Рендеринг на видеокарте · OptiX";
            default -> context.workload();
        };
    }

    public static String unit(String unit) {
        return switch(unit.toLowerCase(Locale.ROOT)) {
            case "points", "score", "scores" -> "баллов";
            case "fps" -> "кадр/с";
            case "seconds", "second", "s" -> "с";
            default -> unit;
        };
    }

    public static String difference(BenchmarkService.Comparison comparison) {
        return "Разница значений: " + comparison.differencePercent().toPlainString()
                + " % (левое − правое) / правое × 100";
    }

    public static String winner(BenchmarkService.Comparison comparison, Map<String,String> names) {
        return comparison.betterModelId().isEmpty() ? "Значения равны."
                : "Лучший результат в этом тесте: " + modelName(comparison.betterModelId(), names);
    }

    public static List<String> conditionLines(BenchmarkService.Context context) {
        List<String> lines = new ArrayList<>();
        context.conditions().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String key=entry.getKey();
            if(Set.of("review_cohort", "reviewCohort", "cohort").contains(key)) {
                lines.add("Группа измерений: результаты сопоставляются только внутри этой серии");
            } else {
                lines.add(conditionLabel(key) + ": " + conditionValue(key, entry.getValue()));
            }
        });
        lines.add("Направление оценки: " + (context.direction()==BenchmarkService.Direction.HIGHER_IS_BETTER
                ? "больше — лучше" : "меньше — лучше"));
        return List.copyOf(lines);
    }

    private static String conditionLabel(String key) {
        return switch(key) {
            case "publisher" -> "Лаборатория";
            case "sourceReview" -> "Публикация";
            case "platform" -> "Платформа";
            case "motherboard" -> "Материнская плата стенда";
            case "bios" -> "Версия BIOS";
            case "ram" -> "Оперативная память стенда";
            case "gpu" -> "Видеокарта стенда";
            case "cpu" -> "Процессор стенда";
            case "gpu_driver", "gpuDriverVersion", "driver" -> "Драйвер видеокарты";
            case "cooler", "cpuCooler" -> "Охлаждение процессора";
            case "psu", "powerSupply" -> "Блок питания стенда";
            case "storage" -> "Накопитель стенда";
            case "os" -> "Операционная система";
            case "power_policy" -> "Настройки питания";
            case "aggregation" -> "Методика усреднения";
            case "benchmark_patch", "minorPatchVersion" -> "Точная версия обновления теста";
            case "comparison_scope", "comparisonScope" -> "Область применимости";
            case "gpuSamplePolicy" -> "Испытанные видеокарты";
            case "gpuClockPowerProfile" -> "Частоты и лимиты видеокарты";
            case "renderSettings" -> "Настройки рендеринга";
            case "renderBackend" -> "Движок рендеринга";
            case "resolution" -> "Разрешение";
            case "preset" -> "Настройки графики";
            case "testSystem", "test_system" -> "Испытательный стенд";
            case "scene" -> "Тестовая сцена";
            default -> "Дополнительное условие источника";
        };
    }

    private static String conditionValue(String key, String value) {
        return switch(key) {
            case "power_policy" -> value.equals("Puget standard power profile; outside-spec enhancements disabled; exact per-model limits not reported")
                    ? "Стандартный профиль Puget; выход за спецификации отключён. Точные лимиты отдельных моделей не опубликованы" : unknowns(value);
            case "aggregation" -> value.equals("Mean of multiple runs; each run at least 5 minutes; run count not reported")
                    ? "Среднее нескольких запусков длительностью от 5 минут; число запусков не опубликовано" : unknowns(value);
            case "comparison_scope" -> value.equals("Same platform and review cohort only; CPU rendering score, not gaming FPS")
                    ? "Одна платформа и один обзор; баллы рендеринга не являются игровым FPS" : unknowns(value);
            case "comparisonScope" -> value.equals("Only this review cohort and published score; no FPS or exact store-AIB guarantee")
                    ? "Только результаты этого обзора; игровой FPS и скорость конкретной карты магазина не установлены" : unknowns(value);
            case "gpuSamplePolicy" -> value.equals("NVIDIA Founders Edition; AMD first-party version; exact part number not_reported; GPU model varies")
                    ? "NVIDIA Founders Edition и собственная версия AMD; точный артикул AMD не опубликован. Модель видеокарты меняется между измерениями" : unknowns(value);
            case "renderSettings" -> value.equals("Publisher standard benchmark; additional settings not_reported")
                    ? "Стандартный тест лаборатории; дополнительные настройки не опубликованы" : unknowns(value);
            case "renderBackend" -> value.equals("Redshift (chart heading); low-level backend not_reported")
                    ? "Redshift по подписи графика; низкоуровневый режим не опубликован"
                    : value.equals("RTX Mode (chart label)")?"Режим RTX по подписи графика":unknowns(value);
            default -> unknowns(value);
        };
    }

    private static String unknowns(String value) {
        return value.replaceAll("(?i)not[_ ]reported; compare only this review cohort", "Не опубликована; сравнение ограничено этим обзором")
                .replaceAll("(?i)not[_ ]reported", "не опубликовано")
                .replace("timings не опубликовано", "тайминги не опубликованы")
                .replace("patch revision не опубликовано", "ревизия обновления не опубликована")
                .replace("running at", "работает на");
    }
}
