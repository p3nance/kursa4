package com.example.authapp.services;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Small explicit intent grammar; model identity is resolved separately against the current catalog. */
public final class ChatIntentParser {
    public enum Intent { RESET, GREETING, THANKS, COMPARE, COMPARE_HELP, SEARCH, DEFINE, BUILD, OTHER }
    public record Parsed(Intent intent,String body,List<String> pair) {
        public Parsed {Objects.requireNonNull(intent);body=Objects.requireNonNullElse(body,"");pair=List.copyOf(pair);}
    }
    private static final int FLAGS=Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE;
    private static final String COURTESY="(?:(?:привет|здравствуйте)[,!:]?\\s+)?(?:(?:пожалуйста|можешь|можете|можно|мог бы|не мог бы|подскажи|подскажите)\\s+)*";
    private static final Pattern COMPARE_PREFIX=p("^"+COURTESY+"(?:сравни(?:ть|те)?|сравнение(?:\\s+между)?|сопоставь(?:те)?|покажи(?:те)?\\s+характеристики|показать\\s+характеристики|чем\\s+отлича(?:ется|ются)|в\\s+ч[её]м\\s+разница(?:\\s+между)?|что\\s+(?:лучше|выбрать)|(?:како[йе]|какая)\\s+(?:лучше|выбрать))\\s*[,+:—-]?\\s*(?:пожалуйста[, ]+)?");
    private static final Pattern SEARCH_PREFIX=p("^"+COURTESY+"(?:найди(?:те)?|найти|покажи(?:те)?|показать|ищу|поиск|есть\\s+ли(?:\\s+у\\s+вас)?|есть\\s+у\\s+вас)\\s+");
    private static final Pattern DEFINE_PREFIX=p("^"+COURTESY+"(?:что\\s+(?:такое|значит|означает)|объясни(?:те|ть)?|расскажи(?:те)?\\s+(?:про|о)|дай\\s+определение)\\s+");
    private static final Pattern DIVIDER=p("\\s+(?:и|или|с|против|от)\\s+|\\s*\\bvs\\b\\s*|(?<=[\\p{L}\\p{N}])vs(?=[\\p{L}\\p{N}])|\\s*;\\s*");
    private static final Pattern PURPOSE=p("(?:^|\\s+)(?:для\\s+(?:игр|работы|рендеринга|рендера|офиса)|в\\s+играх)(?=\\s|$)");

    public Parsed parse(String raw) {
        String text=clean(raw),lower=text.toLowerCase(Locale.ROOT).replace('ё','е');
        if(lower.matches("(?:сброс|сбросить|заново|начать заново|начнем заново|очистить|очисти(?: чат)?|новый диалог)[.!?]*")) return parsed(Intent.RESET,text);
        if(lower.matches("(?:привет|здравствуйте|добрый день|добрый вечер|доброе утро|помощь|что ты умеешь|help)[.!?]*")) return parsed(Intent.GREETING,text);
        if(lower.matches("(?:спасибо|благодарю|спс)[.!?]*")) return parsed(Intent.THANKS,text);
        if(lower.matches(".*(?:как\\s+(?:можно\\s+)?сравни(?:ть|вать)|что\\s+такое\\s+бенчмарк).*")) {
            if(!text.matches(".*\\d.*")) return parsed(Intent.COMPARE_HELP,text);
        }
        if(lower.matches("(?:покажи|показать|выбрать|выбери) модели с общим тестом[.!?]*"))return parsed(Intent.OTHER,text);
        if(lower.matches("(?:сравни(?:ть)?|покажи|показать)(?: по)? бенчмарк(?:и|ам)?[.!?]*"))return parsed(Intent.OTHER,text);
        var compare=COMPARE_PREFIX.matcher(text);
        if(compare.find()) return comparison(text.substring(compare.end()));
        var search=SEARCH_PREFIX.matcher(text);
        if(search.find()) return parsed(Intent.SEARCH,trim(text.substring(search.end())));
        var define=DEFINE_PREFIX.matcher(text);
        if(define.find()) return parsed(Intent.DEFINE,trim(text.substring(define.end())));
        if(lower.matches(".*(?:собери|собрать|соберите|подбери|подобрать|подберите|сборк[ауи]|хочу\\s+(?:пк|компьютер)|нужен\\s+(?:пк|компьютер)).*")) return parsed(Intent.BUILD,text);
        if(DIVIDER.matcher(text).find()) {
            String[] parts=DIVIDER.split(text,2);
            // A conjunction in a purpose answer (e.g. "для учёбы и офиса") is not a comparison intent.
            if(parts.length==2 && parts[0].matches(".*\\d.*") && parts[1].matches(".*\\d.*")) return comparison(text);
        }
        if(lower.matches(".*(?:бенчмарк|benchmark).*")) return parsed(Intent.COMPARE_HELP,text);
        return parsed(Intent.OTHER,text);
    }
    private static Parsed comparison(String text) {
        String body=trim(text);
        String[] parts=DIVIDER.split(body,2);
        var pair=new ArrayList<String>();
        for(String part:parts) {
            String model=trim(PURPOSE.matcher(part).replaceAll(" "));
            model=model.replaceFirst("(?iu)^(?:(?:между|из|пожалуйста|характеристики)\\s+)+","");
            if(!model.isBlank()) pair.add(model);
        }
        return new Parsed(Intent.COMPARE,body,pair);
    }
    private static Parsed parsed(Intent intent,String body) {return new Parsed(intent,body,List.of());}
    private static Pattern p(String expression) {return Pattern.compile(expression,FLAGS);}
    private static String clean(String value) {
        if(value==null) return "";
        return Normalizer.normalize(value,Normalizer.Form.NFKC).replaceAll("(?U)\\s+"," ").strip();
    }
    private static String trim(String value) {return value.strip().replaceAll("^[,:;!?]+\\s*|[,:;!?]+$","").strip();}
}
