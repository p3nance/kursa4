package com.example.authapp.services;

import com.example.authapp.domain.*;
import com.example.authapp.models.Product;
import java.math.BigDecimal;
import java.net.URI;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.*;

/** Rule-based Russian chat bot. It does not call a language model or any external API. */
public final class ChatBotService {
    public sealed interface Content permits TermContent, ProductsContent, BuildContent, BenchmarksContent, SpecificationsContent, PriceComparisonContent {}
    public record ProductInfo(int id, String name, String description, BigDecimal price, int stock,
                              String imageUrl, String category, String manufacturer) {
        public static ProductInfo from(Product p) {
            return new ProductInfo(p.getId(), Objects.toString(p.getName(), ""), Objects.toString(p.getDescription(), ""),
                    p.getPrice(), p.getStock(), Objects.toString(p.getImageUrl(), ""),
                    Objects.toString(p.getCategory(), ""), Objects.toString(p.getManufacturer(), ""));
        }
        public Product toProduct() { return new Product(id, name, description, price, stock, imageUrl, category, manufacturer); }
    }
    public record RequestSummary(BigDecimal budget, BuildRecommendationService.Usage usage,
                                 Integer minRamGb, Integer minStorageGb, boolean requireVerified, List<ProductInfo> ownedParts) {
        public RequestSummary { ownedParts=List.copyOf(ownedParts); }
        public RequestSummary(BigDecimal budget, BuildRecommendationService.Usage usage) {this(budget,usage,null,null,false,List.of());}
        public RequestSummary(BigDecimal budget, BuildRecommendationService.Usage usage,Integer ram,Integer storage,boolean verified) {
            this(budget,usage,ram,storage,verified,List.of());
        }
    }
    public record TermContent(GlossaryService.Term term) implements Content {}
    public record ProductsContent(List<ProductInfo> items, int total) implements Content {
        public ProductsContent { items = List.copyOf(items); }
    }
    public record PriceComparisonContent(ProductInfo left,ProductInfo right) implements Content {
        public BigDecimal difference(){return left.price().subtract(right.price()).abs();}
    }
    public record BuildContent(List<ProductInfo> parts, BigDecimal total, BigDecimal budget,
                               BuildRecommendationService.Usage usage, boolean verified,
                               List<String> findings, List<String> limitations, Set<Integer> ownedProductIds) implements Content {
        public BuildContent {
            parts=List.copyOf(parts);findings=List.copyOf(findings);limitations=List.copyOf(limitations);ownedProductIds=Set.copyOf(ownedProductIds);
            var ids=parts.stream().map(ProductInfo::id).collect(java.util.stream.Collectors.toSet());
            if(!ids.containsAll(ownedProductIds))throw new IllegalArgumentException("Имеющаяся деталь отсутствует в составе");
        }
        public BuildContent(List<ProductInfo> parts,BigDecimal total,BigDecimal budget,BuildRecommendationService.Usage usage,
                            boolean verified,List<String> findings,List<String> limitations) {
            this(parts,total,budget,usage,verified,findings,limitations,Set.of());
        }
    }
    public record BenchmarksContent(List<BenchmarkService.Comparison> comparisons, Map<String,String> modelNames) implements Content {
        public BenchmarksContent { comparisons = List.copyOf(comparisons); modelNames=Map.copyOf(modelNames); }
        public BenchmarksContent(List<BenchmarkService.Comparison> comparisons) { this(comparisons,Map.of()); }
    }
    public record SpecRow(String label,String left,String right) {}
    public record FactSource(String title,URI uri) {}
    public record SpecificationsContent(ProductInfo left,ProductInfo right,List<SpecRow> rows,
                                        List<FactSource> sources,List<String> limitations) implements Content {
        public SpecificationsContent { rows=List.copyOf(rows);sources=List.copyOf(sources);limitations=List.copyOf(limitations); }
    }
    public record Reply(String text, String lead, List<Product> build, List<Content> content,
                        RequestSummary request, List<String> suggestions) {
        public Reply {
            build = snapshotProducts(build); content = List.copyOf(content); suggestions = List.copyOf(suggestions);
        }
        public Reply(String text) { this(text, text, List.of(), List.of(), null, List.of()); }
        public Reply(String text, List<Product> build) { this(text, text, build, List.of(), null, List.of()); }
        @Override public List<Product> build() { return snapshotProducts(build); }
        private Reply withRequest(RequestSummary request) { return new Reply(text, lead, build, content, request, suggestions); }
        private Reply withSuggestions(String... values) { return new Reply(text, lead, build, content, request, List.of(values)); }
    }
    private static List<Product> snapshotProducts(Collection<Product> products) {
        return products.stream().map(ProductInfo::from).map(ProductInfo::toProduct).toList();
    }
    private static final Locale RU = Locale.forLanguageTag("ru-RU");
    private static final String AMOUNT = "(\\d(?:[\\d ]*\\d)?(?:[.,]\\d+)?)";
    private static final String MONEY_UNIT = "((?:тыс(?:яч[аиу]?)?\\.?|[кk])(?:\\s*руб(?:лей|ля|ль)?)?|руб(?:лей|ля|ль)?|₽)";
    private static final Pattern BUDGET = Pattern.compile("(?iu)(?:бюджет(?:ом)?(?:\\s*[:=—])?|до|за)\\s*" + AMOUNT + "\\s*" + MONEY_UNIT + "?(?![\\p{L}\\p{N}])(?!\\s*(?:доллар|usd|eur|евро))");
    private static final Pattern MONEY_BUDGET = Pattern.compile("(?iu)(?:на)\\s*" + AMOUNT + "\\s*" + MONEY_UNIT + "(?![\\p{L}\\p{N}])");
    private static final Pattern SHORT_BUDGET = Pattern.compile("(?iu)^\\s*" + AMOUNT + "\\s*" + MONEY_UNIT + "?\\s*[.!]?$");
    private static final String HELP = "Могу объяснить термин, найти товар, сравнить две модели и подобрать системный блок.\n"
            + "Примеры: «Что такое TDP?», «Найди Ryzen», «Сравни модель A и модель B», «Собери ПК до 100 000 рублей для игр».";
    private List<Product> products;
    private final GlossaryService glossary;
    private final ChatIntentParser intents = new ChatIntentParser();
    private ComparisonConversation comparison;
    private OwnedBuildConversation owned;
    private ChatBuildEditor buildEditor;
    private LocalKnowledgeRepository.Snapshot knowledge;
    private Object catalogVersion;
    private BigDecimal budget;
    private BuildRecommendationService.Usage usage;
    private boolean waitingForBuild;
    private int variantCount=1;
    private final BuildRequestParser buildRequests=new BuildRequestParser();
    private BuildRequestParser.Preferences preferences=BuildRequestParser.Preferences.defaults();

    public ChatBotService(Collection<Product> products) {
        glossary=new GlossaryService();updateCatalog(products);
    }
    private ChatBotService(ChatBotService source) {
        glossary=source.glossary;products=source.products;knowledge=source.knowledge;catalogVersion=source.catalogVersion;
        budget=source.budget;usage=source.usage;waitingForBuild=source.waitingForBuild;comparison=source.comparison.copy();preferences=source.preferences;owned=source.owned.copy();variantCount=source.variantCount;buildEditor=source.buildEditor.copy();
    }
    public synchronized ChatBotService fork() { return new ChatBotService(this); }
    private record ConversationState(BigDecimal budget,BuildRecommendationService.Usage usage,
                                     boolean waitingForBuild,ComparisonConversation comparison,BuildRequestParser.Preferences preferences,OwnedBuildConversation owned,int variantCount,ChatBuildEditor buildEditor,Object catalogVersion) {}
    private synchronized ConversationState checkpoint() {
        return new ConversationState(budget,usage,waitingForBuild,comparison.copy(),preferences,owned.copy(),variantCount,buildEditor.copy(),catalogVersion);
    }
    /** The UI commits a completed draft only after its request-generation check. Catalog data stays current. */
    public void commitConversation(ChatBotService draft) {
        var state=draft.checkpoint();
        synchronized(this) {
            budget=state.budget();usage=state.usage();waitingForBuild=state.waitingForBuild();comparison.commit(state.comparison());preferences=state.preferences();owned.commit(state.owned());variantCount=state.variantCount();
            if(state.catalogVersion()==catalogVersion)buildEditor=state.buildEditor();
        }
    }
    public synchronized void reset() { budget=null;usage=null;waitingForBuild=false;comparison.clear();preferences=BuildRequestParser.Preferences.defaults();owned.clear();variantCount=1;buildEditor.clear(); }
    public synchronized RequestSummary summary() { return new RequestSummary(budget,usage,preferences.minRamGb(),preferences.minStorageGb(),preferences.requireVerified(),ownedProducts()); }
    enum Routing { NATIVE, LANGUAGE }
    synchronized boolean hasPendingSelection() {return owned.pending() || comparison.awaitingChoice() || buildEditor.pending();}
    synchronized boolean buildEditRequest(String text){
        return buildEditor.pending() || ChatBuildEditor.isCommand(text) && (comparison.selectedModels().isEmpty()
                || Pattern.compile("(?U)\\b(?:сборк\\p{L}*|вариант\\p{L}*)\\b").matcher(ModelResolver.normalize(text)).find());
    }
    synchronized void rememberBuilds(Reply reply){buildEditor.observe(reply);}
    synchronized Optional<Reply> unsupportedAction(String raw) {
        if(raw==null)return Optional.empty();String text=ModelResolver.normalize(raw);
        boolean cart=text.matches(".*(?:добав\\p{L}*|полож\\p{L}*|клади|убер\\p{L}*|удал\\p{L}*|оформ\\p{L}*|закаж\\p{L}*).*(?:корзин\\p{L}*|заказ\\p{L}*).*" );
        boolean price=text.matches(".*(?:измени|изменить|поменяй|снизь|понизь|установи|верни|сделай).{0,40}цену.*");
        if(!cart && !price)return Optional.empty();
        String message=price?"В чате можно проверить цену из каталога или уточнить бюджет подбора. Изменение цены самого товара здесь недоступно.":
                "Откройте карточку товара, конфигуратор или раздел корзины, чтобы выполнить это действие. Здесь могу помочь найти товар или подобрать состав.";
        return Optional.of(new Reply(message,message,List.of(),List.of(),summary(),List.of("Найди Ryzen","Собери ПК")));
    }
    synchronized boolean hasExplicitModelMention(String raw) {
        return raw!=null && (!new ModelResolver(knowledge.components()).mentions(raw).isEmpty()
                || Pattern.compile("(?iuU)\\b(?:ryzen|rtx|gtx|radeon|core|rx)\\b.{0,24}\\d{3,5}").matcher(raw).find());
    }
    synchronized boolean catalogPriceQuery(String raw) {
        if(raw==null || !ChatCatalogFilter.hasPriceSyntax(raw)
                || Pattern.compile("(?iuU)\\b(?:пк|компьютер\\p{L}*|системн\\p{L}*|сборк\\p{L}*|собери\\p{L}*|собрать)\\b").matcher(raw).find())return false;
        return hasExplicitModelMention(raw) || Pattern.compile("(?iuU)\\b(?:ryzen|rtx|gtx|radeon|core|samsung|kingston|corsair|seagate|crucial|процессор\\p{L}*|видеокарт\\p{L}*|озу|ram|ssd|ссд|накопител\\p{L}*|кулер\\p{L}*|корпус\\p{L}*|плат[ауы])\\b").matcher(raw).find();
    }
    synchronized Optional<Reply> namedProductReference(String raw) {
        var parsed=intents.parse(raw);
        if(parsed.intent()==ChatIntentParser.Intent.COMPARE)return Optional.empty();
        var named=new ModelResolver(knowledge.components()).mentions(raw);
        return named.size()==1?Optional.of(search(named.getFirst().name(),ChatCatalogFilter.parse(raw)).withRequest(summary())):Optional.empty();
    }
    synchronized Reply productReferenceContext(Reply previous) {
        if(previous==null || previous.content().stream().noneMatch(BenchmarksContent.class::isInstance)
                || previous.content().stream().anyMatch(c->c instanceof ProductsContent || c instanceof SpecificationsContent))return previous;
        var selected=comparison.selectedModels();if(selected.size()!=2)return previous;
        var refs=new ArrayList<ProductInfo>();
        for(String name:selected) {
            var found=products.stream().filter(p->ModelResolver.normalize(p.getName()).equals(ModelResolver.normalize(name))).findFirst();
            if(found.isEmpty())return previous;refs.add(ProductInfo.from(found.get()));
        }
        var content=new ArrayList<>(previous.content());content.add(new ProductsContent(refs,refs.size()));
        return new Reply(previous.text(),previous.lead(),previous.build(),content,previous.request(),previous.suggestions());
    }
    synchronized Routing routing(String raw) {
        if(raw==null || raw.isBlank() || raw.length()>2000)return Routing.NATIVE;
        if(invalidNegativeBudget(raw))return Routing.NATIVE;
        var parsed=intents.parse(raw);
        if(buildEditRequest(raw) || owned.pending() || OwnedBuildConversation.isCommand(raw))return Routing.NATIVE;
        if(parsed.intent()==ChatIntentParser.Intent.BUILD && catalogPriceQuery(raw)
                && Pattern.compile("(?iuU)^(?:подбери|подберите|подобрать)\\s+").matcher(raw.strip()).find())return Routing.NATIVE;
        if((parsed.intent()==ChatIntentParser.Intent.OTHER || parsed.intent()==ChatIntentParser.Intent.BUILD) && catalogPriceQuery(raw))return Routing.LANGUAGE;
        if(LocalChatPlan.relativeMoneyRequest(raw))return Routing.LANGUAGE;
        if(parsed.intent()==ChatIntentParser.Intent.BUILD) {
            if(Pattern.compile("(?iuU)(?:не\\s+(?:для\\s+)?(?:игр\\p{L}*|гейм\\p{L}*|офис\\p{L}*|монтаж\\p{L}*)|вместо\\s+(?:игр|офиса|монтажа)|перехожу\\s+с)").matcher(raw).find())return Routing.LANGUAGE;
            if(budgetFrom(raw).isEmpty() && Pattern.compile("(?iuU)\\d[\\d .,]*\\s*(?:тыс\\p{L}*|руб\\p{L}*|₽|[кk](?![\\p{L}]))").matcher(raw).find())return Routing.LANGUAGE;
        }
        if(parsed.intent()==ChatIntentParser.Intent.DEFINE && glossary.search(parsed.body(),1).isEmpty())return Routing.LANGUAGE;
        if(parsed.intent()==ChatIntentParser.Intent.COMPARE && parsed.pair().size()<2 && !comparison.selectedModels().isEmpty())return Routing.LANGUAGE;
        if(parsed.intent()!=ChatIntentParser.Intent.OTHER)return Routing.NATIVE;
        String text=ModelResolver.normalize(raw);
        if(comparison.awaitingChoice() || text.matches("(?:да|нет|отмена|не надо|первый|второй|третий|[123])"))return Routing.NATIVE;
        var constraints=buildRequests.parse(raw);
        if(constraints.ram()!=null || constraints.storage()!=null || constraints.verified()!=null || constraints.reset())return Routing.NATIVE;
        if(!constraints.problems().isEmpty() && Pattern.compile("(?iuU)\\b(?:озу|ram|ssd|ссд|оператив[нк]\\p{L}*|накопител\\p{L}*)\\b").matcher(raw).find())return Routing.NATIVE;
        if(budgetFrom(raw).isPresent() && text.matches("(?iu)^(?:до |бюджет(?:ом)?[:= ]*|на )?[0-9][0-9 .,]*(?:тыс.*|[кk]|руб.*|₽)?[.!?]*$"))return Routing.NATIVE;
        if(BuildRequestParser.explicitUsageChoice(raw).isPresent())return Routing.NATIVE;
        // Everything else needs semantic interpretation, even if the old waiting-state would swallow it.
        return Routing.LANGUAGE;
    }
    synchronized ChatDialogueContext dialogueContext(List<String> history) {
        return new ChatDialogueContext(summary(),waitingForBuild,comparison.selectedModels(),comparison.purpose(),comparison.details(),history);
    }
    synchronized Reply applyLanguagePlan(String raw,LocalChatPlan plan) {
        var before=checkpoint();
        try {
            Reply answer;
            if(plan.action().equals("BUILD"))answer=replyTo(raw,plan);
            else if(plan.action().equals("CONTEXT"))answer=comparison.applyLanguagePurpose(plan.usage(),raw);
            else if(plan.action().equals("SEARCH")) {
                owned.cancelPending();comparison.clear();waitingForBuild=false;
                answer=search(ChatCatalogFilter.parse(plan.queries().getFirst()).query(),ChatCatalogFilter.parse(raw));
            }
            else answer=replyTo(plan.command(raw));
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Запрос отменён");
            rememberBuilds(answer);return answer.withRequest(summary());
        } catch(RuntimeException ex) {
            budget=before.budget();usage=before.usage();waitingForBuild=before.waitingForBuild();comparison=before.comparison();preferences=before.preferences();owned=before.owned();variantCount=before.variantCount();buildEditor=before.buildEditor();throw ex;
        }
    }
    /** A model may extract a catalog identity only if the resolver also finds that exact identity in the original message. */
    synchronized boolean preservesModelIdentity(String raw,List<String> queries) {
        var resolver=new ModelResolver(knowledge.components());
        var mentioned=resolver.mentions(raw).stream().map(CatalogComponent::productId).collect(java.util.stream.Collectors.toSet());
        for(String query:queries) {
            var result=resolver.resolve(query);
            if(result.status()==ModelResolver.Status.EXACT && result.candidates().stream().anyMatch(c->!mentioned.contains(c.productId())))return false;
        }
        return true;
    }
    public synchronized void updateCatalog(Collection<Product> products) {
        catalogVersion=new Object();
        this.products = snapshotProducts(products);
        knowledge = new LocalKnowledgeRepository().load(this.products);
        comparison=comparison==null?new ComparisonConversation(knowledge,this.products):comparison.refreshed(knowledge,this.products);
        owned=owned==null?new OwnedBuildConversation(knowledge.components()):owned.refreshed(knowledge.components());
        buildEditor=new ChatBuildEditor(knowledge.components(),this.products);
    }

    public synchronized Reply reply(String raw) {
        if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Запрос отменён");
        var before=checkpoint();
        try {
            Reply answer=replyTo(raw);
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Запрос отменён");
            rememberBuilds(answer);return answer.withRequest(summary());
        } catch(RuntimeException ex) {
            budget=before.budget();usage=before.usage();waitingForBuild=before.waitingForBuild();comparison=before.comparison();preferences=before.preferences();owned=before.owned();variantCount=before.variantCount();buildEditor=before.buildEditor();throw ex;
        }
    }
    private Reply replyTo(String raw) {
        return replyTo(raw,null);
    }
    private Reply replyTo(String raw,LocalChatPlan language) {
        if(raw==null || raw.isBlank())return new Reply(HELP);
        if(raw.length()>2000)return new Reply("Сократите сообщение до 2000 символов.");
        raw=raw.replace('\u202f',' ').replace('\u00a0',' ').replace('\n',' ').replace('\r',' ');
        var unsupported=unsupportedAction(raw);if(unsupported.isPresent())return unsupported.get();
        var itemMoney=ChatItemMoney.problem(raw);if(itemMoney.isPresent())return new Reply(itemMoney.get());
        var budgetProblem=ChatBudgetInput.problem(raw);if(budgetProblem.isPresent())return new Reply(budgetProblem.get());
        if(invalidNegativeBudget(raw))return new Reply("Бюджет должен быть больше нуля. Укажите положительную сумму в рублях.");
        if(language==null && LocalChatPlan.relativeMoneyRequest(raw))return new Reply("Назовите итоговый бюджет в рублях. Относительные изменения доступны с локальной моделью.");
        String text=raw.toLowerCase(RU).replace('ё','е').trim();
        var parsed=intents.parse(raw);
        if(language!=null)parsed=new ChatIntentParser.Parsed(ChatIntentParser.Intent.BUILD,raw,List.of());
        if(parsed.intent()==ChatIntentParser.Intent.RESET) {reset();return new Reply("Начнём заново. "+HELP);}
        if(parsed.intent()==ChatIntentParser.Intent.COMPARE_ORDER)return comparison.respond(raw,parsed).orElseThrow();
        var edited=buildEditRequest(raw)?buildEditor.respond(raw,summary()):Optional.<Reply>empty();
        if(edited.isPresent()) {
            owned.cancelPending();comparison.clear();waitingForBuild=false;
            edited.get().content().stream().filter(BuildContent.class::isInstance).map(BuildContent.class::cast).findFirst().ifPresent(card->{
                for(var part:List.copyOf(owned.components().values()))if(!card.ownedProductIds().contains(part.productId()))owned.forget(part.category());
            });
            return edited.get();
        }
        if(parsed.intent()==ChatIntentParser.Intent.GREETING) {owned.cancelPending();comparison.clear();waitingForBuild=false;return new Reply(HELP);}
        if(parsed.intent()==ChatIntentParser.Intent.THANKS)return new Reply("Пожалуйста! Можно продолжить сравнение, найти товар или уточнить задачу.");
        if(parsed.intent()==ChatIntentParser.Intent.SEARCH) {owned.cancelPending();comparison.clear();waitingForBuild=false;return search(parsed.body());}
        if(parsed.intent()==ChatIntentParser.Intent.BUILD && catalogPriceQuery(raw)) {
            var prefix=Pattern.compile("(?iuU)^(?:подбери|подберите|подобрать)\\s+").matcher(raw.strip());
            if(prefix.find()){owned.cancelPending();comparison.clear();waitingForBuild=false;return search(raw.strip().substring(prefix.end()));}
            return new Reply("Предел цены товара не меняет бюджет ПК. Напишите «Найди» и название товара с пределом цены в рублях.");
        }
        if(parsed.intent()==ChatIntentParser.Intent.DEFINE) {owned.cancelPending();comparison.clear();waitingForBuild=false;return definition(parsed.body());}
        boolean comparing=parsed.intent()==ChatIntentParser.Intent.COMPARE || parsed.intent()==ChatIntentParser.Intent.COMPARE_HELP
                || parsed.intent()==ChatIntentParser.Intent.COMPARE_VIEW;
        if(comparing)owned.cancelPending();
        var ownership=comparing?OwnedBuildConversation.Outcome.none(raw):owned.respond(raw);
        String buildRaw=ownership.handled()?ownership.remaining():raw;
        String buildText=buildRaw.toLowerCase(RU).replace('ё','е').trim();
        var constraints=buildRequests.parse(buildRaw);
        boolean refine=ownership.handled() || owned.pending() || parsed.intent()==ChatIntentParser.Intent.OTHER &&
                ((waitingForBuild || budget!=null) && constraints.hasChanges() || constraints.ram()!=null || constraints.storage()!=null || constraints.verified()!=null || constraints.reset());
        if(!comparing && (parsed.intent()==ChatIntentParser.Intent.BUILD || refine))comparison.clear();
        else {
            var compared=comparison.respond(raw,parsed);
            if(compared.isPresent()) {waitingForBuild=false;return compared.get();}
        }
        var terms=glossary.search(raw,3);
        if(!terms.isEmpty() && parsed.intent()!=ChatIntentParser.Intent.BUILD && !containsBuildIntent(text) && !waitingForBuild && !refine) {
            comparison.clear();return definition(raw);
        }
        if (language!=null || parsed.intent()==ChatIntentParser.Intent.BUILD || containsBuildIntent(text) || waitingForBuild || refine || SHORT_BUDGET.matcher(text).matches()) {
            Optional<BigDecimal> parsedBudget = language!=null && language.budget()!=null?Optional.of(language.effectiveBudget(budget)):budgetFrom(buildRaw);
            if(parsedBudget.isPresent() && (parsedBudget.get().signum()<=0 || parsedBudget.get().compareTo(new BigDecimal("100000000"))>0))
                return new Reply("Укажите бюджет больше нуля и не более 100 млн рублей.");
            if (buildText.matches(".*(?:доллар|usd|eur|евро|[$€]).*")) return new Reply("Пока подбираю по ценам каталога в рублях. Укажите бюджет в рублях.");
            if(!constraints.problems().isEmpty()) return new Reply(String.join("\n",constraints.problems()));
            waitingForBuild = true;
            preferences=constraints.apply(preferences);
            if(!buildText.isBlank() && (parsed.intent()==ChatIntentParser.Intent.BUILD || alternativeCount(buildText)>1))variantCount=alternativeCount(buildText);
            if (parsedBudget.isPresent()) budget = parsedBudget.get();
            if(language!=null) {
                if(!language.usage().equals("KEEP") && LocalChatPlan.hasTaskInformation(buildRaw))usage=BuildRecommendationService.Usage.valueOf(language.usage());
            } else {
                var choice=BuildRequestParser.explicitUsageChoice(buildRaw);
                if(choice.isPresent())usage=choice.get();
                else if (buildText.matches(".*(?:игр|гейм).*")) usage = BuildRecommendationService.Usage.GAMING;
                else if (buildText.matches(".*(?:офис|учеб|учеб|документ|браузер).*")) usage = BuildRecommendationService.Usage.OFFICE;
                else if (buildText.matches(".*(?:монтаж|рендер|рабоч|работ|программ|разработ|3d).*")) usage = BuildRecommendationService.Usage.WORKSTATION;
            }
            if(ownership.response()!=null)return ownership.response();
            var ownedProblem=owned.problem();if(ownedProblem!=null)return ownedProblem;
            if (budget == null) return new Reply("Какой бюджет покупки в рублях? Имеющиеся детали в него не входят. Например: «до 100 000 рублей». Монитор и периферия считаются отдельно.").withSuggestions("До 60 000 рублей", "До 100 000 рублей", "До 150 000 рублей");
            if (budget.signum() <= 0 || budget.compareTo(new BigDecimal("100000000")) > 0)
                return new Reply("Укажите бюджет больше нуля и не более 100 млн рублей.");
            if (usage == null) return new Reply("Для каких задач нужен ПК: игры, офис/учёба или монтаж/рабочие приложения?").withSuggestions(BuildRequestParser.usageChoices().toArray(String[]::new));
            return recommend(variantCount);
        }
        if (text.matches(".*(?:совместим|подойдет|подходит).*")) return new Reply("Откройте конфигуратор и выберите компоненты: он проверит сокет, память, питание, габариты и другие доступные характеристики. Если данных недостаточно, это будет указано отдельно.");
        return new Reply("Пока не распознал запрос. " + HELP);
    }

    static Optional<BigDecimal> budgetFrom(String text) {
        text = text.replace('\u202f', ' ').replace('\u00a0', ' ');
        Matcher matcher = BUDGET.matcher(text);
        if (!matcher.find()) {
            matcher = MONEY_BUDGET.matcher(text);
            if (!matcher.find()) { matcher = SHORT_BUDGET.matcher(text); if (!matcher.matches()) return Optional.empty(); }
        }
        try {
            BigDecimal value = new BigDecimal(matcher.group(1).replaceAll("[\\s\\u00a0]", "").replace(',', '.'));
            String suffix = matcher.group(2);
            if (suffix != null && suffix.toLowerCase(RU).matches("(?:тыс.*|[кk](?:\\s*руб.*)?)")) value = value.multiply(BigDecimal.valueOf(1000));
            return Optional.of(value);
        } catch (NumberFormatException ex) { return Optional.empty(); }
    }
    static boolean invalidNegativeBudget(String raw) {
        return Pattern.compile("(?iuU)(?:\\bминус\\s*|(?:бюджет(?:ом)?[:= ]*|до\\s*)[-−]\\s*|^\\s*[-−]\\s*)\\d[\\d .,]*").matcher(raw).find();
    }
    private static boolean containsBuildIntent(String text) { return text.matches(".*(?:собери|собрать|сборк|подбери|подобрать|нужен пк|компьютер|бюджет).*?"); }
    private Reply search(String query) {
        var filter=ChatCatalogFilter.parse(query);
        return search(filter.query(),filter);
    }
    private Reply search(String query,ChatCatalogFilter filter) {
        if(filter.problem()!=null)return new Reply(filter.problem());
        String normalized=ModelResolver.normalize(query);
        ComponentCategory category=switch(normalized) {
            case "cpu","процессор","процессоры","процессора" -> ComponentCategory.CPU;
            case "gpu","видеокарта","видеокарты","видеокарту" -> ComponentCategory.GPU;
            case "ram","оперативная память","оперативную память" -> ComponentCategory.RAM;
            case "ssd","накопитель","накопители","ssd накопитель" -> ComponentCategory.STORAGE;
            case "материнская плата","материнскую плату","материнские платы" -> ComponentCategory.MOTHERBOARD;
            case "бп","блок питания","блоки питания" -> ComponentCategory.PSU;
            case "корпус","корпуса" -> ComponentCategory.CASE;
            case "кулер","охлаждение" -> ComponentCategory.COOLER;
            default -> null;
        };
        String modelQuery=query.replaceFirst("(?iu)^(?:процессор(?:а)?|видеокарт[ау]|модель|cpu|gpu)\\s+","");
        String q=ModelResolver.normalize(modelQuery);
        if(q.isBlank())return new Reply("Укажите название или категорию товара.");
        var resolution=new ModelResolver(knowledge.components()).resolve(modelQuery);
        Set<Integer> ids=new HashSet<>(CatalogNameAliases.exactProductIds(products,modelQuery));
        if(resolution.status()==ModelResolver.Status.EXACT || resolution.status()==ModelResolver.Status.AMBIGUOUS)
            resolution.candidates().stream().map(CatalogComponent::productId).forEach(ids::add);
        var matches=products.stream().filter(p->!ids.isEmpty()?ids.contains(p.getId()):
                category!=null?ComponentCategory.fromDisplayName(p.getCategory()).orElse(null)==category:
                ModelResolver.normalize(p.getName()+" "+p.getCategory()+" "+p.getManufacturer()).contains(q))
                .filter(p->filter.accepts(ProductInfo.from(p))).toList();
        if(matches.isEmpty() && resolution.status()==ModelResolver.Status.SUGGESTED) {
            var candidates=resolution.candidates().stream().limit(3).map(c->"Найди "+c.name()).toList();
            return new Reply("Точное название не найдено. Возможно, вы имели в виду один из этих товаров? Подтвердите выбор.",
                    "Точное название не найдено. Выберите подходящий товар.",List.of(),List.of(),null,candidates);
        }
        if (matches.isEmpty()) return new Reply("В текущем каталоге ничего не найдено по запросу «" + query + "». Уточните название или категорию.");
        StringJoiner answer = new StringJoiner("\n", "В каталоге:\n", "\nЦены и остатки могут измениться.");
        matches.stream().limit(10).forEach(p -> answer.add(p.getName() + " — " + money(p.getPrice()) + (p.getStock() > 0 ? ", в наличии: " + p.getStock() : ", нет в наличии")));
        if (matches.size() > 10) answer.add("Показано 10 из " + matches.size() + ". Уточните запрос.");
        return new Reply(answer.toString(), "Нашёл товары в текущем каталоге. Цены и наличие могут измениться.", List.of(),
                List.of(new ProductsContent(matches.stream().limit(10).map(ProductInfo::from).toList(), matches.size())), null, List.of());
    }
    private static int alternativeCount(String text) {
        if(text.matches("(?U).*(?:\\b2|\\bдва)\\s+вариант.*"))return 2;
        return text.matches(".*(?:вариант[ыа]|альтернатив).*")?3:1;
    }
    private List<ProductInfo> ownedProducts() {
        return owned.components().values().stream().sorted(Comparator.comparing(CatalogComponent::category)).map(c->
                ProductInfo.from(products.stream().filter(p->p.getId()==c.productId()).findFirst().orElseThrow())).toList();
    }
    private Reply recommend(int count) {
        var ownedProblem=owned.problem();if(ownedProblem!=null)return ownedProblem;
        var inherited=owned.components();
        var rejected=inherited.values().stream().filter(c->!preferences.accepts(c)).toList();
        if(!rejected.isEmpty())return new Reply("Имеющиеся детали не подтверждают заданный минимум объёма: "+rejected.stream().map(CatalogComponent::name).collect(java.util.stream.Collectors.joining(", "))+". Измените минимум или список имеющихся деталей.");
        if (knowledge.components().isEmpty()) return new Reply("В каталоге пока нет комплектующих для подбора. Бюджет " + money(budget) + " запомнил. " + warnings());
        var results = new BuildRecommendationService().recommendAlternatives(knowledge.components().stream().filter(preferences::accepts).toList(), purchaseRequest(inherited),count);
        var replies=results.stream().map(this::presentBuild).toList();
        if(replies.size()==1)return replies.getFirst();
        var cards=replies.stream().flatMap(reply->reply.content().stream()).toList();
        StringJoiner text=new StringJoiner("\n\n");
        for(int i=0;i<replies.size();i++)text.add("Вариант "+(i+1)+". "+replies.get(i).text());
        return new Reply(text.toString(),"Подготовил "+replies.size()+" варианта, включая базовый. Они отличаются составом; цена не означает превосходства в FPS или скорости приложений.",
                replies.getFirst().build(),cards,null,List.of("ОЗУ 32 ГБ", "Что такое совместимость?"));
    }
    private BuildRecommendationService.Request purchaseRequest(Map<ComponentCategory,CatalogComponent> inherited) {
        var base=preferences.request(budget,usage);
        return new BuildRecommendationService.Request(base.budget(),base.usage(),base.minRamGb(),base.minStorageGb(),inherited,base.requireVerified());
    }
    private Reply presentBuild(BuildRecommendationService.Recommendation result) {
        if (result.components().isEmpty()) return new Reply(String.join("\n", result.explanations()) + (preferences.minRamGb()!=null || preferences.minStorageGb()!=null ? "\nДля заданных минимумов объёма учитываю только проверенные характеристики ОЗУ/SSD." : "") + "\nБюджет: " + money(budget) + ".\n" + warnings());
        StringJoiner answer = new StringJoiner("\n");
        answer.add(result.status() == BuildRecommendationService.Status.READY ? "Вариант сборки:" : "Черновой вариант — совместимость ещё не подтверждена:");
        result.components().forEach(c -> answer.add(c.category().displayName() + ": " + c.name() + (owned.components().containsKey(c.category())?" — уже есть, не покупаем":" — "+money(c.price()))));
        answer.add("К покупке: " + money(result.purchaseTotal()) + " из " + money(budget));
        result.explanations().forEach(answer::add);
        result.compatibility().findings().stream().filter(f -> f.status() != CompatibilityService.Status.COMPATIBLE).limit(5).forEach(f -> answer.add("• " + f.message()));
        if (!knowledge.warnings().isEmpty()) answer.add(warnings());
        var selected = result.components().stream().map(c -> products.stream().filter(p -> p.getId() == c.productId()).findFirst().orElseThrow()).toList();
        var findings = result.compatibility().findings().stream().filter(f -> f.status() != CompatibilityService.Status.COMPATIBLE).map(CompatibilityService.Finding::message).toList();
        var limitations = new ArrayList<>(result.compatibility().limitations());
        limitations.addAll(result.explanations()); limitations.addAll(knowledge.warnings());
        boolean verified = result.status() == BuildRecommendationService.Status.READY;
        var card = new BuildContent(selected.stream().map(ProductInfo::from).toList(), result.purchaseTotal(), budget, usage, verified, findings, limitations,owned.components().values().stream().map(CatalogComponent::productId).collect(java.util.stream.Collectors.toSet()));
        return new Reply(answer.toString(), verified ? "Подготовил вариант в вашем бюджете." : "Подготовил черновой вариант. Перед покупкой нужно проверить характеристики деталей.",
                selected, List.of(card), null, owned.components().isEmpty()?List.of("Покажи варианты сборки", "Что такое совместимость?"):List.of("Покажи варианты сборки", "Сбросить имеющиеся детали"));
    }
    private Reply definition(String query) {
        var terms=glossary.search(query,3);
        if(terms.isEmpty())return new Reply("Такого термина пока нет в словаре. Попробуйте сокет, CPU, GPU, RAM, SSD, NVMe, TDP или блок питания.");
        StringJoiner answer=new StringJoiner("\n\n");
        for(var term:terms)answer.add(term.term()+": "+term.definition()+"\nПроще: "+term.simpleExplanation()+"\nИсточник: "+term.source());
        return new Reply(answer.toString(),"Вот что означает этот термин.",List.of(),
                terms.stream().map(TermContent::new).map(Content.class::cast).toList(),null,List.of());
    }
    private String warnings() { return String.join("\n", knowledge.warnings()); }
    private static String money(BigDecimal value) { return NumberFormat.getNumberInstance(RU).format(value) + " ₽"; }
}
