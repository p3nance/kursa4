package com.example.authapp.services;

import org.json.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Model text is never rendered as a fact. Queries must be copied from the user's current message. */
record LocalChatPlan(String action, List<String> queries, BigDecimal budget, String budgetText, String usage,String budgetOperation) {
    private static final Set<String> KEYS = Set.of("action", "queries", "budget", "budgetText", "usage");
    private static final Set<String> EXTENDED_KEYS = Set.of("action", "queries", "budget", "budgetText", "usage","budgetOperation");
    private static final Pattern MONEY = Pattern.compile("(?iu)^\\d[\\d \\u202f\\u00a0]*(?:[.,]\\d+)?\\s*(?:(?:тыс(?:яч[аиу]?)?\\.?|[кk])(?:\\s*руб(?:лей|ля|ль)?)?|руб(?:лей|ля|ль)?|₽)$");
    static LocalChatPlan parse(String content) {
        try {
            var object = new JSONObject(content);
            if (!object.keySet().equals(KEYS) && !object.keySet().equals(EXTENDED_KEYS)) throw new IllegalArgumentException("Invalid plan keys");
            var queries = new ArrayList<String>();
            for (Object query : object.getJSONArray("queries")) {
                if (!(query instanceof String s)) throw new IllegalArgumentException("Invalid query");
                queries.add(s);
            }
            if (!object.isNull("budget") && !(object.get("budget") instanceof Number))
                throw new IllegalArgumentException("Invalid budget type");
            String action=object.getString("action"),usage=object.getString("usage"),operation=object.has("budgetOperation")?object.getString("budgetOperation"):"SET";
            if(!Set.of("KEEP","GAMING","OFFICE","WORKSTATION").contains(usage)||!Set.of("SET","ADD","SUBTRACT").contains(operation))throw new IllegalArgumentException("Unknown intent parameter");
            BigDecimal amount=object.isNull("budget")?null:object.getBigDecimal("budget");
            String evidence=object.isNull("budgetText")?null:object.getString("budgetText");
            // These intents have no budget mutation capability. Discard unused wire metadata explicitly.
            if(!action.equals("BUILD")){amount=null;evidence=null;operation="SET";if(!action.equals("CONTEXT"))usage="KEEP";}
            return new LocalChatPlan(action,List.copyOf(queries),amount,evidence,usage,operation);
        } catch (JSONException ex) { throw new IllegalArgumentException("Invalid plan", ex); }
    }
    String command(String raw) {
        if (!Set.of("LOCAL", "BUILD", "SEARCH", "COMPARE", "DEFINE", "CONTEXT", "EXPLAIN").contains(action)
                || !Set.of("KEEP", "GAMING", "OFFICE", "WORKSTATION").contains(usage) || !Set.of("SET","ADD","SUBTRACT").contains(budgetOperation))
            throw new IllegalArgumentException("Unknown intent");
        int count = switch (action) { case "SEARCH", "DEFINE" -> 1; case "COMPARE" -> 2; default -> 0; };
        if (queries.size() != count) throw new IllegalArgumentException("Invalid query count");
        for (String query : queries) {
            if (query.isBlank() || query.length() > 160 || !Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(query) + "(?![\\p{L}\\p{N}])").matcher(raw).find())
                throw new IllegalArgumentException("Query absent from user input");
        }
        if (!action.equals("BUILD") && (budget != null || budgetText != null || !budgetOperation.equals("SET") || !action.equals("CONTEXT") && !usage.equals("KEEP")))
            throw new IllegalArgumentException("Unexpected build fields");
        if (action.equals("BUILD")) {
            if(ChatBotService.invalidNegativeBudget(raw))throw new IllegalArgumentException("Negative budget");
            boolean add=operationMentioned(raw,"ADD"),subtract=operationMentioned(raw,"SUBTRACT");
            if(ambiguousMoneyRequest(raw))
                throw new InputProblem("Уточните изменение: до какой суммы или на какую сумму изменить бюджет?");
            if(operationCount(raw)>1)throw new InputProblem("Укажите одно изменение бюджета за сообщение или назовите итоговую сумму.");
            if((add || subtract) && budgetOperation.equals("SET"))throw new InputProblem("Назовите итоговый бюджет в рублях или явно укажите, сколько прибавить или вычесть.");
            if(!budgetOperation.equals("SET") && (!operationMentioned(raw,budgetOperation) || budget==null))throw new IllegalArgumentException("Budget operation absent from input");
            if(!budgetOperation.equals("SET") && Pattern.compile("(?iuU)не\\s+(?:(?:надо|нужно)\\s+)?(?:увелич\\p{L}*|подним\\p{L}*|добав\\p{L}*|прибав\\p{L}*|убав\\p{L}*|вычт\\p{L}*|отним\\p{L}*|уменьш\\p{L}*|сниз\\p{L}*|сократ\\p{L}*)").matcher(raw).find())
                throw new InputProblem("Бюджет не изменён. Если нужна другая сумма, назовите её явно.");
            if ((budget == null) != (budgetText == null)) throw new IllegalArgumentException("Missing budget evidence");
            if (budget != null) {
                if (budget.signum() <= 0 || budget.compareTo(new BigDecimal("100000000")) > 0
                        || budgetText.isBlank() || budgetText.length() > 80 || !raw.contains(budgetText))
                    throw new IllegalArgumentException("Invalid budget evidence");
                if(budget.stripTrailingZeros().scale()>2)throw new InputProblem("Укажите сумму с точностью до копейки.");
                var known = ChatBotService.budgetFrom(raw);
                if (budgetOperation.equals("SET") && known.isPresent() && known.get().compareTo(budget) != 0)
                    throw new IllegalArgumentException("Budget changed by model");
                if(!moneyQuoteMatches(raw,budgetText,budget,budgetOperation.equals("SET") && known.isPresent()))
                    throw new IllegalArgumentException("Budget conversion mismatch");
                if(!budgetOperation.equals("SET") && operationExpressions(budgetOperation).stream().noneMatch(p->{
                    var matcher=p.matcher(raw);while(matcher.find())if(quotePattern(budgetText).matcher(matcher.group()).find())return true;
                    return false;
                }))throw new IllegalArgumentException("Delta evidence is outside the operation");
            }
            String purpose = switch (usage) {
                case "GAMING" -> " для игр"; case "OFFICE" -> " для офиса";
                case "WORKSTATION" -> " для рабочих задач"; default -> "";
            };
            // Keep the complete original message so existing constraints/negation/ownership guards still run.
            return "Собери ПК" + (budget == null || !budgetOperation.equals("SET") ? "" : " до " + budget.toPlainString() + " рублей") + purpose + "; " + raw;
        }
        return switch (action) {
            case "SEARCH" -> "Найди " + queries.getFirst();
            case "DEFINE" -> "Что такое " + queries.getFirst();
            case "COMPARE" -> "Сравни " + queries.getFirst() + " и " + queries.getLast();
            case "CONTEXT" -> raw;
            case "EXPLAIN" -> raw;
            default -> null;
        };
    }
    static boolean relativeMoneyRequest(String raw) {
        return ambiguousMoneyRequest(raw) || operationMentioned(raw,"ADD") || operationMentioned(raw,"SUBTRACT");
    }
    private static boolean ambiguousMoneyRequest(String raw) {
        return Pattern.compile("(?iuU)(?:увелич\\p{L}*|подним\\p{L}*|уменьш\\p{L}*|сниз\\p{L}*|сократ\\p{L}*)\\s+(?:(?:бюджет|сумму|лимит)\\s+)?\\d[\\d .,]*\\s*(?:тыс\\p{L}*|руб\\p{L}*|₽|[кk](?![\\p{L}]))").matcher(raw).find();
    }
    private static boolean operationMentioned(String raw,String operation) {
        return operationExpressions(operation).stream().anyMatch(p->p.matcher(raw).find());
    }
    private static int operationCount(String raw) {
        var ranges=new ArrayList<int[]>();
        for(String operation:List.of("ADD","SUBTRACT"))for(var expression:operationExpressions(operation)) {
            var matcher=expression.matcher(raw);while(matcher.find())ranges.add(new int[]{matcher.start(),matcher.end()});
        }
        ranges.sort(Comparator.comparingInt(r->r[0]));int count=0,end=-1;
        for(var range:ranges){if(range[0]>=end)count++;end=Math.max(end,range[1]);}
        return count;
    }
    static boolean hasTaskInformation(String raw) {
        String words=raw.toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("[^\\p{L}]+"," ").strip();
        String budgetWord="(?:собери|собрать|подбери|подобрать|пк|компьютер\\p{L}*|системник\\p{L}*|машинк\\p{L}*|нуж\\p{L}*|хоч\\p{L}*|"
                +"на|до|к|из|за|в|и|но|а|для|мой|мои|у|меня|мне|его|теперь|нов\\p{L}*|стар\\p{L}*|прежн\\p{L}*|"
                +"бюджет\\p{L}*|сумм\\p{L}*|лимит\\p{L}*|предел\\p{L}*|потолок\\p{L}*|расход\\p{L}*|трат\\p{L}*|покуп\\p{L}*|"
                +"увелич\\p{L}*|повыш\\p{L}*|подним\\p{L}*|добав\\p{L}*|прибав\\p{L}*|уменьш\\p{L}*|сниз\\p{L}*|сниж\\p{L}*|убав\\p{L}*|вычт\\p{L}*|отним\\p{L}*|"
                +"могу|можно|получ\\p{L}*|выдел\\p{L}*|располага\\p{L}*|руб\\p{L}*|тыс\\p{L}*|к|k|еще|дополнительно|больше|меньше|"
                +"пожалуйста|пожалуй|давай|тогда|просто|опять|всего|сейчас|остав\\p{L}*)";
        return !words.isEmpty() && Arrays.stream(words.split("\\s+")).anyMatch(w->!w.matches(budgetWord));
    }
    private static List<Pattern> operationExpressions(String operation) {
        String amount="\\d[\\d .,]*\\s*(?:тыс\\p{L}*|руб\\p{L}*|₽|[кk](?![\\p{L}]))";
        String verbs=operation.equals("ADD")?"прибав\\p{L}*|добав\\p{L}*|еще|ещё|дополнительно":"убав\\p{L}*|вычт\\p{L}*|отним\\p{L}*";
        String directed=operation.equals("ADD")?"увелич\\p{L}*|подним\\p{L}*":"уменьш\\p{L}*|сниз\\p{L}*|сниж\\p{L}*|сократ\\p{L}*";
        String subject="(?:(?:к\\s+)?(?:бюджет|бюджету|сумму|сумме|лимит|лимиту|расходы|его)\\s+)?";
        return List.of(Pattern.compile("(?iuU)(?:"+verbs+")\\s+"+subject+"(?:на\\s+)?"+amount),
                Pattern.compile("(?iuU)(?:"+directed+")\\s+"+subject+"на\\s+"+amount),
                Pattern.compile("(?iuU)на\\s+"+amount+"\\s+"+(operation.equals("ADD")?"больше":"меньше")));
    }
    private static boolean moneyQuoteMatches(String raw,String quote,BigDecimal value,boolean allowBare) {
        var occurrence=quotePattern(quote).matcher(raw);
        while(occurrence.find()) {
            String span=quote;int end=occurrence.end();
            if(!MONEY.matcher(span).matches()) {
                if(!span.matches("[0-9][0-9 \\u202f\\u00a0]*(?:[.,][0-9]+)?"))continue;
                var unit=Pattern.compile("(?iuU)^\\s*(?:(?:тыс(?:яч[аиу]?)?\\.?|[кk])(?:\\s*руб(?:лей|ля|ль)?)?|руб(?:лей|ля|ль)?|₽)(?![\\p{L}\\p{N}])").matcher(raw.substring(end));
                if(unit.find()){span+=unit.group();end+=unit.end();}
                else if(!allowBare)continue;
            }
            if(span.strip().matches("(?iu).*?[кk]") && raw.substring(end).matches("(?iuU)^\\s+(?:бюджету|сумме|лимиту)\\b.*"))continue;
            if(ChatBotService.budgetFrom("до "+span).filter(b->b.compareTo(value)==0).isPresent())return true;
        }
        return false;
    }
    private static Pattern quotePattern(String quote) {
        boolean endsWithDigit=Character.isDigit(quote.charAt(quote.length()-1));
        return Pattern.compile("(?iuU)(?<![\\p{L}\\p{N}])(?<![0-9][.,])"+Pattern.quote(quote)
                +(endsWithDigit?"(?![0-9]|[.,][0-9])":"(?![\\p{L}\\p{N}])"));
    }
    BigDecimal effectiveBudget(BigDecimal current) {
        if(budgetOperation.equals("SET"))return budget;
        if(current==null)throw new InputProblem("Для относительного изменения нужен прежний бюджет. Назовите итоговую сумму в рублях.");
        BigDecimal result=budgetOperation.equals("ADD")?current.add(budget):current.subtract(budget);
        if(result.signum()<=0 || result.compareTo(new BigDecimal("100000000"))>0)throw new InputProblem("После изменения бюджет должен быть больше нуля и не более 100 млн рублей. Укажите другую сумму.");
        return result;
    }
    static final class InputProblem extends IllegalArgumentException {InputProblem(String message){super(message);}}
}
