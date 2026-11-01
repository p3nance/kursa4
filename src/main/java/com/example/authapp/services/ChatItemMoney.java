package com.example.authapp.services;

import java.util.Optional;
import java.util.regex.Pattern;

/** An amount explicitly earmarked for one part cannot silently replace the whole-build budget. */
final class ChatItemMoney {
    private static final String PART="(?:процессор\\p{L}*|видеокарт\\p{L}*|ssd|ссд|озу|ram|накопител\\p{L}*|кулер\\p{L}*|корпус\\p{L}*|материнск\\p{L}*\\s+плат\\p{L}*)";
    private static final Pattern MONEY=Pattern.compile("(?iuU)(?<![\\p{L}\\p{N}])\\d[\\d .,]*\\s*(?:тыс\\p{L}*|руб\\p{L}*|₽)(?![\\p{L}\\p{N}])");
    private static final Pattern TARGET=Pattern.compile("(?iuU)\\b(?:на|для)\\s+(?:(?:этот|новый)\\s+)?"+PART+"\\b|\\b"+PART+".{0,30}\\bза\\s+\\d");
    static Optional<String> problem(String raw) {
        if(raw==null || ChatCatalogFilter.hasPriceSyntax(raw) || !MONEY.matcher(raw).find() || !TARGET.matcher(raw).find())return Optional.empty();
        if(Pattern.compile("(?iuU)\\b(?:пк|компьютер\\p{L}*|системн\\p{L}*|сборк\\p{L}*|собери\\p{L}*|собрать)\\b").matcher(raw).find())return Optional.empty();
        if(OwnedBuildConversation.isCommand(raw) && !Pattern.compile("(?iuU)^у меня (?:уже )?есть\\s+\\d").matcher(raw.strip()).find())return Optional.empty();
        return Optional.of("Сумма на отдельную деталь не меняет бюджет ПК. Укажите предел цены товара, например «Найди процессоры до 20 тысяч рублей» или «Найди SSD до 15 тысяч рублей».");
    }
}
