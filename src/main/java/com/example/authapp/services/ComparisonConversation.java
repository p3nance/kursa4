package com.example.authapp.services;

import com.example.authapp.domain.*;
import com.example.authapp.models.Product;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded rule-based comparison dialogue. All choices are explicit catalog selections. */
final class ComparisonConversation {
    private final LocalKnowledgeRepository.Snapshot knowledge;
    private final ModelResolver resolver;
    private final ComparisonService responses;
    private CatalogComponent left,right;
    private String leftQuery="",rightQuery="",purpose="",replacement="",taskDetails="";
    private int awaiting=-1; // 0/1: model choice; 2: replacement target
    private List<CatalogComponent> options=List.of();
    private boolean invalidated;
    private boolean specsMode;
    private boolean awaitingTaskDetails;

    ComparisonConversation(LocalKnowledgeRepository.Snapshot knowledge,Collection<Product> products) {
        this.knowledge=knowledge;resolver=new ModelResolver(knowledge.components());responses=new ComparisonService(knowledge,products);
    }
    ComparisonConversation copy() {
        return copyWithServices();
    }
    private ComparisonConversation(ComparisonConversation source) {
        knowledge=source.knowledge;resolver=source.resolver;responses=source.responses;copyState(source);
    }
    private ComparisonConversation copyWithServices() { return new ComparisonConversation(this); }
    private void copyState(ComparisonConversation source) {
        left=source.left;right=source.right;leftQuery=source.leftQuery;rightQuery=source.rightQuery;
        purpose=source.purpose;replacement=source.replacement;taskDetails=source.taskDetails;awaiting=source.awaiting;options=source.options;invalidated=source.invalidated;specsMode=source.specsMode;awaitingTaskDetails=source.awaitingTaskDetails;
    }
    void commit(ComparisonConversation draft) { copyState(draft);rebind(); }
    ComparisonConversation refreshed(LocalKnowledgeRepository.Snapshot updated,Collection<Product> products) {
        var next=new ComparisonConversation(updated,products);next.copyState(this);next.rebind();return next;
    }
    private void rebind() {
        var before=new ArrayList<CatalogComponent>();if(left!=null)before.add(left);if(right!=null)before.add(right);before.addAll(options);
        if(before.stream().anyMatch(c->current(c)==null)) {clear();invalidated=true;return;}
        left=current(left);right=current(right);options=options.stream().map(this::current).toList();
    }
    private CatalogComponent current(CatalogComponent previous) {
        if(previous==null)return null;
        return knowledge.components().stream().filter(c->c.productId()==previous.productId() && c.category()==previous.category()
                && ModelResolver.normalize(c.name()).equals(ModelResolver.normalize(previous.name()))).findFirst().orElse(null);
    }
    void clear() {left=null;right=null;leftQuery="";rightQuery="";purpose="";replacement="";taskDetails="";awaiting=-1;options=List.of();invalidated=false;specsMode=false;awaitingTaskDetails=false;}

    Optional<ChatBotService.Reply> respond(String raw,ChatIntentParser.Parsed parsed) {
        String text=ModelResolver.normalize(raw);
        if(parsed.intent()==ChatIntentParser.Intent.COMPARE_HELP) return Optional.of(responses.help());
        // A goal-first follow-up describes the current pair; a divider inside its scenario is not a new pair.
        if((parsed.intent()==ChatIntentParser.Intent.OTHER || parsed.intent()==ChatIntentParser.Intent.COMPARE) && startsGamingGoal(raw)) {
            if(complete()) {
                setPurposeFrom(raw);
                return Optional.of(taskDetails.isBlank()?finish(false):acceptTaskDetails(taskDetails));
            }
            if(awaiting==0 || awaiting==1) {setPurposeFrom(raw);return Optional.of(askModel(awaiting));}
        }
        String modelRaw=raw;
        if(parsed.intent()==ChatIntentParser.Intent.OTHER || parsed.intent()==ChatIntentParser.Intent.COMPARE) {
            var marker=EXPLICIT_GAMING_GOAL.matcher(raw);
            if(marker.find() && marker.start()>0) {
                modelRaw=raw.substring(0,marker.start()).strip();
                parsed=new ChatIntentParser().parse(modelRaw);
            }
        }
        // Scenario follow-ups remain meaningful after the first clarification was answered.
        // Only an explicit scenario form may precede model mentions: a GPU inside a game
        // scenario is a condition, while "сравни ..." still starts a new comparison.
        if(complete() && purpose.equals("Для игр") && isGamingScenarioFollowUp(raw,parsed))
            return Optional.of(acceptTaskDetails(continuedGamingDetails(raw)));
        // Fully explicit new model requests take precedence over a pending choice.
        if(parsed.intent()==ChatIntentParser.Intent.COMPARE && parsed.pair().size()==2)
            return Optional.of(begin(parsed.pair().get(0),parsed.pair().get(1),raw));
        var explicitModels=resolver.mentions(modelRaw);
        if((parsed.intent()==ChatIntentParser.Intent.OTHER || parsed.intent()==ChatIntentParser.Intent.COMPARE) && explicitModels.size()==2)
            return Optional.of(begin(explicitModels.get(0).name(),explicitModels.get(1).name(),raw));
        if(text.matches("(?:покажи|показать|выбрать|выбери) модели с общим тестом.*")) {
            var examples=responses.comparablePairSuggestions(left==null?null:left.category());
            return Optional.of(reply(examples.isEmpty()?"Для выбранной категории пока нет пары с общим тестом. Могу сравнить характеристики двух моделей.":
                    "Вот пары из каталога с общим тестом. Выберите запрос ниже.",examples));
        }
        if(text.matches("(?:сравни(?:ть)?|покажи|показать) характеристики.*"))
            return Optional.of(complete()?finish(true):reply("Какие две модели сравнить по характеристикам? Назовите их или выберите модели в каталоге.",List.of()));
        if(text.matches("(?:сравни(?:ть)?|покажи|показать)(?: по)? бенчмарки?.*")) {
            if(!complete())return Optional.of(reply("Какие две модели сравнить по бенчмаркам?",responses.comparablePairSuggestions(null)));
            specsMode=false;return Optional.of(finish(false));
        }
        if(awaiting>=0 && parsed.intent()==ChatIntentParser.Intent.COMPARE
                && !text.matches(".*(?:сравни(?:ть)?|сравнение) (?:с|со) (?:первым|вторым|первой|второй).*")) {
            String query=parsed.pair().isEmpty()?parsed.body():parsed.pair().getFirst();
            return Optional.of(begin(query,"",raw));
        }
        if(awaiting==2) {
            int target=target(text);
            if(target<0 && left!=null && text.equals(ModelResolver.normalize("Заменить "+left.name())))target=0;
            if(target<0 && right!=null && text.equals(ModelResolver.normalize("Заменить "+right.name())))target=1;
            if(target<0)return Optional.of(replacementTarget());
            return Optional.of(replace(target,replacement));
        }
        if(awaiting>=0 && parsed.intent()!=ChatIntentParser.Intent.COMPARE && goal(raw)!=null) {
            setPurposeFrom(raw);
            return Optional.of(startsGamingGoal(raw) || resolver.mentions(raw).isEmpty()?askModel(awaiting):choose(cleanQuery(raw)));
        }
        if(awaiting>=0 && parsed.intent()!=ChatIntentParser.Intent.COMPARE)
            return Optional.of(choose(raw));
        boolean related=parsed.intent()==ChatIntentParser.Intent.OTHER || parsed.intent()==ChatIntentParser.Intent.COMPARE;
        if(!related) return Optional.empty();
        if(text.matches(".*(?:вместо|замен(?:и|ить|ить бы)).*")) {
            if(!complete())return Optional.of(reply("Сначала назовите две модели для сравнения, затем можно заменить одну из них.",List.of()));
            String query=raw.replaceFirst("(?iu)^.*?(?:вместо\\s+(?:него|неё|нее|первого|второго|первой|второй)|замени(?:ть)?\\s+(?:первую|вторую|первый|второй|его|её|ее))\\s*","")
                    .replaceAll("(?iu)^(?:а\\s+)?(?:на\\s+)","").replaceAll("[?!.,]+$","").trim();
            var found=resolver.mentions(query);
            if(found.size()==1)query=found.getFirst().name();
            if(query.isBlank() || ModelResolver.normalize(query).equals(text))
                return Optional.of(reply("Какую новую модель хотите рассмотреть? Например: «вместо первого 14700K».",List.of()));
            int target=target(text);
            if(target>=0)return Optional.of(replace(target,query));
            replacement=query;awaiting=2;return Optional.of(replacementTarget());
        }
        if(text.matches(".*(?:сравни(?:ть)?|сравнение) (?:с|со) (?:первым|вторым|первой|второй).*")) {
            if(!complete())return Optional.of(reply("Не вижу прежней пары моделей. Назовите обе модели для сравнения.",List.of()));
            int target=target(text);
            var mentioned=resolver.mentions(raw);
            if(!mentioned.isEmpty())return Optional.of(begin(mentioned.getFirst().name(),target==0?left.name():right.name(),raw));
            CatalogComponent anchor=target==0?left:right;
            left=null;right=anchor;leftQuery="";rightQuery=anchor.name();awaiting=0;options=List.of();
            return Optional.of(reply("С какой моделью сравнить «"+anchor.name()+"»?",List.of()));
        }
        var mentioned=resolver.mentions(modelRaw);
        if(mentioned.size()>2)return Optional.of(reply("Вижу больше двух моделей. Назовите одну пару, которую нужно сравнить.",List.of()));
        if(mentioned.size()==2)return Optional.of(begin(mentioned.get(0).name(),mentioned.get(1).name(),raw));
        if(parsed.intent()==ChatIntentParser.Intent.COMPARE) {
            String query=parsed.pair().isEmpty()?parsed.body():parsed.pair().getFirst();
            if(query.isBlank() || (goal(raw)!=null && mentioned.isEmpty() && !hasHardwareToken(raw))
                    || query.matches("(?iu).*(?:комплектующ|модел[ьи]|видеокарт[ыа]|процессор[ыа]).*")) {
                clear();return Optional.of(reply("Назовите две модели: например, «14700K vs 14600K». Можно писать сокращённо.",responses.comparablePairSuggestions(null)));
            }
            return Optional.of(begin(query,"",raw));
        }
        if(complete() && awaitingTaskDetails && parsed.intent()==ChatIntentParser.Intent.OTHER && goal(raw)==null)
            return Optional.of(acceptTaskDetails(raw));
        if(complete() && (text.matches("^(?:а |тогда |давай ).*") || goal(raw)!=null)) {
            var next=resolver.resolve(cleanQuery(raw));
            if(next.status()!=ModelResolver.Status.NOT_FOUND && !(awaitingTaskDetails && goal(raw)==null
                    && next.candidates().stream().allMatch(c->c.category()!=left.category()))) {
                if(goal(raw)!=null)setPurposeFrom(raw);
                replacement=cleanQuery(raw);awaiting=2;return Optional.of(replacementTarget());
            }
        }
        if(complete() && goal(raw)!=null && mentioned.isEmpty()) {
            if(hasHardwareToken(raw))return Optional.of(reply("Не распознал новую модель в запросе. Уточните её название; прежняя пара сохранена.",List.of()));
            setPurposeFrom(raw);return Optional.of(finish(false));
        }
        if(invalidated && (text.matches(".*(?:для игр|для работы|перв|втор|вместо).*"))) {
            invalidated=false;return Optional.of(reply("Каталог изменился, прежний выбор моделей устарел. Назовите новую пару.",List.of()));
        }
        return Optional.empty();
    }
    private ChatBotService.Reply acceptTaskDetails(String raw) {
        awaitingTaskDetails=false;
        taskDetails=raw.length()>300?raw.substring(0,300)+"…":raw;
        String ack="Учёл уточнение: «"+taskDetails+"». ";
        if(purpose.equals("Для игр")) {
            var answer=responses.compare(left,right,specsMode,purpose+"\nУточнение: "+taskDetails).reply();
            return new ChatBotService.Reply(ack+answer.text(),ack+answer.lead(),List.of(),answer.content(),null,answer.suggestions());
        }
        return reply(ack+"Продолжаем сравнивать «"+left.name()+"» и «"+right.name()+"». В текущем наборе есть отдельные тесты рендеринга; они не описывают скорость всех программ и всей сборки.",
                List.of("Сравнить характеристики","Показать модели с общим тестом"));
    }
    private ChatBotService.Reply begin(String first,String second,String raw) {
        clear();specsMode=ModelResolver.normalize(raw).matches(".*(?:характеристик|по цене).*");leftQuery=cleanQuery(first);rightQuery=cleanQuery(second);setPurposeFrom(raw);
        return resolve(0,leftQuery);
    }
    private ChatBotService.Reply resolve(int slot,String query) {
        awaiting=slot;options=List.of();
        if(query.isBlank())return askModel(slot);
        var resolution=resolver.resolve(query);
        if(resolution.status()==ModelResolver.Status.EXACT && resolution.candidates().size()==1)
            return selected(slot,resolution.candidates().getFirst());
        options=List.copyOf(resolution.candidates());
        if(options.isEmpty())return reply("Модель «"+query+"» не найдена в текущем каталоге. Уточните название "+(slot==0?"первой":"второй")+" модели.",List.of());
        return choices(resolution.status()==ModelResolver.Status.SUGGESTED?
                "Возможно, вы имели в виду одну из этих моделей? Подтвердите выбор; автоматически заменять название не буду.":
                "Есть несколько вариантов. Какую модель вы имеете в виду?");
    }
    private ChatBotService.Reply selected(int slot,CatalogComponent component) {
        options=List.of();
        if(slot==0) {left=component;leftQuery=component.name();return right==null?resolve(1,rightQuery):finish(false);}
        right=component;rightQuery=component.name();return left==null?resolve(0,leftQuery):finish(false);
    }
    private ChatBotService.Reply choose(String raw) {
        String normalized=ModelResolver.normalize(raw);int index=ordinal(normalized);
        if(!options.isEmpty()) {
            if(index>=0 && index<Math.min(3,options.size()))return selected(awaiting,options.get(index));
            if(options.size()==1 && normalized.matches("(?:да|верно|подтверждаю|она|он|именно)"))return selected(awaiting,options.getFirst());
            var exact=options.stream().filter(c->ModelResolver.normalize(c.name()).equals(normalized)).toList();
            if(exact.size()==1)return selected(awaiting,exact.getFirst());
            if(normalized.matches("(?:8|16|24|32|64)\\s*(?:gb|гб)?")) {
                String capacity=normalized.replaceAll("[^0-9]","");
                var candidates=options.stream().filter(c->Pattern.compile("(?iu)(?<!\\d)"+capacity+"\\s*(?:gb|гб)").matcher(c.name()).find()).toList();
                if(candidates.size()==1)return selected(awaiting,candidates.getFirst());
            }
        }
        return resolve(awaiting,raw);
    }
    private ChatBotService.Reply replace(int target,String query) {
        replacement="";options=List.of();
        if(target==0) {left=null;leftQuery=query;} else {right=null;rightQuery=query;}
        return resolve(target,query);
    }
    private ChatBotService.Reply replacementTarget() {
        return reply("Какую модель заменить на «"+replacement+"»: первую («"+left.name()+"») или вторую («"+right.name()+"»)?",
                List.of("Заменить "+left.name(),"Заменить "+right.name()));
    }
    private ChatBotService.Reply finish(boolean specsOnly) {
        awaiting=-1;options=List.of();replacement="";
        if(left.productId()==right.productId()) {
            right=null;rightQuery="";awaiting=1;
            return reply("Выбрана одна и та же модель: «"+left.name()+"». С какой другой моделью сравнить?",List.of());
        }
        if(specsOnly)specsMode=true;
        awaitingTaskDetails=!purpose.isBlank() && taskDetails.isBlank();
        if(!specsMode && !purpose.equals("Для игр") && !taskDetails.isBlank())return acceptTaskDetails(taskDetails);
        return responses.compare(left,right,specsMode,purpose+(taskDetails.isBlank()?"":"\nУточнение: "+taskDetails)).reply();
    }
    private ChatBotService.Reply askModel(int slot) {
        String known=slot==0?(right==null?"":" с «"+right.name()+"»"):(left==null?"":" с «"+left.name()+"»");
        return reply("Назовите "+(slot==0?"первую":"вторую")+" модель для сравнения"+known+".",List.of());
    }
    private ChatBotService.Reply choices(String heading) {
        var shown=options.stream().limit(3).map(CatalogComponent::name).toList();
        String text=heading+"\n"+String.join("\n",java.util.stream.IntStream.range(0,shown.size()).mapToObj(i->(i+1)+". "+shown.get(i)).toList());
        if(options.size()>shown.size())text+="\nЕсть и другие варианты; можно уточнить полный артикул.";
        return reply(text,shown);
    }
    private boolean complete() {return left!=null && right!=null;}
    private static int ordinal(String text) {
        if(text.matches("(?:1|первый|первую|первого|первым|первая)"))return 0;
        if(text.matches("(?:2|второй|вторую|второго|вторым|вторая)"))return 1;
        if(text.matches("(?:3|третий|третью|третьего|третья)"))return 2;
        return -1;
    }
    private static int target(String text) {
        if(text.matches("(?U).*\\bперв[а-я]*\\b.*"))return 0;
        if(text.matches("(?U).*\\bвтор[а-я]*\\b.*"))return 1;
        return ordinal(text);
    }
    private static String cleanQuery(String query) {
        return ChatIntentParser.modelQuery(query.replaceFirst("(?iu)^(?:(?:а|тогда|давай|из|между|характеристики)\\s+)+","")
                .replaceAll("(?iu)\\s+(?:для игр|для работы|для монтажа|для офиса|для учебы|в играх).*$","")
                .replaceAll("[?!.,]+$","").strip());
    }
    private static String goal(String raw) {
        String text=ModelResolver.normalize(raw);
        if(text.matches(".*(?:для игр|в играх|игров|гейм).*"))return "Для игр";
        if(text.matches(".*(?:для работы|для монтажа|монтаж|рендер|рабочих задач|программирован).*"))return "Для работы";
        if(text.matches(".*(?:для офиса|для учебы|офис|учеб).*"))return "Для офиса и учёбы";
        return null;
    }
    private static boolean hasHardwareToken(String text) {
        return cleanQuery(text).matches("\\d{4,5}") || Pattern.compile("(?iu)(?:rtx|gtx|\\brx\\s*\\d|ryzen|райзен|radeon|intel|\\bi[3579]\\s*[- ]*\\d|\\b\\d{4,5}(?:k|kf|f|x|x3d)\\b)").matcher(text).find();
    }
    private static final Pattern SCENARIO_RESOLUTION=Pattern.compile("(?iu)(?<![a-z0-9])(?:720p|1080p|1440p|2160p|2k|4k)(?![a-z0-9])");
    private static boolean isGamingScenarioFollowUp(String raw,ChatIntentParser.Parsed parsed) {
        if(parsed.intent()!=ChatIntentParser.Intent.OTHER && parsed.intent()!=ChatIntentParser.Intent.COMPARE)return false;
        String text=raw.strip();
        if(Pattern.compile("(?iuU)^(?:а|тогда|давай)\\s+(?:в|на)\\s+.+").matcher(text).matches())return true;
        // A bare Latin game title is accepted only as a follow-up, never a hardware model.
        return parsed.intent()==ChatIntentParser.Intent.OTHER && !hasHardwareToken(text)
                && Pattern.compile("(?iuU)^(?:а|тогда|давай)\\s+[a-z][a-z0-9]*(?:\\s+.*|[?!.,]*)$").matcher(text).matches();
    }
    private String continuedGamingDetails(String raw) {
        String details=raw.replaceFirst("(?iuU)^(?:а|тогда|давай)\\s+","").replaceAll("[?!.,]+$","").strip();
        var resolution=SCENARIO_RESOLUTION.matcher(details);
        if(resolution.find() && details.replaceFirst("(?iuU)^(?:на\\s+)?(?:720p|1080p|1440p|2160p|2k|4k)$","").isEmpty()
                && !taskDetails.isBlank()) {
            String requested=resolution.group();
            var previous=SCENARIO_RESOLUTION.matcher(taskDetails);
            return previous.find()?previous.replaceAll(requested):taskDetails+", на "+requested;
        }
        return details;
    }
    private static final Pattern EXPLICIT_GAMING_GOAL=Pattern.compile("(?iuU)(?<![\\p{L}\\p{N}])(?:для\\s+игр|в\\s+играх)(?![\\p{L}\\p{N}])");
    private static boolean startsGamingGoal(String raw) {
        return Pattern.compile("(?iuU)^(?:(?:а|тогда|давай)\\s+)?(?:для\\s+игр|в\\s+играх)(?![\\p{L}\\p{N}])").matcher(raw.strip()).find();
    }
    private void setPurposeFrom(String raw) {
        String next=goal(raw);if(next==null)return;
        setPurpose(next);
        var marker=(next.equals("Для игр")?EXPLICIT_GAMING_GOAL:
                Pattern.compile("(?iuU)(?<![\\p{L}\\p{N}])для\\s+(?:работы|монтажа|рендеринга|рендера|офиса|уч[её]бы)(?![\\p{L}\\p{N}])")).matcher(raw);
        if(marker.find()) {
            String tail=raw.substring(marker.end()).replaceAll("^[\\s,:;—-]+|[.!?]+$","").strip();
            taskDetails=tail.length()>300?tail.substring(0,300)+"…":tail;
        }
    }
    private void setPurpose(String value) {if(!purpose.equals(value))taskDetails="";purpose=value;}
    private static ChatBotService.Reply reply(String text,List<String> suggestions) {
        return new ChatBotService.Reply(text,text,List.of(),List.of(),null,suggestions);
    }
}
