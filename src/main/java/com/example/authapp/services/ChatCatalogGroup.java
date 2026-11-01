package com.example.authapp.services;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;

/** Price/stock operations on an explicitly shown group, without model arithmetic or inferred hardware quality. */
final class ChatCatalogGroup {
    private static final List<String> ORDINALS=List.of("перв","втор","трет","четверт","пят","шест","седьм","восьм","девят","десят");
    static boolean question(String raw) {
        if(raw==null)return false;
        String text=normalize(raw);
        boolean group=Pattern.compile("(?U)\\b(?:них|этих|показан\\p{L}*|списк\\p{L}*)\\b").matcher(text).find();
        boolean ordinal=ORDINALS.stream().anyMatch(s->Pattern.compile("(?U)\\b"+s+"\\p{L}*\\b").matcher(text).find());
        boolean difference=text.matches(".*(?:разниц\\p{L}*.*цен|сэконом|насколько.*(?:дешев|доро[гж])).*");
        return (group || ordinal || difference) && (difference || text.matches(".*(?:дешев|доро[гж]|налич).*") || ChatCatalogFilter.hasPriceSyntax(raw));
    }
    static ChatProductContext.Result resolve(ChatBotService.Reply previous,String raw,ChatBotService.RequestSummary request) {
        var candidates=new LinkedHashMap<Integer,ChatBotService.ProductInfo>();
        if(previous!=null)for(var content:previous.content()) {
            if(content instanceof ChatBotService.ProductsContent p)p.items().forEach(item->candidates.putIfAbsent(item.id(),item));
            if(content instanceof ChatBotService.SpecificationsContent s){candidates.putIfAbsent(s.left().id(),s.left());candidates.putIfAbsent(s.right().id(),s.right());}
            if(content instanceof ChatBotService.PriceComparisonContent p){candidates.putIfAbsent(p.left().id(),p.left());candidates.putIfAbsent(p.right().id(),p.right());}
        }
        var items=List.copyOf(candidates.values());
        if(items.isEmpty())return clarify("Нет актуальной карточки со списком товаров. Сначала найдите товары или сравните две модели.",request);
        String text=normalize(raw);
        var filter=ChatCatalogFilter.parse(raw);
        if(filter.problem()!=null)return clarify(filter.problem(),request);
        String choice=normalize(filter.query());
        if(choice.matches(".*(?:не\\s+(?:сам\\p{L}*\\s+)?(?:дешев|доро[гж])|дешев.*доро[гж]|доро[гж].*дешев).*"))
            return clarify("Уточните один критерий: самый дешёвый, самый дорогой или разница цен двух товаров.",request);
        boolean difference=text.matches(".*(?:разниц\\p{L}*.*цен|сэконом|насколько.*(?:дешев|доро[гж])).*");
        boolean priceChoice=choice.matches(".*(?:дешев|доро[гж]).*");
        if(filter.active()) {
            if(difference)return clarify("Для разницы цен укажите два товара без дополнительного отбора по цене и наличию.",request);
            items=items.stream().filter(filter::accepts).toList();
            if(items.isEmpty())return answer(Boolean.FALSE.equals(filter.inStock()) && filter.minimum()==null && filter.maximum()==null?
                    "Все показанные товары в наличии.":"В показанном списке по этим условиям ничего не найдено.",List.of(),request);
            if(!priceChoice)return answer("Товары из показанного списка, соответствующие условиям цены и наличия:",items,request);
        }
        if(difference) {
            var indices=indices(text);
            for(int index:indices)if(index<0 || index>=items.size())return clarify("В показанном списке нет такого номера. Назовите два номера из списка.",request);
            if(indices.isEmpty() && items.size()==2)indices=List.of(0,1);
            if(indices.size()!=2 || indices.get(0).equals(indices.get(1)))return clarify("Укажите два разных товара: названия или два номера в показанном списке.",request);
            var a=items.get(indices.get(0));var b=items.get(indices.get(1));
            BigDecimal delta=a.price().subtract(b.price()).abs();
            String relation=delta.signum()==0?"Цены одинаковые.":(a.price().compareTo(b.price())>0?a.name():b.name())+" дороже по цене каталога.";
            String answer="Разница цен: "+money(delta)+" ₽. "+a.name()+" — "+money(a.price())+" ₽; "+b.name()+" — "+money(b.price())+" ₽. "+relation+" Цена не показывает производительность.";
            return new ChatProductContext.Result(new ChatBotService.Reply(answer,"Сравнил цены двух товаров из показанного списка.",List.of(),
                    List.of(new ChatBotService.PriceComparisonContent(a,b)),request,List.of()),true);
        }
        if(candidates.size()==1)return clarify("В показанной группе только один товар. Для выбора по цене найдите несколько товаров или сравните две модели.",request);
        boolean expensive=Pattern.compile("(?U)(?<!\\p{L})доро[гж]").matcher(choice).find() || choice.contains("недешев");
        BigDecimal price=items.stream().map(ChatBotService.ProductInfo::price).reduce((a,b)->expensive?a.max(b):a.min(b)).orElseThrow();
        var selected=items.stream().filter(p->p.price().compareTo(price)==0).toList();
        String scope=filter.active()?" среди показанных товаров после отбора по цене и наличию":" среди показанных товаров";
        return answer((expensive?"Самая высокая":"Самая низкая")+" цена"+scope+": "+money(price)+" ₽. "+(selected.size()>1?"Эта цена у нескольких товаров. ":"")+"Цена не показывает производительность. Наличие указано в карточках.",selected,request);
    }
    private static List<Integer> indices(String text) {
        String words=String.join("|",ORDINALS);
        var match=Pattern.compile("(?U)\\b(?:"+words+")\\p{L}*\\b|(?<![\\p{L}\\p{N}])(?:10|[1-9])(?![\\p{L}\\p{N}])").matcher(text);
        var result=new ArrayList<Integer>();
        while(match.find()) {
            String token=match.group();int index;
            if(Character.isDigit(token.charAt(0)))index=Integer.parseInt(token)-1;
            else {index=-1;for(int i=0;i<ORDINALS.size();i++)if(token.startsWith(ORDINALS.get(i))){index=i;break;}}
            result.add(index);
        }return List.copyOf(result);
    }
    private static String normalize(String raw){return raw.toLowerCase(Locale.ROOT).replace('ё','е');}
    private static String money(BigDecimal value){var f=NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));f.setMaximumFractionDigits(2);return f.format(value);}
    private static ChatProductContext.Result answer(String text,List<ChatBotService.ProductInfo> items,ChatBotService.RequestSummary request) {
        return new ChatProductContext.Result(new ChatBotService.Reply(text,text,List.of(),items.isEmpty()?List.of():List.of(new ChatBotService.ProductsContent(items,items.size())),request,List.of()),true);
    }
    private static ChatProductContext.Result clarify(String text,ChatBotService.RequestSummary request){return new ChatProductContext.Result(new ChatBotService.Reply(text,text,List.of(),List.of(),request,List.of()),false);}
}
