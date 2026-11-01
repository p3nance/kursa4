package com.example.authapp.services;

import com.example.authapp.domain.*;
import com.example.authapp.models.Product;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;
import static com.example.authapp.domain.ComponentSpecs.CAPACITY_GB;

/** Edits the last proposed composition. Model names, prices and assessment always come from Java. */
final class ChatBuildEditor {
    private record Edit(ComponentCategory category,String query,int variant,String source) {}
    private static final Pattern CHANGE=Pattern.compile("^(?:пожалуйста\\s+|можешь\\s+|давай\\s+)?(?:замени(?:ть)?|поменяй|смени)\\s+(.+?)(?:\\s+на\\s+(.+))?$");
    private static final Pattern INSTALL=Pattern.compile("^(?:пожалуйста\\s+)?(?:поставь|установи)\\s+(.+?)\\s+(?:вместо\\s+(.+)|в\\s+сборку)$");
    private static final Pattern VARIANT=Pattern.compile("\\s+в(?:о)?\\s+(?:варианте|сборке)\\s*([1-3])$");
    private static final Pattern PARTIAL_MODEL=Pattern.compile("^(?:(amd|intel|nvidia)\\s+)?(?:(ryzen(?:\\s+[3579])?|core\\s+i[3579]|i[3579]|radeon\\s+rx|geforce\\s+(?:rtx|gtx)|rtx|gtx|rx)\\s+)?(\\d{3,5})$");
    private final List<CatalogComponent> catalog;
    private final ModelResolver resolver;
    private final Map<Integer,ChatBotService.ProductInfo> products;
    private List<ChatBotService.BuildContent> builds=List.of();
    private int selected=-1;
    private Edit pending;
    private boolean choosingVariant;
    private List<CatalogComponent> choices=List.of();
    private String requestedBrand="";
    private List<CatalogComponent> brandOptions=List.of();

    ChatBuildEditor(Collection<CatalogComponent> catalog,Collection<Product> products) {
        this.catalog=List.copyOf(catalog);resolver=new ModelResolver(catalog);
        var infos=new HashMap<Integer,ChatBotService.ProductInfo>();products.forEach(p->infos.put(p.getId(),ChatBotService.ProductInfo.from(p)));this.products=Map.copyOf(infos);
    }
    private ChatBuildEditor(ChatBuildEditor other) {
        catalog=other.catalog;resolver=other.resolver;products=other.products;builds=other.builds;selected=other.selected;
        pending=other.pending;choosingVariant=other.choosingVariant;choices=other.choices;requestedBrand=other.requestedBrand;brandOptions=other.brandOptions;
    }
    ChatBuildEditor copy(){return new ChatBuildEditor(this);}
    boolean pending(){return pending!=null;}
    void clear(){builds=List.of();selected=-1;cancel();}
    private void cancel(){pending=null;choices=List.of();choosingVariant=false;requestedBrand="";brandOptions=List.of();}
    void observe(ChatBotService.Reply reply) {
        var cards=reply.content().stream().filter(ChatBotService.BuildContent.class::isInstance).map(ChatBotService.BuildContent.class::cast).toList();
        if(!cards.isEmpty()){builds=cards;selected=cards.size()==1?0:-1;cancel();}
    }
    static boolean isCommand(String raw) {return parse(raw)!=null || negative(ModelResolver.normalize(raw));}
    private static boolean negative(String text){return category(text.replaceFirst("^не (?:меняй|заменяй)\\s+","").split("\\s+на\\s+",2)[0])!=null && text.startsWith("не ");}
    private static Edit parse(String raw) {
        if(raw==null || raw.length()>2000)return null;
        String text=ModelResolver.normalize(raw);int variant=-1;
        var v=VARIANT.matcher(text);if(v.find()){variant=Integer.parseInt(v.group(1))-1;text=text.substring(0,v.start()).strip();}
        var change=CHANGE.matcher(text);
        if(change.matches()) {
            String from=change.group(1),to=Objects.toString(change.group(2),"");
            var category=category(from);
            // Budget, comparison order and arbitrary prose must keep their own routing.
            if(category==null && !looksLikeModel(from))return null;
            return new Edit(category,to,variant,from);
        }
        var install=INSTALL.matcher(text);
        if(install.matches() && looksLikeModel(install.group(1)))return new Edit(category(install.group(2)),install.group(1),variant,Objects.toString(install.group(2),""));
        return null;
    }
    Optional<ChatBotService.Reply> respond(String raw,ChatBotService.RequestSummary summary) {
        String text=ModelResolver.normalize(raw);
        if(text.matches("(?:отмена|не надо|не меняй(?: .*)?|не заменяй(?: .*)?|оставь как есть)")) {
            if(!pending() && !negative(text))return Optional.empty();
            cancel();return Optional.of(message("Состав сборки оставлен без изменений.",summary,List.of()));
        }
        Edit edit=parse(raw);
        if(edit==null && pending==null)return Optional.empty();
        if(edit==null && new ChatIntentParser().parse(raw).intent()!=ChatIntentParser.Intent.OTHER){cancel();return Optional.empty();}
        if(builds.isEmpty()){cancel();return Optional.of(message("Сначала подберите сборку, затем назовите компонент для замены.",summary,List.of("Собери ПК")));}
        if(edit!=null) {
            cancel();pending=edit;
            if(edit.variant()>=builds.size()){choosingVariant=true;return Optional.of(variantPrompt(summary));}
            if(edit.variant()>=0)selected=edit.variant();
            if(selected<0){choosingVariant=true;return Optional.of(variantPrompt(summary));}
        } else if(choosingVariant) {
            int index=ordinal(text,builds.size());
            if(index<0)return leaveOrPrompt(raw,summary,variantPrompt(summary));
            selected=index;choosingVariant=false;
        } else if(!choices.isEmpty()) {
            int index=ordinal(text,choices.size());
            if(index<0 && choices.size()==1 && text.matches("(?:да|подтверждаю|давай|подойдет)"))index=0;
            if(index<0) {
                var found=resolver.resolve(text);
                if(found.status()==ModelResolver.Status.EXACT && found.candidates().size()==1)
                    for(int i=0;i<choices.size();i++)if(choices.get(i).productId()==found.candidates().getFirst().productId())index=i;
                if(index<0 && !requestedBrand.isEmpty() && found.status()==ModelResolver.Status.EXACT && found.candidates().size()==1
                        && brandOptions.stream().anyMatch(c->c.productId()==found.candidates().getFirst().productId()))return Optional.of(apply(found.candidates().getFirst(),summary));
            }
            if(index<0)return leaveOrPrompt(raw,summary,choicePrompt(summary));
            return Optional.of(apply(choices.get(index),summary));
        } else if(pending.query().isBlank())pending=new Edit(pending.category(),text,pending.variant(),pending.source());
        if(pending.query().isBlank())return Optional.of(prompt("На какую точную модель заменить компонент?",summary));
        String brand=brand(pending.query());
        if(!brand.isEmpty()) {
            ComponentCategory target=pending.category();
            if(target==null) {
                var source=resolver.resolve(pending.source());
                if(source.status()==ModelResolver.Status.EXACT && source.candidates().size()==1)target=source.candidates().getFirst().category();
            }
            if(target==null)return Optional.of(rejectPick("Уточните категорию для замены на "+brand+": процессор, видеокарта или другая деталь?",summary));
            var family=brandFamily(pending.query());
            if(family!=null && family!=target)return Optional.of(rejectPick("Названное семейство относится к другой категории. Уточните модель или производителя для выбранного компонента.",summary));
            final var category=target;
            var old=builds.get(selected);
            brandOptions=catalog.stream().filter(c->c.category()==category && c.stock()>0 && c.price().signum()>0)
                    .filter(c->ModelResolver.normalize(products.get(c.productId()).manufacturer()).equals(brand))
                    .filter(c->old.parts().stream().noneMatch(p->p.id()==c.productId()))
                    .sorted(Comparator.comparing((CatalogComponent c)->conflicts(c,old)).thenComparing(CatalogComponent::price).thenComparingInt(CatalogComponent::productId)).toList();
            requestedBrand=brand;choices=brandOptions.stream().limit(5).toList();
            if(choices.isEmpty())return Optional.of(rejectPick("Других моделей "+brand.toUpperCase(Locale.ROOT)+" этой категории в наличии нет. Прежний состав сохранён.",summary));
            return Optional.of(choicePrompt(summary));
        }
        var found=resolver.resolve(pending.query());
        if(found.status()==ModelResolver.Status.EXACT && found.candidates().size()==1)return Optional.of(apply(found.candidates().getFirst(),summary));
        var number=PARTIAL_MODEL.matcher(pending.query());boolean partial=number.matches();
        String maker=partial?number.group(1):null,family=partial?number.group(2):null;
        java.util.function.Predicate<CatalogComponent> matchesIdentity=c->
                (maker==null || ModelResolver.normalize(products.get(c.productId()).manufacturer()).equals(maker))
                && (family==null || ModelResolver.normalize(c.name()).contains(family+" "));
        choices=found.candidates().stream().filter(c->pending.category()==null || c.category()==pending.category())
                .filter(matchesIdentity).limit(5).toList();
        if(choices.isEmpty() && partial) {
            // A partial model number can offer catalog variants, but never selects one silently.
            // Keep every explicit maker/family/tier; a CPU7800 must not suggest the RX7800 GPU.
            var digits=Pattern.compile("(?<![\\p{L}\\p{N}])"+Pattern.quote(number.group(3))+"(?![0-9])");
            choices=catalog.stream().filter(c->pending.category()==null || c.category()==pending.category())
                    .filter(c->c.stock()>0 && c.price().signum()>0).filter(matchesIdentity)
                    .filter(c->digits.matcher(ModelResolver.normalize(c.name())).find()).limit(5).toList();
        }
        if(choices.isEmpty()){pending=new Edit(pending.category(),"",pending.variant(),pending.source());return Optional.of(prompt("Точная модель не найдена в каталоге. Назовите другую модель; прежняя сборка сохранена.",summary));}
        return Optional.of(choicePrompt(summary));
    }
    private Optional<ChatBotService.Reply> leaveOrPrompt(String raw,ChatBotService.RequestSummary summary,ChatBotService.Reply reply) {
        var intent=new ChatIntentParser().parse(raw).intent();
        if(intent!=ChatIntentParser.Intent.OTHER){cancel();return Optional.empty();}
        return Optional.of(reply);
    }
    private ChatBotService.Reply apply(CatalogComponent replacement,ChatBotService.RequestSummary summary) {
        var old=builds.get(selected);
        if(pending.category()==null && looksLikeModel(pending.source())) {
            var source=resolver.resolve(pending.source());
            if(source.status()!=ModelResolver.Status.EXACT || source.candidates().size()!=1)return rejectPick("Уточните заменяемый компонент: назовите его категорию или точную модель из предложенной сборки.",summary);
            var previous=source.candidates().getFirst();
            if(previous.category()!=replacement.category())return rejectPick("Заменяемая и новая модель относятся к разным категориям. Прежняя сборка сохранена.",summary);
            if(old.parts().stream().noneMatch(p->p.id()==previous.productId()))return rejectPick("Названного заменяемого компонента нет в выбранной сборке. Прежний состав сохранён.",summary);
        }
        if(pending.category()!=null && replacement.category()!=pending.category())return rejectPick("Названная модель относится к другой категории. Уточните компонент для замены.",summary);
        if(replacement.stock()<=0 || replacement.price().signum()<=0)return rejectPick("Эта модель сейчас недоступна для покупки. Назовите другую модель; прежняя сборка сохранена.",summary);
        var parts=new ArrayList<CatalogComponent>();ChatBotService.ProductInfo removed=null;
        for(var part:old.parts()) {
            var fresh=catalog.stream().filter(c->c.productId()==part.id() && c.name().equals(part.name())).findFirst();
            if(fresh.isEmpty()){clear();return message("Каталог изменился. Подберите сборку заново перед заменой.",summary,List.of("Собери ПК"));}
            if(fresh.get().category()==replacement.category()){removed=part;parts.add(replacement);}else parts.add(fresh.get());
        }
        if(removed==null)return prompt("В выбранной сборке нет компонента этой категории.",summary);
        var owned=new HashSet<>(old.ownedProductIds());if(removed.id()!=replacement.productId())owned.remove(removed.id());
        BigDecimal total=parts.stream().filter(c->!owned.contains(c.productId())).map(CatalogComponent::price).reduce(BigDecimal.ZERO,BigDecimal::add);
        var assessment=new CompatibilityService().assess(parts);
        var findings=new ArrayList<String>();
        assessment.findings().stream().filter(f->f.status()!=CompatibilityService.Status.COMPATIBLE).map(CompatibilityService.Finding::message).forEach(findings::add);
        BigDecimal limit=summary.budget()==null?old.budget():summary.budget();
        var currentUsage=Objects.requireNonNullElse(summary.usage(),old.usage());
        var defaults=BuildRecommendationService.Request.forUsage(limit,currentUsage);
        for(var part:parts) {
            Integer min=part.category()==ComponentCategory.RAM?Objects.requireNonNullElse(summary.minRamGb(),defaults.minRamGb()):part.category()==ComponentCategory.STORAGE?Objects.requireNonNullElse(summary.minStorageGb(),defaults.minStorageGb()):null;
            if(min!=null && !part.specs().fact(CAPACITY_GB).map(f->f.verified() && f.value()>=min).orElse(false))findings.add("Не подтверждён заданный минимум объёма: "+part.category().displayName()+" — "+min+" ГБ.");
        }
        boolean over=limit!=null && total.compareTo(limit)>0;
        if(over)findings.add("Бюджет превышен на "+money(total.subtract(limit))+". Исходный бюджет сохранён.");
        boolean verified=assessment.status()==CompatibilityService.Status.COMPATIBLE && findings.isEmpty();
        var limits=new ArrayList<>(assessment.limitations());
        limits.add("Изменена только названная категория; остальные компоненты сохранены. Цены и наличие — текущий снимок каталога.");
        if(!verified)limits.add("Это черновик. До покупки устраните конфликты и проверьте неизвестные характеристики, минимумы объёма и бюджет.");
        String lead="Заменил «"+removed.name()+"» на «"+replacement.name()+"». "+(assessment.hasConflict()?"В черновике есть конфликты совместимости.":verified?"Совместимость подтверждена доступными данными.":"Совместимость и ограничения требуют проверки.");
        if(over)lead+=" Бюджет превышен на "+money(total.subtract(limit))+"; его значение не изменено.";
        var infos=parts.stream().map(c->products.get(c.productId())).toList();
        var card=new ChatBotService.BuildContent(infos,total,limit,currentUsage,verified,findings,limits,owned);
        builds=List.of(card);selected=0;cancel();
        String body=lead+"\n"+String.join("\n",infos.stream().map(p->p.category()+": "+p.name()+" — "+(owned.contains(p.id())?"уже есть":money(p.price()))).toList())+"\nК покупке: "+money(total)+"\n"+String.join("\n",findings);
        return new ChatBotService.Reply(body,lead,infos.stream().map(ChatBotService.ProductInfo::toProduct).toList(),List.of(card),summary,List.of("Объясни сборку"));
    }
    private ChatBotService.Reply variantPrompt(ChatBotService.RequestSummary summary) {
        return message("В какой из предложенных сборок заменить компонент?",summary,java.util.stream.IntStream.range(0,builds.size()).mapToObj(i->"Вариант "+(i+1)).toList());
    }
    private ChatBotService.Reply choicePrompt(ChatBotService.RequestSummary summary) {
        if(!requestedBrand.isEmpty()) {
            var old=builds.get(selected);
            String options=String.join("\n",java.util.stream.IntStream.range(0,choices.size()).mapToObj(i->{var c=choices.get(i);return (i+1)+". "+c.name()+" — "+money(c.price())+(conflicts(c,old)?"; есть конфликт с текущими деталями":"; полная совместимость требует проверки");}).toList());
            String text="Выберите конкретную модель "+requestedBrand.toUpperCase(Locale.ROOT)+" для замены:\n"+options+"\nДо выбора состав не меняется. При смене платформы могут понадобиться другая плата или память; после выбора покажу результат проверки.";
            if(brandOptions.size()>choices.size())text+=" Показаны "+choices.size()+" из "+brandOptions.size()+"; можно назвать другую точную модель этого бренда.";
            return message(text,summary,choices.stream().map(CatalogComponent::name).toList());
        }
        return message("Уточните точную модель для замены. Выберите товар из каталога: "+String.join("; ",choices.stream().map(CatalogComponent::name).toList())+". До выбора состав не меняется.",summary,choices.stream().map(CatalogComponent::name).toList());
    }
    private ChatBotService.Reply prompt(String text,ChatBotService.RequestSummary summary){return message(text,summary,List.of());}
    private ChatBotService.Reply rejectPick(String text,ChatBotService.RequestSummary summary){pending=new Edit(pending.category(),"",pending.variant(),pending.source());choices=List.of();requestedBrand="";brandOptions=List.of();return prompt(text,summary);}
    private static ChatBotService.Reply message(String text,ChatBotService.RequestSummary summary,List<String> choices){return new ChatBotService.Reply(text,text,List.of(),List.of(),summary,choices);}
    private static String money(BigDecimal amount){return NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).format(amount)+" ₽";}
    private static int ordinal(String text,int size) {
        String value=text.replaceFirst("^(?:вариант|сборка|модель)\\s+","");
        int n=switch(value){case "первый","первую","первое","1"->0;case "второй","вторую","второе","2"->1;case "третий","третью","третье","3"->2;case "четвертый","четвертую","четвертое","4"->3;case "пятый","пятую","пятое","5"->4;default->-1;};return n<size?n:-1;
    }
    private boolean conflicts(CatalogComponent candidate,ChatBotService.BuildContent build) {
        var parts=build.parts().stream().map(p->catalog.stream().filter(c->c.productId()==p.id()).findFirst().orElseThrow())
                .map(c->c.category()==candidate.category()?candidate:c).toList();
        return new CompatibilityService().assess(parts).hasConflict();
    }
    private static String brand(String query) {
        String value=ModelResolver.normalize(query);
        value=switch(value){case "ryzen","amd ryzen","radeon","amd radeon"->"amd";case "geforce","nvidia geforce"->"nvidia";default->value;};
        return Set.of("amd","intel","nvidia","asus","msi","gigabyte","asrock","kingston","g skill","corsair","patriot","seagate","wd","samsung","crucial","be quiet","deepcool","seasonic","cooler master","fractal design","nzxt","lian li","phanteks","noctua").contains(value)?value:"";
    }
    private static ComponentCategory brandFamily(String query){return switch(ModelResolver.normalize(query)){case "ryzen","amd ryzen"->ComponentCategory.CPU;case "radeon","amd radeon","geforce","nvidia geforce"->ComponentCategory.GPU;default->null;};}
    private static boolean looksLikeModel(String value){return value!=null && Pattern.compile("\\d{3,5}").matcher(value).find();}
    private static ComponentCategory category(String text) {
        if(text==null)return null;
        return switch(text.strip()) {
            case "видеокарту","видеокарты","gpu","графику"->ComponentCategory.GPU;
            case "процессор","проц","проца","цп","цпу","cpu"->ComponentCategory.CPU;
            case "материнскую плату","материнку","плату"->ComponentCategory.MOTHERBOARD;
            case "оперативку","оперативную память","озу","ram","память"->ComponentCategory.RAM;
            case "ssd","ссд","накопитель","диск"->ComponentCategory.STORAGE;
            case "блок питания","бп"->ComponentCategory.PSU;
            case "корпус"->ComponentCategory.CASE;
            case "кулер","охлаждение","сжо"->ComponentCategory.COOLER;
            default->null;
        };
    }
}
