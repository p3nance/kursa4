package com.example.authapp.services;

import com.example.authapp.domain.CatalogComponent;
import com.example.authapp.domain.ComponentCategory;
import com.example.authapp.domain.ComponentSpecs;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Explicit build constraints only; no hardware fact is inferred from a user request. */
public final class BuildRequestParser {
    private static final List<String> USAGE_CHOICES=List.of("Для игр","Для учёбы и офиса","Для монтажа и рабочих задач");
    static List<String> usageChoices(){return USAGE_CHOICES;}
    /** Application-generated task choices are commands, including their complete visible labels. */
    static java.util.Optional<BuildRecommendationService.Usage> explicitUsageChoice(String raw) {
        return java.util.Optional.ofNullable(switch(ModelResolver.normalize(raw)) {
            case "для игр"->BuildRecommendationService.Usage.GAMING;
            case "для учебы и офиса","для офиса"->BuildRecommendationService.Usage.OFFICE;
            case "для монтажа и рабочих задач","для монтажа","для работы"->BuildRecommendationService.Usage.WORKSTATION;
            default->null;
        });
    }
    public record Preferences(Integer minRamGb, Integer minStorageGb, boolean requireVerified) {
        /** An explicit capacity is satisfied only by a manufacturer-verified value. */
        public boolean accepts(CatalogComponent component) {
            Integer minimum=component.category()==ComponentCategory.RAM?minRamGb:
                    component.category()==ComponentCategory.STORAGE?minStorageGb:null;
            return minimum==null || component.specs().fact(ComponentSpecs.CAPACITY_GB)
                    .filter(ComponentSpecs.Fact::verified).map(f->f.value()>=minimum).orElse(false);
        }
        public static Preferences defaults() { return new Preferences(null, null, false); }
        public BuildRecommendationService.Request request(BigDecimal budget, BuildRecommendationService.Usage usage) {
            var base=BuildRecommendationService.Request.forUsage(budget,usage);
            return new BuildRecommendationService.Request(budget,usage,
                    minRamGb==null?base.minRamGb():minRamGb,
                    minStorageGb==null?base.minStorageGb():minStorageGb,base.ownedComponents(),requireVerified);
        }
    }
    public record Patch(Integer ram, Integer storage, Boolean verified, boolean reset, List<String> problems) {
        public Patch { problems=List.copyOf(problems); }
        public boolean hasChanges() { return ram!=null || storage!=null || verified!=null || reset || !problems.isEmpty(); }
        public Preferences apply(Preferences previous) {
            if(!problems.isEmpty()) return previous;
            var base=reset?Preferences.defaults():previous;
            return new Preferences(ram==null?base.minRamGb():ram,storage==null?base.minStorageGb():storage,
                    verified==null?base.requireVerified():verified);
        }
    }
    private static final int FLAGS=Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE|Pattern.UNICODE_CHARACTER_CLASS;
    private static final String RAM="(?:озу|ram|оперативк[ауие]|оперативн(?:ая|ой|ую)\\s+памят(?:ь|и))";
    private static final String STORAGE="(?:ssd|ссд|накопител[ьяею]|диск(?:а)?)";
    private static final String LIMIT="(?:(не\\s+(?:меньше|менее|больше|более)|минимум|максимум|от|до|ровно|точно)\\s+)?";
    private static final String SIZE="((?:[0-9]{1,3}(?: [0-9]{3})+|[0-9]+)(?:[.,][0-9]+)?)\\s*(гб|gb|тб|tb|гигабайт(?:а|ов)?|гига|гигов|терабайт(?:а|ов)?)";
    private static List<Pattern> patterns(String label) {
        return List.of(Pattern.compile("(?<![-\\p{L}\\p{N}.,])"+LIMIT+SIZE+"\\s+"+label+"\\b",FLAGS),
                Pattern.compile("\\b"+label+"\\s*[:=]?\\s*"+LIMIT+SIZE+"(?![\\p{L}\\p{N}])",FLAGS));
    }
    private static final List<Pattern> RAM_PATTERNS=patterns(RAM),STORAGE_PATTERNS=patterns(STORAGE);

    public Patch parse(String raw) {
        String text=Normalizer.normalize(raw==null?"":raw,Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("(?U)\\s+"," ").trim();
        var problems=new ArrayList<String>();
        Integer ram=capacity(text,RAM_PATTERNS,"ОЗУ",problems),storage=capacity(text,STORAGE_PATTERNS,"SSD",problems);
        if(Pattern.compile("(?:добав\\p{L}*|прибав\\p{L}*|увелич\\p{L}*|уменьш\\p{L}*|сниз\\p{L}*|убав\\p{L}*)\\s+(?:(?:"+RAM+"|"+STORAGE+")\\s+)?(?:на\\s+)?"+SIZE,FLAGS).matcher(text).find())
            problems.add("Укажите итоговый минимум объёма, например «ОЗУ не меньше 32 ГБ». Прибавление и вычитание объёма пока не поддерживаются.");
        if(ram==null && Pattern.compile("\\b"+RAM+"\\b",FLAGS).matcher(text).find() && text.matches(".*[0-9].*"))
            problems.add("Укажите минимум ОЗУ с единицей, например «ОЗУ 32 ГБ».");
        if(storage==null && Pattern.compile("\\b"+STORAGE+"\\b",FLAGS).matcher(text).find() && text.matches(".*[0-9].*"))
            problems.add("Укажите минимум SSD с единицей, например «SSD 2 ТБ».");
        Boolean verified=null;
        if(text.matches(".*(?:только (?:проверенн|подтвержденн)|без (?:непроверенн|неизвестн)).*")) verified=true;
        if(text.matches(".*(?:можно чернов|разреши чернов|разрешить чернов|снять строг|отключи строг).*")) {
            if(Boolean.TRUE.equals(verified))problems.add("Выберите один режим: только проверенная совместимость или черновой вариант.");
            verified=false;
        }
        boolean reset=text.matches(".*(?:сбрось|сбросить|убери|очисти) ограничения(?: по памяти)?(?:[,;.!?]|$).*");
        if(text.matches(".*(?:у меня (?:уже )?есть|уже есть|имеющ(?:иеся|ийся|аяся)|без видеокарт|только (?:intel|amd|nvidia)|ddr[345]).*"))
            problems.add("Имеющиеся детали, исключение категорий и выбор конкретной платформы пока задавайте в конфигураторе. В чате доступны бюджет, задача и минимумы ОЗУ/SSD.");
        if(text.matches("(?U).*(?:\\b(?:с|на)\\s+(?:(?:процессор[^ ]*|видеокарт[^ ]*)\\s+)?(?:rtx|gtx|rx|ryzen|intel|amd|core|i[3579])|hdd|жестк).*"))
            problems.add("Фиксированные модели и HDD пока выбирайте вручную в конфигураторе. Эти условия автоматически не применены.");
        return new Patch(ram,storage,verified,reset,problems);
    }
    private static Integer capacity(String text,List<Pattern> patterns,String name,List<String> problems) {
        Integer value=null;
        for(var pattern:patterns) {
            var match=pattern.matcher(text);
            while(match.find()) {
                String limit=match.group(1);
                if(limit!=null && limit.matches("до|максимум|ровно|точно|не (?:больше|более)")) {
                    problems.add("Для "+name+" пока поддерживается минимум объёма. Уточните «не меньше N ГБ».");continue;
                }
                try {
                    BigDecimal amount=new BigDecimal(match.group(2).replace(" ","").replace(',','.'));
                    String unit=match.group(3);
                    if(unit.equals("тб")||unit.equals("tb")||unit.startsWith("терабайт")) amount=amount.multiply(BigDecimal.valueOf(1000));
                    int next=amount.intValueExact();
                    if(next<1 || next>1_000_000) throw new ArithmeticException();
                    if(value!=null && value!=next) problems.add("Указано несколько разных минимумов "+name+". Оставьте одно значение.");
                    else value=next;
                } catch(ArithmeticException ex) { problems.add("Укажите положительный целый объём "+name+" в ГБ."); }
            }
        }
        return value;
    }
}
