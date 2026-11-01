package com.example.authapp.services;

import com.example.authapp.domain.*;
import com.example.authapp.models.Product;
import java.net.URI;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;
import static com.example.authapp.domain.ComponentSpecs.*;

/** Compares known facts and matching measurements; it never fills absent measurements from specifications. */
public final class ComparisonService {
    public enum Status { MODEL_NOT_FOUND, AMBIGUOUS_MODEL, DIFFERENT_CATEGORIES, NO_MEASUREMENTS, NO_COMMON_CONTEXT, COMPARABLE }
    public record Outcome(Status status, ChatBotService.Reply reply) {}
    private final LocalKnowledgeRepository.Snapshot knowledge;
    private final Map<Integer,ChatBotService.ProductInfo> products;
    private final Map<String,String> modelNames;
    private static final Locale RU=Locale.forLanguageTag("ru-RU");
    private static final String UNKNOWN="Не подтверждено";

    public ComparisonService(LocalKnowledgeRepository.Snapshot knowledge, Collection<Product> products) {
        this.knowledge=Objects.requireNonNull(knowledge);
        Map<Integer,ChatBotService.ProductInfo> productMap=new HashMap<>();
        for(var p:products) productMap.put(p.getId(),ChatBotService.ProductInfo.from(p));
        this.products=Map.copyOf(productMap);
        Map<String,String> names=new LinkedHashMap<>();
        for(var component:knowledge.components()) names.put(modelId(component), component.name());
        this.modelNames=Map.copyOf(names);
    }

    public Outcome compare(CatalogComponent left,CatalogComponent right,boolean specsOnly,String purpose) {
        if(left==null || right==null) return new Outcome(Status.MODEL_NOT_FOUND,new ChatBotService.Reply(
                "Не удалось найти обе модели в каталоге. Назовите модель, которую нужно уточнить."));
        if(left.category()!=right.category()) return new Outcome(Status.DIFFERENT_CATEGORIES,new ChatBotService.Reply(
                "«"+left.name()+"» — "+left.category().displayName()+", а «"+right.name()+"» — "+right.category().displayName()+". "
                        +"Для сравнения скорости выберите две модели одной категории. Для проверки совместимости используйте конфигуратор."));
        var leftResults=knowledge.benchmarks().resultsFor(modelId(left));
        var rightResults=knowledge.benchmarks().resultsFor(modelId(right));
        var comparisons=knowledge.benchmarks().compareModels(modelId(left),modelId(right));
        Status status=leftResults.isEmpty()||rightResults.isEmpty()?Status.NO_MEASUREMENTS
                :comparisons.isEmpty()?Status.NO_COMMON_CONTEXT:Status.COMPARABLE;
        if(specsOnly || status!=Status.COMPARABLE) return specifications(left,right,status,specsOnly,purpose,leftResults,rightResults);
        boolean gamingRequested=gamingPurpose(purpose);
        if(nonGamingPurpose(purpose) && !gamingRequested) {
            var work=comparisons.stream().filter(c->!isGaming(c.left().context())).toList();
            if(work.isEmpty())return unavailableWorkMeasurements(left,right,status,purpose,leftResults,rightResults);
            comparisons=work;
        }
        var gaming=comparisons.stream().filter(c->isGaming(c.left().context())).toList();
        String details=taskDetails(purpose);
        if(gamingRequested && !gaming.isEmpty()) {
            if(!details.isBlank()) {
                var scenario=gamingScenario(gaming,details);
                if(scenario.isEmpty())return unavailableGamingScenario(left,right,status,gaming);
                comparisons=scenario;
            } else comparisons=gaming;
        } else if(gamingRequested && !details.isBlank()) {
            return unavailableGamingScenario(left,right,status,gaming);
        }
        boolean platformComparison=comparisons.stream().anyMatch(c->"PUBLISHED_PLATFORM_COMPARISON".equals(c.left().context().conditions().get("comparison_kind")));
        boolean platformConflict=comparisons.stream().anyMatch(c->"SOURCE_PLATFORM_CONFLICT".equals(c.left().context().conditions().get("comparison_kind")));
        String lead="Сравниваю «"+left.name()+"» и «"+right.name()+"»: нашёл общие тесты. Вывод относится только к их условиям."
                +(platformComparison?" Это опубликованное сравнение двух тестовых платформ.":"")
                +(platformConflict?" Описание стенда в статье не совпадает с подписью диаграммы; фактическая конфигурация не установлена.":"");
        StringJoiner text=new StringJoiner("\n\n");text.add(lead);
        comparisons.stream().limit(5).forEach(c -> {
            var context=c.left().context();
            text.add(BenchmarkPresentation.title(context)+" · "+BenchmarkPresentation.workload(context)
                    +(BenchmarkPresentation.scopeNote(context).isEmpty()?"":"\n"+BenchmarkPresentation.scopeNote(context))
                    +"\n"+left.name()+": "+c.left().value()+"; "+right.name()+": "+c.right().value()+" "+BenchmarkPresentation.unit(context.unit())
                    +"\n"+BenchmarkPresentation.winner(c,modelNames)
                    +"\n"+BenchmarkPresentation.difference(c)+". "+c.explanation()
                    +"\nУсловия:\n"+String.join("\n",BenchmarkPresentation.conditionLines(context))
                    +"\nИсточники: "+c.left().source()+" ("+c.left().dateDescription()+"); "+c.right().source()+" ("+c.right().dateDescription()+")");
        });
        String task=gamingRequested && !gaming.isEmpty()
                ? "Показан средний FPS в конкретной игре на опубликованном стенде. Это не прогноз для других игр или вашей сборки."
                    +(details.isBlank()?" В какие игры и с какой видеокартой планируете играть?":" Уточнение не подтверждает эквивалентность вашего ПК опубликованному стенду.")
                : gamingRequested
                    ? "Для этой пары нет общих проверенных игровых измерений. Показываю имеющиеся прочие тесты отдельно. "+purposeNote(purpose)
                    : purposeNote(purpose);
        if(!task.isEmpty()) text.add(task);
        List<String> suggestions=List.of("Сравнить характеристики", "Для игр", "Для работы");
        return new Outcome(status,new ChatBotService.Reply(text.toString(),lead+(task.isEmpty()?"":" "+task),List.of(),
                List.of(new ChatBotService.BenchmarksContent(comparisons.stream().limit(5).toList(),modelNames)),null,suggestions));
    }

    private static boolean isGaming(BenchmarkService.Context context) {
        return context.unit().equalsIgnoreCase("FPS") && context.workload().toLowerCase(Locale.ROOT).contains("gaming");
    }
    private static boolean gamingPurpose(String purpose) {
        String first=Objects.toString(purpose,"").split("\nУточнение: ",2)[0].toLowerCase(RU);
        return first.contains("игр") || first.contains("gaming");
    }
    private static boolean nonGamingPurpose(String purpose) {
        String first=Objects.toString(purpose,"").split("\nУточнение: ",2)[0].toLowerCase(RU).replace('ё','е');
        return first.contains("работ") || first.contains("work") || first.contains("рендер")
                || first.contains("render") || first.contains("монтаж") || first.contains("офис")
                || first.contains("учеб") || first.contains("office") || first.contains("study");
    }
    private Outcome unavailableWorkMeasurements(CatalogComponent left,CatalogComponent right,Status status,String purpose,
                                                List<BenchmarkService.Result> leftResults,List<BenchmarkService.Result> rightResults) {
        var facts=specifications(left,right,status,true,purpose,leftResults,rightResults).reply();
        String lead="Для этой пары нет общих проверенных измерений рабочих задач. Показываю проверенные характеристики; игровые результаты не определяют скорость работы.";
        return new Outcome(status,new ChatBotService.Reply(lead+"\n"+facts.text(),lead+" "+purposeNote(purpose),
                facts.build(),facts.content(),facts.request(),
                List.of("Сравнить характеристики","Для игр","Показать модели с общим тестом")));
    }
    private static String taskDetails(String purpose) {
        String[] split=Objects.toString(purpose,"").split("\nУточнение: ",2);
        return split.length==2?split[1].strip():"";
    }
    /** Deliberately narrow: a named supported game and an optional chart resolution, no inferred PC settings. */
    private static final Pattern BLACK_MYTH=Pattern.compile("(?U)\\b(?:black\\s+myth(?:\\s+wukong)?|бл[эе]к\\s+миф(?:\\s+вуконг)?|вуконг)\\b");
    private static final Pattern F1_GAME=Pattern.compile("\\bf\\s*1\\s*(24|25)\\b");
    private static List<BenchmarkService.Comparison> gamingScenario(List<BenchmarkService.Comparison> gaming,String raw) {
        String details=ModelResolver.normalize(raw).replaceAll("(?iu)(?<=[\\p{L}])(?=(?:720p|1080p|1440p|2160p|2k|4k)(?![a-z0-9]))"," ");
        Set<String> games=new LinkedHashSet<>();
        if(Pattern.compile("\\bstarfield\\b").matcher(details).find())games.add("Starfield");
        var f1=F1_GAME.matcher(details);while(f1.find())games.add("F1/"+f1.group(1));
        if(BLACK_MYTH.matcher(details).find())games.add("Black Myth: Wukong");
        if(games.size()!=1)return List.of();
        String selected=games.iterator().next(),game=selected.startsWith("F1/")?"F1":selected;
        String version=game.equals("F1")?selected.substring(3):"";
        var resolution=Pattern.compile("(?<![a-z0-9])(?:720p|1080p|1440p|2160p|2k|4k)(?![a-z0-9])").matcher(details);
        Set<String> requested=new LinkedHashSet<>();while(resolution.find())requested.add(resolution.group());
        if(requested.size()>1)return List.of();
        String remaining=BLACK_MYTH.matcher(details).replaceAll("").replaceAll("\\bstarfield\\b|\\bf\\s*1\\s*(?:24|25)\\b","")
                .replaceAll("(?<![a-z0-9])(?:720p|1080p|1440p|2160p|2k|4k)(?![a-z0-9])","")
                .replaceAll("(?U)\\b(?:а|в|на|для|игра|игры|играю|при|разрешение|разрешении)\\b","").strip();
        if(!remaining.isBlank())return List.of();
        return gaming.stream().filter(c->c.left().context().suite().equalsIgnoreCase(game))
                .filter(c->!game.equals("F1") || c.left().context().version().equals(version))
                .filter(c->requested.isEmpty() || Pattern.compile("(?<![a-z0-9])"+Pattern.quote(requested.iterator().next())+"(?![a-z0-9])")
                        .matcher(c.left().context().conditions().getOrDefault("resolution","").toLowerCase(Locale.ROOT)).find()).toList();
    }
    private Outcome unavailableGamingScenario(CatalogComponent left,CatalogComponent right,Status status,List<BenchmarkService.Comparison> gaming) {
        String lead="Продолжаем сравнивать «"+left.name()+"» и «"+right.name()+"». Для указанного сценария нет подтверждённого общего игрового результата; FPS и процентную разницу для него не вывожу.";
        String available=gaming.isEmpty()?"Для этой пары общих игровых измерений пока нет.":
                "Для пары доступны другие опубликованные условия: "+String.join("; ",gaming.stream().map(c->BenchmarkPresentation.title(c.left().context())+" · "+BenchmarkPresentation.resolution(c.left().context())).distinct().toList())+". Они не подтверждают результат для названного сценария.";
        List<String> actions=new ArrayList<>();
        for(var comparison:gaming) {
            var context=comparison.left().context();
            var resolution=Pattern.compile("(?i)^(1080p|1440p|4k)\\b").matcher(context.conditions().getOrDefault("resolution",""));
            if(resolution.find()) {
                String scenario="в "+BenchmarkPresentation.title(context)+" "+resolution.group();
                String action="Для игр "+scenario;
                if(!gamingScenario(gaming,scenario).isEmpty() && !actions.contains(action) && actions.size()<2)actions.add(action);
            }
        }
        actions.add("Сравнить характеристики");actions.add("Показать модели с общим тестом");
        return new Outcome(status,new ChatBotService.Reply(lead+"\n"+available+"\nПоддержано только точное название доступной игры и разрешение диаграммы; дополнительные параметры ПК требуют отдельных измерений.",lead+" "+available,List.of(),List.of(),null,actions));
    }

    private Outcome specifications(CatalogComponent left,CatalogComponent right,Status status,boolean specsOnly,String purpose,
                                    List<BenchmarkService.Result> leftResults,List<BenchmarkService.Result> rightResults) {
        String lead;
        if(specsOnly) lead="Сравниваю проверенные характеристики «"+left.name()+"» и «"+right.name()+"».";
        else if(status==Status.NO_MEASUREMENTS) {
            List<String> absent=new ArrayList<>();if(leftResults.isEmpty())absent.add(left.name());if(rightResults.isEmpty())absent.add(right.name());
            lead="Распознал «"+left.name()+"» и «"+right.name()+"». В локальном наборе нет проверенных измерений для "+String.join(" и ",absent)
                    +". Пока сравню доступные подтверждённые характеристики.";
        } else lead="Для «"+left.name()+"» и «"+right.name()+"» есть измерения, но нет общего теста с одинаковыми условиями: "
                +contextDifferences(leftResults,rightResults)+". Процент скорости здесь был бы некорректен; сравню проверенные характеристики.";
        List<ChatBotService.SpecRow> rows=new ArrayList<>();List<ChatBotService.FactSource> sources=new ArrayList<>();
        for(var attribute:attributes(left.category())) {
            String title=attribute.equals(CAPACITY_GB)?switch(left.category()) {
                case GPU -> "Объём видеопамяти";
                case RAM -> "Общий объём комплекта памяти";
                case STORAGE -> "Ёмкость накопителя";
                default -> label(attribute);
            }:label(attribute);
            rows.add(new ChatBotService.SpecRow(title,value(left,attribute),value(right,attribute)));
            addSource(sources,left,attribute);addSource(sources,right,attribute);
        }
        List<String> limitations=new ArrayList<>(List.of(
                "Показаны только характеристики со статусом VERIFIED; «Не подтверждено» означает отсутствие проверенного значения, а не отсутствие функции.",
                "Из характеристик и цены нельзя вывести общий рейтинг скорости или игровой FPS.",
                "Цена и наличие получены из снимка каталога и могут измениться; перед покупкой перепроверьте их."));
        if(left.category()==ComponentCategory.GPU) limitations.add("Рекомендация по мощности БП относится к эталонной системе; точное исполнение видеокарты магазина не установлено.");
        if(left.category()==ComponentCategory.CPU) limitations.add("Электрическая максимальная мощность и маркетинговый TDP не взаимозаменяемы; совпадение сокета не подтверждает поддержку BIOS.");
        limitations.addAll(knowledge.warnings());
        var card=new ChatBotService.SpecificationsContent(product(left),product(right),rows,sources.stream().distinct().toList(),limitations);
        StringJoiner text=new StringJoiner("\n");text.add(lead);
        for(var row:rows) text.add(row.label()+": "+left.name()+" — "+row.left()+"; "+right.name()+" — "+row.right());
        text.add("Цена и наличие из каталога: "+catalogLine(left)+"; "+catalogLine(right));
        for(var source:card.sources()) text.add("Источник: "+source.title()+" — "+source.uri());
        for(String limitation:limitations) text.add(limitation);
        String task=purposeNote(purpose);
        if(task.isEmpty()) task="Для каких задач выбираете?";
        text.add(task);
        boolean measurementsForPurpose=status==Status.COMPARABLE && (!nonGamingPurpose(purpose)
                || knowledge.benchmarks().compareModels(modelId(left),modelId(right)).stream()
                    .anyMatch(c->!isGaming(c.left().context())));
        List<String> actions=specsOnly && measurementsForPurpose
                ?List.of("Сравнить по бенчмаркам","Для игр","Для работы")
                :List.of("Для игр","Для работы","Показать модели с общим тестом");
        return new Outcome(status,new ChatBotService.Reply(text.toString(),lead+" "+task,List.of(),List.of(card),null,actions));
    }

    public ChatBotService.Reply help() {
        var examples=comparablePairSuggestions(null);
        String lead="Назовите две модели своими словами. Я найду их в каталоге, сравню имеющиеся тесты и характеристики. "
                +"Если название неоднозначно, предложу варианты; если измерений нет, объясню, чего не хватает.";
        String text=lead+(examples.isEmpty()?" Сейчас в каталоге нет пары с общим проверенным тестом.":"\nПары с общими измерениями сейчас:\n"+String.join("\n",examples));
        return new ChatBotService.Reply(text,lead,List.of(),List.of(),null,examples);
    }

    public List<String> comparablePairSuggestions(ComponentCategory category) {
        var catalog=knowledge.components().stream().filter(c -> category==null || c.category()==category)
                .sorted(Comparator.comparingInt(CatalogComponent::productId)).toList();
        EnumMap<ComponentCategory,List<String>> groups=new EnumMap<>(ComponentCategory.class);
        for(int i=0;i<catalog.size();i++) for(int j=i+1;j<catalog.size();j++) {
            var a=catalog.get(i);var b=catalog.get(j);
            if(a.category()!=b.category() || modelId(a).equalsIgnoreCase(modelId(b)))continue;
            if(!knowledge.benchmarks().compareModels(modelId(a),modelId(b)).isEmpty())
                groups.computeIfAbsent(a.category(),c -> new ArrayList<>()).add("Сравни "+a.name()+" и "+b.name());
        }
        List<String> result=new ArrayList<>();
        for(int index=0;result.size()<3;index++) {
            boolean added=false;
            for(var entries:groups.values()) if(index<entries.size() && result.size()<3) { result.add(entries.get(index));added=true; }
            if(!added)break;
        }
        return List.copyOf(result);
    }

    private String contextDifferences(List<BenchmarkService.Result> left,List<BenchmarkService.Result> right) {
        List<String> closest=null;
        for(var a:left)for(var b:right) {
            LinkedHashSet<String> differences=new LinkedHashSet<>();
            var x=a.context();var y=b.context();
            if(!x.suite().equals(y.suite()))differences.add("разные программы тестирования");
            if(!x.version().equals(y.version()))differences.add("разные версии теста");
            if(!x.workload().equals(y.workload()) || !x.metric().equals(y.metric()) || !x.unit().equals(y.unit()) || x.direction()!=y.direction())differences.add("разные режимы или метрики");
            if(!x.conditions().equals(y.conditions()))differences.add("разные стенды, настройки или серии измерений");
            if(closest==null || differences.size()<closest.size())closest=List.copyOf(differences);
        }
        return closest==null || closest.isEmpty()?"условия не совпадают":String.join(", ",closest);
    }

    private static String purposeNote(String purpose) {
        if(Objects.toString(purpose,"").contains("\nУточнение: "))return "Задача уточнена: "+purpose.replace("\nУточнение: ","; ")+". Выводы ограничены имеющимися тестами; скорость конкретной игры или другой программы по ним не установлена.";
        String task=Objects.toString(purpose,"").toLowerCase(RU);
        if(task.contains("игр") || task.contains("gaming")) return "Тесты рендеринга не определяют выбор для игр. В какие игры и с какой видеокартой планируете играть?";
        if(nonGamingPurpose(purpose)) return "Результат зависит от рабочей нагрузки. С какими программами и задачами будете работать?";
        if(task.contains("офис") || task.contains("office")) return "Для офисного выбора важны задачи и остальная сборка. Какие программы планируете использовать?";
        return "";
    }

    private static String modelId(CatalogComponent component) {
        return component.specs().fact(MODEL_ID).filter(Fact::verified).map(Fact::value).orElse(component.name());
    }
    private ChatBotService.ProductInfo product(CatalogComponent component) {
        return products.getOrDefault(component.productId(),new ChatBotService.ProductInfo(component.productId(),component.name(),"",
                component.price(),component.stock(),"",component.category().displayName(),""));
    }
    private static String catalogLine(CatalogComponent component) {
        return component.name()+" — "+NumberFormat.getNumberInstance(RU).format(component.price())+" ₽, "+(component.stock()>0?"в наличии "+component.stock()+" шт.":"нет в наличии");
    }
    private static List<Attribute<?>> attributes(ComponentCategory category) {
        return switch(category) {
            case CPU -> List.of(SOCKET,MEMORY_TYPE,MAX_POWER_WATTS);
            case GPU -> List.of(CAPACITY_GB,MIN_PSU_WATTS,MAX_POWER_WATTS,LENGTH_MM,REQUIRED_POWER_CONNECTORS);
            case MOTHERBOARD -> List.of(SOCKET,MEMORY_TYPE,FORM_FACTOR,MEMORY_SLOTS,MAX_MEMORY_GB,SUPPORTED_STORAGE_INTERFACES);
            case RAM -> List.of(MEMORY_TYPE,CAPACITY_GB,MODULE_COUNT);
            case STORAGE -> List.of(STORAGE_INTERFACE,CAPACITY_GB,FORM_FACTOR);
            case PSU -> List.of(PSU_WATTS,PSU_FORM_FACTOR,POWER_CONNECTORS);
            case CASE -> List.of(SUPPORTED_FORM_FACTORS,SUPPORTED_PSU_FORM_FACTORS,MAX_GPU_LENGTH_MM,MAX_COOLER_HEIGHT_MM,RADIATOR_MOUNTS);
            case COOLER -> List.of(COOLER_KIND,RADIATOR_NOMINAL_MM,RADIATOR_LENGTH_MM,RADIATOR_WIDTH_MM,
                    RADIATOR_THICKNESS_MM,RADIATOR_FAN_THICKNESS_MM,HEIGHT_MM,SUPPORTED_SOCKETS,COOLING_WATTS);
        };
    }
    private static String label(Attribute<?> attribute) {
        return switch(attribute.name()) {
            case "socket" -> "Сокет";
            case "memory_type" -> "Поколение оперативной памяти";
            case "max_power_watts" -> "Максимальная электрическая мощность";
            case "capacity_gb" -> "Объём памяти / накопителя";
            case "min_psu_watts" -> "Рекомендация производителя по БП";
            case "length_mm" -> "Длина";
            case "required_power_connectors" -> "Необходимые разъёмы питания";
            case "form_factor" -> "Форм-фактор";
            case "memory_slots" -> "Слоты оперативной памяти";
            case "max_memory_gb" -> "Максимальный объём RAM платы";
            case "supported_storage_interfaces" -> "Поддерживаемые интерфейсы накопителей";
            case "module_count" -> "Модулей в комплекте";
            case "storage_interface" -> "Интерфейс накопителя";
            case "psu_watts" -> "Мощность блока питания";
            case "psu_form_factor" -> "Форм-фактор блока питания";
            case "power_connectors" -> "Разъёмы питания";
            case "supported_form_factors" -> "Форматы материнских плат";
            case "supported_psu_form_factors" -> "Форматы блоков питания";
            case "max_gpu_length_mm" -> "Максимальная длина видеокарты";
            case "max_cooler_height_mm" -> "Максимальная высота кулера";
            case "cooler_kind" -> "Тип охлаждения";
            case "radiator_nominal_mm" -> "Номинальный класс радиатора";
            case "radiator_length_mm" -> "Фактическая длина радиатора";
            case "radiator_width_mm" -> "Фактическая ширина радиатора";
            case "radiator_thickness_mm" -> "Толщина радиатора без вентиляторов";
            case "radiator_fan_thickness_mm" -> "Толщина штатного слоя вентиляторов";
            case "radiator_mounts" -> "Крепления радиаторов";
            case "height_mm" -> "Высота воздушного кулера";
            case "supported_sockets" -> "Крепления для сокетов";
            case "cooling_watts" -> "Подтверждённая мощность охлаждения";
            default -> "Характеристика";
        };
    }
    private static String value(CatalogComponent part,Attribute<?> attribute) {
        if(part.category()==ComponentCategory.COOLER) {
            String kind=part.specs().fact(COOLER_KIND).filter(Fact::verified).map(Fact::value).orElse("");
            if(attribute.equals(HEIGHT_MM) && kind.equals("AIO"))return "Не применяется к СЖО";
            if((attribute.equals(RADIATOR_NOMINAL_MM)||attribute.equals(RADIATOR_LENGTH_MM)||attribute.equals(RADIATOR_WIDTH_MM)
                    ||attribute.equals(RADIATOR_THICKNESS_MM)||attribute.equals(RADIATOR_FAN_THICKNESS_MM)) && kind.equals("AIR"))return "Не применяется";
        }
        var fact=part.specs().fact(attribute).filter(Fact::verified);
        if(fact.isEmpty())return UNKNOWN;
        Object value=fact.get().value();
        if(value instanceof com.example.authapp.domain.RadiatorMountMatrix matrix)return matrix.summary();
        String text=value instanceof Set<?> values ? values.stream().map(Object::toString).sorted().reduce((a,b)->a+", "+b).orElse("Нет") : value.toString();
        String name=attribute.name();
        if(attribute.equals(COOLER_KIND))return switch(text) {case "AIR" -> "Воздушное";case "AIO" -> "Жидкостное (AIO)";default -> UNKNOWN;};
        if(name.endsWith("_watts"))return text+" Вт";
        if(name.endsWith("_mm"))return text+" мм";
        if(name.endsWith("_gb"))return text+" ГБ";
        return text;
    }
    private static void addSource(List<ChatBotService.FactSource> sources,CatalogComponent component,Attribute<?> attribute) {
        component.specs().fact(attribute).filter(Fact::verified).ifPresent(fact -> {
            try {
                URI uri=URI.create(fact.source());
                if(Set.of("http","https").contains(Objects.toString(uri.getScheme(),"").toLowerCase(Locale.ROOT)) && uri.getHost()!=null)
                    sources.add(new ChatBotService.FactSource(component.name()+" — характеристики",uri));
            } catch(IllegalArgumentException ignored) { /* Non-web local provenance is not an external link. */ }
        });
    }
}
