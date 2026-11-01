package com.example.authapp.services;

import java.util.Optional;
import java.util.regex.Pattern;

/** Deterministic clarification for unsupported money syntax; no model failure is implied. */
final class ChatBudgetInput {
    private static final Pattern FRAME=Pattern.compile("(?iuU)(?:добав\\p{L}*|прибав\\p{L}*|увелич\\p{L}*|подним\\p{L}*|уменьш\\p{L}*|сниз\\p{L}*|сократ\\p{L}*|вычт\\p{L}*|отним\\p{L}*)\\s+(?:(?:к\\s+)?(?:бюджет\\p{L}*|сумм\\p{L}*|лимит\\p{L}*)\\s+)?(?:на\\s+|до\\s+)?\\d");
    private static final Pattern UNIT=Pattern.compile("(?iuU)\\d[\\d .,]*\\s*(?:(?:тыс(?:яч[аиу]?)?\\.?|[кk])(?:\\s*руб(?:лей|ля|ль)?)?|руб(?:лей|ля|ль)?|₽)(?![\\p{L}\\p{N}])");
    static Optional<String> problem(String raw) {
        if(raw==null || !FRAME.matcher(raw).find())return Optional.empty();
        if(!Pattern.compile("(?iuU)бюджет|сумм|лимит").matcher(raw).find()
                && Pattern.compile("(?iuU)(?:\\d\\s*(?:гб|gb|тб|tb|гига|терабайт)|озу|оператив|ssd|ссд)").matcher(raw).find())return Optional.empty();
        if(raw.contains("%"))return Optional.of("Изменение бюджета в процентах пока не поддерживается. Укажите сумму в рублях: например «Добавь 10 тысяч» или итоговый бюджет.");
        if(Pattern.compile("(?iuU)(?:доллар|\\busd\\b|\\beur\\b|евро|[$€])").matcher(raw).find())
            return Optional.of("Бюджет подбора ведётся в рублях. Укажите сумму изменения или итоговую сумму в рублях.");
        if(Pattern.compile("(?iuU)\\d[\\d .,]*\\s+к\\s+(?:бюджету|сумме|лимиту)\\b").matcher(raw).find())
            return Optional.of("Укажите денежную единицу: например «Добавь 10 рублей к бюджету» или «Добавь 10 тысяч к бюджету».");
        if(!UNIT.matcher(raw).find())return Optional.of("Укажите денежную единицу и сумму, например «Добавь 10 тысяч рублей».");
        return Optional.empty();
    }
}
