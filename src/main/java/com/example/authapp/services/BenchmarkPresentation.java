package com.example.authapp.services;

import java.util.*;

/** Human-readable labels shared by the plain-text bot and the JavaFX cards. */
public final class BenchmarkPresentation {
    private BenchmarkPresentation() {}

    public static String modelName(String id, Map<String,String> names) {
        return names.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(id))
                .map(Map.Entry::getValue).findFirst().orElse(id);
    }

    public static String title(BenchmarkService.Context context) {
        return context.version().equalsIgnoreCase("Not reported")
                ? context.suite() : context.suite()+" "+context.version();
    }

    public static String resolution(BenchmarkService.Context context) {
        return conditionValue("resolution",context.conditions().getOrDefault("resolution","Не опубликовано"));
    }

    public static String workload(BenchmarkService.Context context) {
        return switch(context.workload().toLowerCase(Locale.ROOT)) {
            case "gpu gaming benchmark (4k/high/no upscaling)" -> "Игровой тест видеокарты · средняя частота кадров";
            case "cpu gaming benchmark (1080p/high)" -> "Игровой тест процессора · средняя частота кадров";
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
            case "chart_row_tag" -> "Метка серии в источнике";
            case "tested_samples" -> "Испытанные образцы видеокарт";
            case "resizable_bar" -> "Resizable BAR";
            case "hardware_scheduling" -> "Планирование GPU и ReBAR";
            case "power_plan" -> "План электропитания ОС";
            case "ray_tracing" -> "Режим графики";
            case "upscaling" -> "Масштабирование изображения";
            case "frame_generation" -> "Генерация кадров";
            default -> "Дополнительное условие источника";
        };
    }

    private static final Map<String,String> GAMING_NOTES=Map.ofEntries(
            Map.entry("AMD AM5 only; Intel and other platforms are not imported into this cohort", "AMD AM5; измерения Intel и других платформ не включены в эту группу"),
            Map.entry("Not reported in this article or chart; do not infer from a different review or a living test-bench page", "Не опубликована; сведения из другого обзора или обновляемой страницы стенда не подставляются"),
            Map.entry("DDR5-6000 Lexar, 30-36-36-68; capacity not reported in the chart", "DDR5-6000 Lexar, тайминги 30-36-36-68; объём не опубликован"),
            Map.entry("Arctic Liquid Freezer II 360; MX-4 paste; 100% fan speed; pump setting not reported", "Arctic Liquid Freezer II 360; паста MX-4; вентиляторы 100%; режим помпы не опубликован"),
            Map.entry("Imported rows have no OC or ECO suffix; actual per-model power and boost limits are not published", "В подписях нет OC/ECO; фактические лимиты питания и boost отдельных моделей не опубликованы"),
            Map.entry("Average FPS derived from frametime; run count and per-CPU uncertainty not published", "Средний FPS рассчитан из времени кадра; число запусков и неопределённость для каждого CPU не опубликованы"),
            Map.entry("Not reported; compare only this published October 2024 review cohort", "Не опубликована; сравнение ограничено этой серией измерений октября 2024"),
            Map.entry("Same AMD platform group and single published chart only; not a prediction for another game or a complete store build", "Одна платформа AMD и одна опубликованная диаграмма; это не прогноз для другой игры или готовой сборки")
    );
    private static final Map<String,String> GPU_GAMING_NOTES=Map.ofEntries(
            Map.entry("GN Intel Core i7-12700KF GPU test bench; this single Starfield chart only", "Стенд GPU на Intel Core i7-12700KF; только эта диаграмма Starfield"),
            Map.entry("Intel Core i7-12700KF, overclocked to 4.9 GHz P-cores / 3.9 GHz E-cores", "Intel Core i7-12700KF, разгон P-ядер до 4,9 ГГц/E-ядер до 3,9 ГГц"),
            Map.entry("DDR5-6000 G.Skill Trident Z with manually tightened timings; capacity and exact timings not reported", "DDR5-6000 G.Skill Trident Z с ручной настройкой таймингов; объём и точные тайминги не опубликованы"),
            Map.entry("Arctic Liquid Freezer II 360 at 100% fan speed; pump setting not reported", "Arctic Liquid Freezer II 360, вентиляторы 100%; режим помпы не опубликован"),
            Map.entry("EVGA 1600W T2 Supernova and Corsair AX1600i are listed; per-GPU assignment not reported", "Перечислены EVGA 1600W T2 Supernova и Corsair AX1600i; распределение по видеокартам не опубликовано"),
            Map.entry("Windows 11; edition and build not reported", "Windows 11; редакция и номер сборки не опубликованы"),
            Map.entry("Enabled (chart: ReBAR Always On; article: enabled)", "Включён по статье и подписи диаграммы"),
            Map.entry("Hardware-accelerated GPU scheduling enabled", "Аппаратное планирование GPU включено"),
            Map.entry("High Performance", "Высокая производительность"),
            Map.entry("All imported rows are labelled Stock. This label does not establish reference clocks, exact power limits or identical AIB settings", "Все импортированные строки подписаны Stock. Это не подтверждает эталонные частоты, точные лимиты питания или одинаковые настройки разных исполнений"),
            Map.entry("Published test samples: NVIDIA RTX 4070 FE 12GB Stock; NVIDIA RTX 4070 Super 12GB Stock (FE/AIB not specified); RTX 4090 24GB Stock (FE/AIB not specified); AMD RX 7800 XT 16GB Stock (AIB not specified); Sapphire RX 7900 XTX Nitro+ 24GB Stock. Nitro+ is a specific published AIB test sample, not an identification or performance guarantee for the store SKU. Generic store GPU names do not establish the shipped AIB. No FE model is inferred where the chart omits it.", "Опубликованные образцы: RTX 4070 FE 12GB; RTX 4070 SUPER 12GB и RTX 4090 24GB (FE/AIB не указаны); RX 7800 XT 16GB (исполнение не указано); Sapphire RX 7900 XTX Nitro+ 24GB. Nitro+ — образец лаборатории, а не установленный артикул магазина. Там, где FE не указан, он не предполагается"),
            Map.entry("AVG FPS measured as onPresent frames per second; pass count and per-GPU uncertainty not reported", "Средний FPS измерен по событиям onPresent; число проходов и неопределённость для каждой видеокарты не опубликованы"),
            Map.entry("Not reported; the article says this Starfield dataset was refreshed after major game updates; compare only this chart", "Не опубликована; по статье набор Starfield обновлён после крупных патчей. Сравнение ограничено этой диаграммой"),
            Map.entry("4K (chart title; exact pixel dimensions not stated)", "4K по подписи источника; точные размеры кадра не опубликованы"),
            Map.entry("Rasterized benchmark section; no ray-tracing result imported", "Растеризация; результаты трассировки лучей не импортированы"),
            Map.entry("No Upscaling (chart title)", "Без масштабирования по подписи диаграммы"),
            Map.entry("Not reported in this article or chart; no frame-generation setting is inferred", "Не опубликована; режим генерации кадров не предполагается"),
            Map.entry("Only five catalogue-matching rows with the same published (1/24) tag in this one Starfield 4K/High/No Upscaling chart. Not a global GPU ranking or FPS prediction for a complete store build", "Только пять соответствующих каталогу строк одной диаграммы Starfield 4K/High с меткой (1/24). Это не общий рейтинг GPU и не прогноз FPS готовой сборки")
    );
    private static String conditionValue(String key, String value) {
        String gamingNote=GAMING_NOTES.get(value);
        if(gamingNote!=null)return gamingNote;
        String gpuNote=GPU_GAMING_NOTES.get(value);
        if(gpuNote!=null)return gpuNote;
        if(key.equals("preset") && value.equals("High"))return "Высокие";
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
        return value.replace("Not reported in this article or chart; do not infer from other reviews or living methodology", "Не опубликовано; сведения из других обзоров не подставляются")
                .replace("Not reported in this article or chart", "Не опубликовано в этой статье или диаграмме")
                .replace("[10/24] (published chart tag; exact measurement date not reported)", "[10/24]; точная дата измерения не опубликована")
                .replace("(1/24) (published chart tag; exact measurement date not reported)", "(1/24); точная дата измерения не опубликована")
                .replaceAll("(?i)not[_ ]reported; compare only this review cohort", "Не опубликована; сравнение ограничено этим обзором")
                .replaceAll("(?i)not[_ ]reported", "не опубликовано")
                .replace("timings не опубликовано", "тайминги не опубликованы")
                .replace("patch revision не опубликовано", "ревизия обновления не опубликована")
                .replace("running at", "работает на");
    }
}
