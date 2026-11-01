package com.example.authapp.services;

import com.example.authapp.domain.ComponentCategory;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Pattern;

/** Resolves references only against the last immutable card; never guesses a model from text/history. */
final class ChatProductContext {
    record Result(ChatBotService.Reply reply,boolean resolved) {}
    private static final List<String> ORDINALS=List.of("перв","втор","трет","четверт","пят","шест","седьм","восьм","девят","десят");
    static boolean simpleOrdinal(String raw) {
        return raw!=null && raw.strip().toLowerCase(Locale.ROOT).replace('ё','е').matches("(?U)^(?:(?:а|тогда|давай|покажи|можно|хочу)\\s+)?(?:[1-9]|10|(?:перв|втор|трет|четверт|пят|шест|седьм|восьм|девят|десят)\\p{L}*(?:\\s+(?:вариант|товар|модель))?)[.!?]*$");
    }
    static boolean referenceQuestion(String raw) {
        if(raw==null)return false;
        String text=raw.toLowerCase(Locale.ROOT).replace('ё','е');
        if(text.matches(".*(?:корзин|закаж|оформи|купи|поменяй цену|измени цену|снизь цену).*"))return false;
        return Pattern.compile("(?iuU)\\b(?:эт\\p{L}*|он|она|оно|они|его|ее|него|нее|них|перв\\p{L}*|втор\\p{L}*|трет\\p{L}*)\\b|(?:из|в) (?:этой )?сборк\\p{L}*").matcher(text).find()
                && Pattern.compile("(?iuU)\\b(?:стоит|стоимост\\p{L}*|цен\\p{L}*|денег|налич\\p{L}*|назван\\p{L}*|называ\\p{L}*|что за|дешев\\p{L}*|доро[гж]\\p{L}*)\\b").matcher(text).find();
    }
    static boolean applies(ChatBotService.Reply previous,String raw) {
        if(!referenceQuestion(raw))return false;
        boolean build=previous!=null && previous.content().stream().anyMatch(ChatBotService.BuildContent.class::isInstance);
        return !build || category(raw.toLowerCase(Locale.ROOT).replace('ё','е'))!=null || ordinal(raw.toLowerCase(Locale.ROOT))>=0
                || raw.toLowerCase(Locale.ROOT).matches(".*(?:товар|компонент|детал).*" );
    }
    static boolean hasOrderedProducts(ChatBotService.Reply reply) {
        return reply!=null && reply.content().stream().anyMatch(c->c instanceof ChatBotService.ProductsContent || c instanceof ChatBotService.SpecificationsContent || c instanceof ChatBotService.BuildContent || c instanceof ChatBotService.BenchmarksContent || c instanceof ChatBotService.PriceComparisonContent);
    }
    static boolean usesGroup(String raw) {
        return raw!=null && (ordinal(raw.toLowerCase(Locale.ROOT).replace('ё','е'))>=0
                || raw.toLowerCase(Locale.ROOT).matches(".*(?:из них|из этих|из списка|показанн).*"));
    }
    static Result resolve(ChatBotService.Reply previous,String raw,ChatBotService.RequestSummary request) {
        if(previous!=null && simpleOrdinal(raw) && previous.content().stream().anyMatch(ChatBotService.BuildContent.class::isInstance)) {
            var builds=previous.content().stream().filter(ChatBotService.BuildContent.class::isInstance).map(ChatBotService.BuildContent.class::cast).toList();
            int selected=ordinal(raw.toLowerCase(Locale.ROOT).replace('ё','е'));
            if(selected<0 || selected>=builds.size())return clarification("В предыдущем ответе нет такого варианта сборки. Назовите деталь или выберите показанный вариант.",request,List.of("Покажи варианты сборки"));
            var build=builds.get(selected);
            return new Result(new ChatBotService.Reply("Выбран вариант сборки "+(selected+1)+" из предыдущего ответа.","Выбранный вариант сборки.",
                    build.parts().stream().map(ChatBotService.ProductInfo::toProduct).toList(),List.of(build),request,List.of()),true);
        }
        var candidates=new LinkedHashMap<Integer,ChatBotService.ProductInfo>();
        if(previous!=null)for(var content:previous.content()) {
            if(content instanceof ChatBotService.ProductsContent p)p.items().forEach(item->candidates.putIfAbsent(item.id(),item));
            else if(content instanceof ChatBotService.BuildContent b)b.parts().forEach(item->candidates.putIfAbsent(item.id(),item));
            else if(content instanceof ChatBotService.SpecificationsContent s){candidates.putIfAbsent(s.left().id(),s.left());candidates.putIfAbsent(s.right().id(),s.right());}
            else if(content instanceof ChatBotService.PriceComparisonContent p){candidates.putIfAbsent(p.left().id(),p.left());candidates.putIfAbsent(p.right().id(),p.right());}
        }
        String text=raw.toLowerCase(Locale.ROOT).replace('ё','е');
        ComponentCategory category=category(text);
        var items=candidates.values().stream().filter(p->category==null || ComponentCategory.fromDisplayName(p.category()).orElse(null)==category).toList();
        if(items.isEmpty())return clarification("Нет актуальной карточки для такого уточнения. Сначала найдите товар или назовите точную модель.",request,List.of("Найди Ryzen"));
        if(text.matches(".*(?:другую|другой|другое|не эту|не этот|не первый|не второй).*"))
            return clarification("Назовите другую точную модель. Не буду выбирать её за вас.",request,List.of());
        int index=ordinal(text);
        if(index<0 && items.size()>1)return clarification("В предыдущем результате несколько товаров. Какой именно: назовите модель или номер в списке?",request,
                items.stream().limit(3).map(p->"Найди "+p.name()).toList());
        if(index>=items.size())return clarification("В показанном списке нет такого номера. Назовите модель или выберите один из показанных товаров.",request,
                items.stream().limit(3).map(p->"Найди "+p.name()).toList());
        var selected=items.get(Math.max(0,index));
        var format=NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));format.setMaximumFractionDigits(2);
        String answer=selected.name()+" — "+format.format(selected.price())+" ₽. "+(selected.stock()>0?"В наличии: "+selected.stock()+".":"Сейчас нет в наличии.")
                +" Цена и остаток взяты из текущей карточки каталога и могут измениться.";
        return new Result(new ChatBotService.Reply(answer,"Данные выбранного товара из каталога.",List.of(),
                List.of(new ChatBotService.ProductsContent(List.of(selected),1)),request,List.of()),true);
    }
    private static Result clarification(String text,ChatBotService.RequestSummary request,List<String> suggestions) {
        return new Result(new ChatBotService.Reply(text,text,List.of(),List.of(),request,suggestions),false);
    }
    private static int ordinal(String text) {
        var number=Pattern.compile("(?U)^(?:(?:а|тогда|давай|покажи|можно|хочу)\\s+)?([1-9]|10)[.!?]*$").matcher(text.strip());
        if(number.matches())return Integer.parseInt(number.group(1))-1;
        for(int i=0;i<ORDINALS.size();i++)if(Pattern.compile("(?U)\\b"+ORDINALS.get(i)+"\\p{L}*\\b").matcher(text).find())return i;
        return -1;
    }
    private static ComponentCategory category(String text) {
        if(text.matches(".*(?:процессор|\\bcpu\\b).*"))return ComponentCategory.CPU;
        if(text.matches(".*(?:видеокарт|\\bgpu\\b).*"))return ComponentCategory.GPU;
        if(text.matches(".*(?:оперативн|оперативк|\\bram\\b|озу).*"))return ComponentCategory.RAM;
        if(text.matches(".*(?:накопител|\\bssd\\b).*"))return ComponentCategory.STORAGE;
        if(text.matches(".*(?:материнск|плата|плату).*"))return ComponentCategory.MOTHERBOARD;
        if(text.matches(".*блок\\p{L}* питания.*"))return ComponentCategory.PSU;
        if(text.contains("корпус"))return ComponentCategory.CASE;
        if(text.matches(".*(?:кулер|охлажден).*"))return ComponentCategory.COOLER;
        return null;
    }
}
