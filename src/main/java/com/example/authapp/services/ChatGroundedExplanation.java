package com.example.authapp.services;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;

/** Explains immutable typed evidence. The model selects this action but supplies no factual prose. */
final class ChatGroundedExplanation {
    static Optional<ChatBotService.Reply> explain(ChatBotService.Reply previous,ChatBotService.RequestSummary request) {
        if(previous==null || previous.content().isEmpty())return Optional.empty();
        var lines=new ArrayList<String>();
        for(var content:previous.content()) {
            if(content instanceof ChatBotService.BuildContent build) {
                lines.add("Основания предложенной сборки:");
                lines.add("К покупке: "+money(build.total())+" при бюджете "+money(build.budget())+". Имеющиеся детали в эту сумму не входят.");
                lines.add("Состав подбирается из текущего каталога с учётом задачи, бюджета и заданных минимумов. Цена не считается показателем FPS или скорости приложений.");
                lines.add(build.verified()?"Доступные проверки совместимости пройдены.":"Совместимость не подтверждена полностью; неизвестные характеристики нужно проверить до покупки.");
                build.limitations().stream().distinct().limit(5).forEach(lines::add);
                build.findings().stream().distinct().limit(5).forEach(lines::add);
            } else if(content instanceof ChatBotService.TermContent term) {
                lines.add(term.term().term()+": "+term.term().simpleExplanation());lines.add("Источник: "+term.term().source());
            } else if(content instanceof ChatBotService.ProductsContent products) {
                lines.add("Это результаты поиска по текущему каталогу. Цена и наличие взяты из карточек, а не придуманы моделью.");
                products.items().stream().limit(3).forEach(p->lines.add(p.name()+" — "+money(p.price())));
                lines.add("Более высокая цена сама по себе не доказывает большую производительность. Для вывода нужны сопоставимые характеристики и тесты конкретных моделей.");
            } else if(content instanceof ChatBotService.PriceComparisonContent prices) {
                lines.add(prices.left().name()+" — "+money(prices.left().price())+"; "+prices.right().name()+" — "+money(prices.right().price())+".");
                lines.add("Разница цен: "+money(prices.difference())+". Это разность цен каталога, не показатель производительности.");
            } else if(content instanceof ChatBotService.SpecificationsContent specs) {
                lines.add("Сравнение относится к конкретным моделям: "+specs.left().name()+" и "+specs.right().name()+".");
                lines.add("Показаны подтверждённые характеристики из источников карточки. Неизвестное значение не считается подтверждением совместимости.");
                specs.limitations().stream().limit(5).forEach(lines::add);specs.sources().stream().limit(3).forEach(s->lines.add("Источник: "+s.uri()));
            } else if(content instanceof ChatBotService.BenchmarksContent) {
                lines.add("Это отдельные измерения в условиях, указанных в карточке. Их нельзя выдавать за FPS всей будущей сборки или переносить на другую игру, настройки и разрешение.");
                lines.add(previous.text());
            }
        }
        if(lines.isEmpty())return Optional.empty();
        String text=String.join("\n",new LinkedHashSet<>(lines));
        return Optional.of(new ChatBotService.Reply(text,text,List.of(),List.of(),request,List.of("Собери ПК","Что такое совместимость?")));
    }
    private static String money(BigDecimal value) {
        var format=NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));format.setMaximumFractionDigits(2);return format.format(value)+" ₽";
    }
}
