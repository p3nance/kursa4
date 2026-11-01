package com.example.authapp.services;

import com.example.authapp.domain.CatalogComponent;
import com.example.authapp.domain.ComponentCategory;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Explicit ownership statements, catalog-bound choices and atomic batches. No purchases or inferred ownership. */
final class OwnedBuildConversation {
    record Outcome(boolean handled,String remaining,ChatBotService.Reply response) {
        static Outcome none(String text){return new Outcome(false,text,null);}
    }
    private enum Mode { NONE, MODEL, CHOICE, CONFIRM, CONFLICT }
    private static final int FLAGS=Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE|Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern PREFIX=p("\\b(?:у меня (?:уже )?есть|у меня имеется|из имеющегося)\\s*:?\\s*");
    private static final Pattern ABSENCE=p("\\bу меня (?:больше |уже |пока )?нет\\s*:?\\s*");
    private static final Pattern NEGATIVE_CONTEXT=p("\\b(?:не (?:считай|считайте|думай|предполагай|надо считать|нужно считать|следует считать)|неверно|неправда|ошибочно)\\b");
    private static final Pattern HYPOTHETICAL=p("\\b(?:если|допустим|представим|предположим)\\b");
    private static final Pattern SUFFIX=p("^(.+?)\\s+у меня (?:уже )?есть\\b");
    private static final Pattern TAIL=p("(?:[,;]\\s*|\\s+)(?=(?:собери|собрать|соберите|покажи|показать|дай|подбери|подобрать|подберите|дополни|остальное|остальные|нужно|нужен|нужна|нужны|хочу|бюджет|минимум|для (?:игр|офиса|учебы|работы|монтажа|рендера))\\b|до\\s+[0-9][0-9 ]*(?:[.,][0-9]+)?\\s*(?:тыс|[кk](?![\\p{L}])|руб|₽))");
    private static final Pattern CLEAR_ALL=p("^(?:сбрось|сбросить|очисти|очистить|забудь) (?:все )?(?:имеющиеся|мои) (?:детали|комплектующие)[.!?]*$");
    private static final Pattern CLEAR_ONE=p("^(?:убери|забудь|не учитывай) (?:имеющ\\p{L}*\\s+)?(.+?) из имеющихся[.!?]*$");
    private final List<CatalogComponent> catalog;
    private final ModelResolver resolver;
    private final EnumMap<ComponentCategory,CatalogComponent> owned=new EnumMap<>(ComponentCategory.class);
    private final EnumMap<ComponentCategory,CatalogComponent> staged=new EnumMap<>(ComponentCategory.class);
    private final List<CatalogComponent> invalidated=new ArrayList<>();
    private List<String> queries=List.of();
    private List<CatalogComponent> options=List.of();
    private int index;
    private Mode mode=Mode.NONE;
    private ComponentCategory expected;
    private boolean confirmEach;
    private String inputProblem="";

    OwnedBuildConversation(Collection<CatalogComponent> catalog) {
        this.catalog=List.copyOf(catalog);resolver=new ModelResolver(catalog);
    }
    private OwnedBuildConversation(OwnedBuildConversation source,Collection<CatalogComponent> catalog) {
        this(catalog);copyState(source);rebind();
    }
    OwnedBuildConversation copy(){return new OwnedBuildConversation(this,catalog);}
    OwnedBuildConversation refreshed(Collection<CatalogComponent> updated){return new OwnedBuildConversation(this,updated);}
    void commit(OwnedBuildConversation draft){copyState(draft);rebind();}
    private void copyState(OwnedBuildConversation other) {
        if(other==this)return;
        owned.clear();owned.putAll(other.owned);staged.clear();staged.putAll(other.staged);
        invalidated.clear();invalidated.addAll(other.invalidated);queries=other.queries;options=other.options;
        index=other.index;mode=other.mode;expected=other.expected;confirmEach=other.confirmEach;inputProblem=other.inputProblem;
    }
    Map<ComponentCategory,CatalogComponent> components(){return Map.copyOf(owned);}
    void forget(ComponentCategory category){owned.remove(category);}
    boolean pending(){return mode!=Mode.NONE;}
    void clear(){owned.clear();invalidated.clear();cancelPending();}
    void cancelPending(){staged.clear();queries=List.of();options=List.of();index=0;mode=Mode.NONE;expected=null;confirmEach=false;inputProblem="";}
    private CatalogComponent current(CatalogComponent previous) {
        return catalog.stream().filter(c->sameModel(c,previous)).findFirst().orElse(null);
    }
    private static boolean sameModel(CatalogComponent a,CatalogComponent b) {
        return a.productId()==b.productId() && a.category()==b.category() && clean(a.name()).equalsIgnoreCase(clean(b.name()));
    }
    private void rebind() {
        for(var entry:List.copyOf(owned.entrySet())) {
            var fresh=current(entry.getValue());
            if(fresh==null){invalidated.add(entry.getValue());owned.remove(entry.getKey());}
            else owned.put(entry.getKey(),fresh);
        }
        if(staged.values().stream().anyMatch(c->current(c)==null)||options.stream().anyMatch(c->current(c)==null)) {
            var lost=new ArrayList<>(staged.values());lost.addAll(options);cancelPending();
            for(var c:lost)if(current(c)==null && invalidated.stream().noneMatch(i->sameModel(i,c)))invalidated.add(c);
        } else {
            staged.replaceAll((key,value)->current(value));options=options.stream().map(this::current).toList();
        }
    }
    ChatBotService.Reply problem() {
        if(pending())return prompt();
        if(invalidated.isEmpty())return null;
        return reply("Каталог изменился: прежнюю имеющуюся модель нужно уточнить — "+names(invalidated)+". Не заменяю её и не включаю новую деталь в покупку автоматически. Назовите имеющуюся модель заново или очистите список.",List.of("Сбросить имеющиеся детали"));
    }
    static boolean isCommand(String raw) {
        String text=clean(raw);
        return PREFIX.matcher(text).find() || SUFFIX.matcher(text).find() || ABSENCE.matcher(text).find() || CLEAR_ALL.matcher(text).matches() || CLEAR_ONE.matcher(text).matches();
    }
    Outcome respond(String raw) {
        String text=clean(raw);
        int separator=text.indexOf(';');
        if(separator>0 && CLEAR_ALL.matcher(text.substring(0,separator).strip()).matches()) {
            clear();String remaining=text.substring(separator+1).strip();
            return buildMetadata(remaining)?new Outcome(true,remaining,null):new Outcome(true,"",reply("Список имеющихся деталей очищен. Сформулируйте следующий запрос отдельным сообщением.",List.of()));
        }
        if(CLEAR_ALL.matcher(text).matches()) {
            clear();return new Outcome(true,"",reply("Список имеющихся деталей очищен. В следующем подборе все выбранные категории будут покупаться.",List.of()));
        }
        var removal=CLEAR_ONE.matcher(text);
        if(removal.matches()) {
            var found=resolver.resolve(cleanQuery(removal.group(1)));
            var matches=found.candidates().stream().filter(c->owned.containsKey(c.category())&&sameModel(owned.get(c.category()),c)).toList();
            if(matches.size()!=1)return new Outcome(true,"",reply("Назовите точную имеющуюся модель, которую нужно убрать, или очистите весь список.",List.of("Сбросить имеющиеся детали")));
            var selected=matches.getFirst();owned.remove(selected.category());invalidated.removeIf(c->c.category()==selected.category());cancelPending();
            return new Outcome(true,"",reply("Больше не считаю имеющейся: "+selected.name()+". Эта категория теперь входит в стоимость покупки.",List.of()));
        }
        var absence=ABSENCE.matcher(text);
        if(absence.find()) {
            String beforeAbsence=text.substring(0,absence.start());
            if(HYPOTHETICAL.matcher(beforeAbsence).find() || quotedAssertion(beforeAbsence) || questionContext(beforeAbsence,text))
                return new Outcome(true,"",reply("Это условие, цитата или вопрос. Список имеющихся деталей не изменён. Чтобы убрать деталь, напишите явно: «У меня нет …».",List.of()));
            String body=text.substring(absence.end());var tail=TAIL.matcher(body);int end=tail.find()?tail.start():body.length();
            return deny(body.substring(0,end),text.substring(0,absence.start())+" "+body.substring(end));
        }
        var prefix=PREFIX.matcher(text);boolean hasPrefix=prefix.find();
        String before=hasPrefix?text.substring(0,prefix.start()):"";
        if(hasPrefix && NEGATIVE_CONTEXT.matcher(before).find()) {
            if(HYPOTHETICAL.matcher(before).find() || quotedAssertion(before) || questionContext(before,text))
                return new Outcome(true,"",reply("Это условие, цитата или вопрос. Список имеющихся деталей не изменён. Чтобы убрать деталь, напишите явно: «У меня нет …».",List.of()));
            String body=text.substring(prefix.end());var tail=TAIL.matcher(body);int end=tail.find()?tail.start():body.length();
            return deny(body.substring(0,end),before+" "+body.substring(end));
        }
        boolean confirm=HYPOTHETICAL.matcher(before).find() || quotedAssertion(before) || questionContext(before,text);
        var suffix=SUFFIX.matcher(text);
        if(!confirm && suffix.find() && plausiblePostfix(suffix.group(1))) {
            return declare(suffix.group(1),false,text.substring(suffix.end()));
        }
        if(hasPrefix) {
            String body=text.substring(prefix.end());var tail=TAIL.matcher(body);int end=tail.find()?tail.start():body.length();
            return declare(body.substring(0,end),confirm,before+" "+body.substring(end));
        }
        return answer(text,false);
    }
    private Outcome declare(String body,boolean confirm,String remaining) {
        if(pending()) {
            var proposed=splitQueries(body);
            if(confirm || proposed.size()!=1)return new Outcome(true,remaining,reply("Сейчас уточняем одну имеющуюся модель. Назовите её утвердительно; для нового списка сначала отмените уточнение.",List.of("Отменить уточнение")));
            var resolved=answer(proposed.getFirst(),true);
            return new Outcome(true,remaining,resolved.response());
        }
        begin(body,confirm);return new Outcome(true,remaining,advance());
    }
    private Outcome answer(String text,boolean declaration) {
        if(!pending())return Outcome.none(text);
        if(!declaration && buildMetadata(text))return Outcome.none(text);
        String normalized=ModelResolver.normalize(text);
        if(normalized.matches("нет|отмена|отмени|отменить уточнение|не подтверждаю")) {
            cancelPending();return new Outcome(true,"",reply("Уточнение отменено. Прежний список имеющихся деталей не изменён.",List.of()));
        }
        if(!inputProblem.isEmpty())return new Outcome(true,"",prompt());
        var declared=hint(text);
        if(declared!=null && expected!=null && declared!=expected)
            return new Outcome(true,"",reply("Уточняем имеющуюся деталь прежней категории. Назовите подходящую модель или отмените уточнение.",List.of("Отменить уточнение")));
        CatalogComponent chosen=null;
        if(!options.isEmpty()) {
            if(mode==Mode.CONFIRM && options.size()==1 && normalized.matches("да|подтверждаю|верно|да подтверждаю"))chosen=options.getFirst();
            else {
                String choice=text.replaceFirst("(?iu)^(?:выбираю|подтверждаю)(?:\\s+имеющ\\p{L}*)?(?:\\s+деталь)?\\s*[:—-]?\\s*","");
                if(choice.matches("[1-9]")) {
                    int selected=Integer.parseInt(choice)-1;if(selected<options.size())chosen=options.get(selected);
                } else {
                    var resolved=resolver.resolve(cleanQuery(choice));
                    if(resolved.status()==ModelResolver.Status.EXACT && resolved.candidates().size()==1)
                        chosen=options.stream().filter(c->sameModel(c,resolved.candidates().getFirst())).findFirst().orElse(null);
                }
            }
            if(chosen==null)return new Outcome(true,"",prompt());
        } else {
            var found=resolver.resolve(cleanQuery(text));
            if(expected!=null && !found.candidates().isEmpty() && found.candidates().stream().noneMatch(c->c.category()==expected))
                return new Outcome(true,"",reply("Нужна модель прежней категории; для нового списка напишите «У меня есть …».",List.of()));
            if(found.status()!=ModelResolver.Status.EXACT || found.candidates().size()!=1) {
                setResolution(found,text);return new Outcome(true,"",prompt());
            }
            chosen=found.candidates().getFirst();
            if(expected!=null && chosen.category()!=expected)return new Outcome(true,"",reply("Уточняем «"+queries.get(index)+"». Получена модель другой категории; для нового списка напишите «У меня есть …».",List.of()));
        }
        if(expected!=null && chosen.category()!=expected)return new Outcome(true,"",prompt());
        if(mode!=Mode.CONFLICT && conflicts(chosen))return new Outcome(true,"",prompt());
        accept(chosen);return new Outcome(true,"",advance());
    }
    private static boolean buildMetadata(String text) {
        String n=ModelResolver.normalize(text);
        return n.matches("(?:собери|собрать|подбери|подобрать|покажи варианты|показать варианты).*|(?:до|бюджет|на|за) [0-9].*|для (?:игр|офиса|учебы|работы|монтажа|рендера).*|(?:озу|ram|ssd|ссд) [0-9].*|(?:только проверенн|только подтвержденн|можно чернов|сбрось ограничения).*")
                || text.matches("(?iu)[0-9][0-9 ,.]*\\s*(?:руб\\p{L}*|тыс\\p{L}*|[кk]|₽)[.!?]*");
    }
    private void begin(String body,boolean confirm) {
        cancelPending();confirmEach=confirm;
        queries=splitQueries(body);
        if(queries.isEmpty())queries=List.of("");
        if(queries.size()>8){queries=List.of("");mode=Mode.MODEL;expected=null;inputProblem="В одном запросе можно указать до восьми имеющихся деталей. Начните список заново или отмените уточнение.";return;}
        mode=Mode.MODEL;
    }
    private ChatBotService.Reply advance() {
        if(!inputProblem.isEmpty())return prompt();
        while(index<queries.size()) {
            String original=queries.get(index),query=cleanQuery(original);var resolution=resolver.resolve(query);
            if(resolution.status()!=ModelResolver.Status.EXACT || resolution.candidates().size()!=1) {
                setResolution(resolution,original);return prompt();
            }
            var choice=resolution.candidates().getFirst();var declared=hint(original);
            if(declared!=null && declared!=choice.category()){options=List.of();mode=Mode.MODEL;expected=declared;return prompt();}
            if(confirmEach){options=List.of(choice);mode=Mode.CONFIRM;expected=choice.category();return prompt();}
            if(conflicts(choice))return prompt();
            accept(choice);
        }
        owned.putAll(staged);invalidated.removeIf(c->staged.containsKey(c.category()));cancelPending();return problem();
    }
    private boolean conflicts(CatalogComponent candidate) {
        var previous=staged.getOrDefault(candidate.category(),owned.get(candidate.category()));
        if(previous!=null && !sameModel(previous,candidate)) {
            options=List.of(previous,candidate);mode=Mode.CONFLICT;expected=candidate.category();return true;
        }
        return false;
    }
    private void accept(CatalogComponent candidate) {staged.put(candidate.category(),candidate);index++;options=List.of();mode=Mode.MODEL;expected=null;}
    private void setResolution(ModelResolver.Resolution found,String query) {
        var declared=hint(query);
        if(expected==null)expected=declared;
        if(expected==null && !found.candidates().isEmpty())expected=found.candidates().getFirst().category();
        options=found.candidates().stream().filter(c->expected==null || c.category()==expected).limit(8).toList();
        mode=options.isEmpty()?Mode.MODEL:switch(found.status()){case EXACT,AMBIGUOUS->Mode.CHOICE;case SUGGESTED->Mode.CONFIRM;case NOT_FOUND->Mode.MODEL;};
    }
    private ChatBotService.Reply prompt() {
        if(!inputProblem.isEmpty())return reply(inputProblem,List.of("Отменить уточнение"));
        String query=index<queries.size()?queries.get(index):"";
        String lead=switch(mode) {
            case CONFLICT->"Для одной категории выберите одну имеющуюся деталь. Какую используем в сборке?";
            case CONFIRM->confirmEach?"Запрос условный или приведён как пример. Нужно подтверждение, что эта модель уже у вас есть.":"Нужно подтверждение имеющейся модели: возможно, вы имели в виду другой вариант. Ничего не применяю автоматически.";
            case CHOICE->"Нашёл несколько моделей для «"+query+"». Какая именно уже у вас есть?";
            default->"Имеющаяся модель «"+query+"» не найдена однозначно в текущем каталоге. Уточните название. Без неё не продолжаю подбор и не добавляю замену в покупку.";
        };
        var suggestions=new ArrayList<String>();StringJoiner list=new StringJoiner("\n");
        for(int i=0;i<options.size();i++){var c=options.get(i);list.add((i+1)+". "+c.name());suggestions.add("Выбираю имеющуюся деталь: "+c.name());}
        suggestions.add("Отменить уточнение");
        return reply(lead+(options.isEmpty()?"":"\n"+list),suggestions);
    }
    private static List<String> splitQueries(String body) {
        return Arrays.stream(body.split("(?iu)\\s+и\\s+|[,;]+")).map(String::strip).filter(q->!q.isBlank()).toList();
    }
    private boolean plausiblePostfix(String text) {
        if(HYPOTHETICAL.matcher(text).find() || text.matches("(?iu).*(?:собери|собрать|подбери|подобрать|бюджет|нужно|нужен|привет|здравствуй).*"))return false;
        return resolver.resolve(cleanQuery(text)).status()!=ModelResolver.Status.NOT_FOUND || hint(text)!=null;
    }
    private static boolean questionContext(String before,String text) {
        return text.endsWith("?") || p("\\b(?:правда ли|верно ли|разве|возможно ли|может ли быть)\\b").matcher(before).find();
    }
    private static boolean quotedAssertion(String before) {
        return before.chars().filter(c->c=='«').count()>before.chars().filter(c->c=='»').count()
                || before.chars().filter(c->c=='"').count()%2==1 || before.chars().filter(c->c==39).count()%2==1
                || p("\\b(?:фраза|пример|цитата)\\b").matcher(before).find();
    }
    private Outcome deny(String body,String remaining) {
        cancelPending();var categories=EnumSet.noneOf(ComponentCategory.class);var stale=new ArrayList<CatalogComponent>();
        for(String query:splitQueries(body)) {
            var category=categoryOnly(query);
            if(category!=null){categories.add(category);continue;}
            var found=resolver.resolve(cleanQuery(query));
            if(found.status()==ModelResolver.Status.NOT_FOUND || found.status()==ModelResolver.Status.SUGGESTED) {
                var old=invalidated.stream().filter(c->ModelResolver.normalize(c.name()).equals(ModelResolver.normalize(cleanQuery(query)))).toList();
                if(old.isEmpty())return new Outcome(true,remaining,reply("Не удалось точно сопоставить отрицание модели. Список имеющихся не изменён; уточните название или очистите список.",List.of("Сбросить имеющиеся детали")));
                stale.addAll(old);continue;
            }
            for(var candidate:found.candidates()) {
                if(owned.containsKey(candidate.category()) && sameModel(owned.get(candidate.category()),candidate))categories.add(candidate.category());
                invalidated.stream().filter(c->sameModel(c,candidate)).forEach(stale::add);
            }
        }
        categories.forEach(owned::remove);invalidated.removeIf(c->categories.contains(c.category())||stale.contains(c));
        return new Outcome(true,remaining,reply("Указанные детали не учитываю как имеющиеся. Следующий подбор будет учитывать покупку отсутствующих категорий.",List.of()));
    }
    private static ComponentCategory categoryOnly(String query) {
        return switch(ModelResolver.normalize(query)) {
            case "видеокарта","видеокарты","gpu"->ComponentCategory.GPU;
            case "процессор","процессора","cpu"->ComponentCategory.CPU;
            case "озу","ram","оперативная память","оперативной памяти","оперативка"->ComponentCategory.RAM;
            case "ssd","накопитель","накопителя","диск","диска"->ComponentCategory.STORAGE;
            case "материнская плата","материнской платы","motherboard"->ComponentCategory.MOTHERBOARD;
            case "блок питания","блока питания","бп","psu"->ComponentCategory.PSU;
            case "корпус","корпуса","case"->ComponentCategory.CASE;
            case "кулер","кулера","охлаждение","cooler"->ComponentCategory.COOLER;
            default->null;
        };
    }
    private static ComponentCategory hint(String query) {
        String n=ModelResolver.normalize(query);
        for(var entry:Map.of("видеокарта",ComponentCategory.GPU,"процессор",ComponentCategory.CPU,
                "оперативная память",ComponentCategory.RAM,"накопитель",ComponentCategory.STORAGE,
                "материнская плата",ComponentCategory.MOTHERBOARD,"блок питания",ComponentCategory.PSU,
                "корпус",ComponentCategory.CASE,"кулер",ComponentCategory.COOLER).entrySet())
            if(n.equals(entry.getKey()) || n.startsWith(entry.getKey()+" "))return entry.getValue();
        if(p("\\b(?:rtx|gtx|rx|gpu|видеокарт\\p{L}*)\\b").matcher(n).find())return ComponentCategory.GPU;
        if(p("\\b(?:ryzen|core|i[3579]|cpu|процессор\\p{L}*)\\b").matcher(n).find())return ComponentCategory.CPU;
        if(p("\\b(?:озу|ram|оперативн\\p{L}*|оперативка)\\b").matcher(n).find())return ComponentCategory.RAM;
        if(p("\\b(?:ssd|ссд|накопител\\p{L}*)\\b").matcher(n).find())return ComponentCategory.STORAGE;
        if(p("\\b(?:материнск\\p{L}*|motherboard)\\b").matcher(n).find())return ComponentCategory.MOTHERBOARD;
        if(p("\\b(?:блок питания|бп|psu)\\b").matcher(n).find())return ComponentCategory.PSU;
        if(p("\\b(?:корпус\\p{L}*|case)\\b").matcher(n).find())return ComponentCategory.CASE;
        if(p("\\b(?:кулер\\p{L}*|охлажден\\p{L}*|cooler)\\b").matcher(n).find())return ComponentCategory.COOLER;
        return null;
    }
    private static String cleanQuery(String text) {
        return text.strip().replaceFirst("(?iu)^(?:еще|также)\\s+","").replaceFirst("(?iu)^(?:мой|моя|мое|моё)\\s+","")
                .replaceFirst("(?iu)^(?:видеокарта|процессор|материнская плата|оперативная память|накопитель|блок питания|корпус|кулер)\\s+","")
                .replaceAll("^[,:;.!? ]+|[,:;.!? ]+$","").strip();
    }
    private static String names(Collection<CatalogComponent> items){return items.stream().map(CatalogComponent::name).distinct().reduce((a,b)->a+", "+b).orElse("");}
    private static Pattern p(String value){return Pattern.compile(value,FLAGS);}
    private static String clean(String text){return Normalizer.normalize(Objects.toString(text,""),Normalizer.Form.NFKC).replaceAll("(?U)\\s+"," ").strip();}
    private static ChatBotService.Reply reply(String text,List<String> suggestions){return new ChatBotService.Reply(text,text,List.of(),List.of(),null,suggestions);}
}
