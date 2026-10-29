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
    public sealed interface Content permits TermContent, ProductsContent, BuildContent, BenchmarksContent, SpecificationsContent {}
    public record ProductInfo(int id, String name, String description, BigDecimal price, int stock,
                              String imageUrl, String category, String manufacturer) {
        public static ProductInfo from(Product p) {
            return new ProductInfo(p.getId(), Objects.toString(p.getName(), ""), Objects.toString(p.getDescription(), ""),
                    BigDecimal.valueOf(p.getPrice()), p.getStock(), Objects.toString(p.getImageUrl(), ""),
                    Objects.toString(p.getCategory(), ""), Objects.toString(p.getManufacturer(), ""));
        }
        public Product toProduct() { return new Product(id, name, description, price.doubleValue(), stock, imageUrl, category, manufacturer); }
    }
    public record RequestSummary(BigDecimal budget, BuildRecommendationService.Usage usage) {}
    public record TermContent(GlossaryService.Term term) implements Content {}
    public record ProductsContent(List<ProductInfo> items, int total) implements Content {
        public ProductsContent { items = List.copyOf(items); }
    }
    public record BuildContent(List<ProductInfo> parts, BigDecimal total, BigDecimal budget,
                               BuildRecommendationService.Usage usage, boolean verified,
                               List<String> findings, List<String> limitations) implements Content {
        public BuildContent { parts = List.copyOf(parts); findings = List.copyOf(findings); limitations = List.copyOf(limitations); }
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
    private static final String MONEY_UNIT = "(тыс(?:яч[аиу]?)?\\.?|[кk](?![\\p{L}])|руб(?:лей|ля|ль)?|₽)";
    private static final Pattern BUDGET = Pattern.compile("(?iu)(?:бюджет(?:ом)?(?:\\s*[:=—])?|до|за)\\s*" + AMOUNT + "\\s*" + MONEY_UNIT + "?(?![\\p{L}\\p{N}])(?!\\s*(?:доллар|usd|eur|евро))");
    private static final Pattern MONEY_BUDGET = Pattern.compile("(?iu)(?:на)\\s*" + AMOUNT + "\\s*" + MONEY_UNIT + "(?![\\p{L}\\p{N}])");
    private static final Pattern SHORT_BUDGET = Pattern.compile("(?iu)^\\s*" + AMOUNT + "\\s*" + MONEY_UNIT + "?\\s*[.!]?$");
    private static final String HELP = "Могу объяснить термин, найти товар, сравнить две модели и подобрать системный блок.\n"
            + "Примеры: «Что такое TDP?», «Найди Ryzen», «Сравни модель A и модель B», «Собери ПК до 100 000 рублей для игр».";
    private List<Product> products;
    private final GlossaryService glossary;
    private final ChatIntentParser intents = new ChatIntentParser();
    private ComparisonConversation comparison;
    private LocalKnowledgeRepository.Snapshot knowledge;
    private BigDecimal budget;
    private BuildRecommendationService.Usage usage;
    private boolean waitingForBuild;

    public ChatBotService(Collection<Product> products) {
        glossary=new GlossaryService();updateCatalog(products);
    }
    private ChatBotService(ChatBotService source) {
        glossary=source.glossary;products=source.products;knowledge=source.knowledge;
        budget=source.budget;usage=source.usage;waitingForBuild=source.waitingForBuild;comparison=source.comparison.copy();
    }
    public synchronized ChatBotService fork() { return new ChatBotService(this); }
    private record ConversationState(BigDecimal budget,BuildRecommendationService.Usage usage,
                                     boolean waitingForBuild,ComparisonConversation comparison) {}
    private synchronized ConversationState checkpoint() {
        return new ConversationState(budget,usage,waitingForBuild,comparison.copy());
    }
    /** The UI commits a completed draft only after its request-generation check. Catalog data stays current. */
    public void commitConversation(ChatBotService draft) {
        var state=draft.checkpoint();
        synchronized(this) {
            budget=state.budget();usage=state.usage();waitingForBuild=state.waitingForBuild();comparison.commit(state.comparison());
        }
    }
    public synchronized void reset() { budget=null;usage=null;waitingForBuild=false;comparison.clear(); }
    public synchronized RequestSummary summary() { return new RequestSummary(budget, usage); }
    public synchronized void updateCatalog(Collection<Product> products) {
        this.products = snapshotProducts(products);
        knowledge = new LocalKnowledgeRepository().load(this.products);
        comparison=comparison==null?new ComparisonConversation(knowledge,this.products):comparison.refreshed(knowledge,this.products);
    }

    public synchronized Reply reply(String raw) {
        if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Запрос отменён");
        var before=checkpoint();
        try {
            Reply answer=replyTo(raw);
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Запрос отменён");
            return answer.withRequest(summary());
        } catch(RuntimeException ex) {
            budget=before.budget();usage=before.usage();waitingForBuild=before.waitingForBuild();comparison=before.comparison();throw ex;
        }
    }
    private Reply replyTo(String raw) {
        if(raw==null || raw.isBlank())return new Reply(HELP);
        if(raw.length()>2000)return new Reply("Сократите сообщение до 2000 символов.");
        raw=raw.replace('\u202f',' ').replace('\u00a0',' ').replace('\n',' ').replace('\r',' ');
        String text=raw.toLowerCase(RU).replace('ё','е').trim();
        var parsed=intents.parse(raw);
        if(parsed.intent()==ChatIntentParser.Intent.RESET) {reset();return new Reply("Начнём заново. "+HELP);}
        if(parsed.intent()==ChatIntentParser.Intent.GREETING) {comparison.clear();waitingForBuild=false;return new Reply(HELP);}
        if(parsed.intent()==ChatIntentParser.Intent.THANKS)return new Reply("Пожалуйста! Можно продолжить сравнение, найти товар или уточнить задачу.");
        if(parsed.intent()==ChatIntentParser.Intent.SEARCH) {comparison.clear();waitingForBuild=false;return search(parsed.body());}
        if(parsed.intent()==ChatIntentParser.Intent.DEFINE) {comparison.clear();waitingForBuild=false;return definition(parsed.body());}
        if(parsed.intent()==ChatIntentParser.Intent.BUILD)comparison.clear();
        else {
            var compared=comparison.respond(raw,parsed);
            if(compared.isPresent()) {waitingForBuild=false;return compared.get();}
        }
        var terms=glossary.search(raw,3);
        if(!terms.isEmpty() && parsed.intent()!=ChatIntentParser.Intent.BUILD && !containsBuildIntent(text) && !waitingForBuild) {
            comparison.clear();return definition(raw);
        }
        if (parsed.intent()==ChatIntentParser.Intent.BUILD || containsBuildIntent(text) || waitingForBuild || SHORT_BUDGET.matcher(text).matches()) {
            waitingForBuild = true;
            if (text.matches(".*(?:доллар|usd|eur|евро|[$€]).*")) return new Reply("Пока подбираю по ценам каталога в рублях. Укажите бюджет в рублях.");
            Optional<BigDecimal> parsedBudget = budgetFrom(raw);
            if (parsedBudget.isPresent()) budget = parsedBudget.get();
            if (text.matches(".*(?:игр|гейм).*")) usage = BuildRecommendationService.Usage.GAMING;
            else if (text.matches(".*(?:офис|учеб|учеб|документ|браузер).*")) usage = BuildRecommendationService.Usage.OFFICE;
            else if (text.matches(".*(?:монтаж|рендер|рабоч|программ|разработ|3d).*")) usage = BuildRecommendationService.Usage.WORKSTATION;
            if (budget == null) return new Reply("Какой бюджет системного блока в рублях? Например: «до 100 000 рублей». Монитор и периферия считаются отдельно.").withSuggestions("До 60 000 рублей", "До 100 000 рублей", "До 150 000 рублей");
            if (budget.signum() <= 0 || budget.compareTo(new BigDecimal("100000000")) > 0)
                return new Reply("Укажите бюджет больше нуля и не более 100 млн рублей.");
            if (usage == null) return new Reply("Для каких задач нужен ПК: игры, офис/учёба или монтаж/рабочие приложения?").withSuggestions("Для игр", "Для учёбы и офиса", "Для монтажа и рабочих задач");
            return recommend();
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
            if (suffix != null && suffix.toLowerCase(RU).matches("(?:тыс.*|[кk])")) value = value.multiply(BigDecimal.valueOf(1000));
            return Optional.of(value);
        } catch (NumberFormatException ex) { return Optional.empty(); }
    }
    private static boolean containsBuildIntent(String text) { return text.matches(".*(?:собери|собрать|сборк|подбери|подобрать|нужен пк|компьютер|бюджет).*?"); }
    private Reply search(String query) {
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
        Set<Integer> ids=resolution.status()==ModelResolver.Status.EXACT || resolution.status()==ModelResolver.Status.AMBIGUOUS
                ?resolution.candidates().stream().map(CatalogComponent::productId).collect(java.util.stream.Collectors.toSet()):Set.of();
        var matches=products.stream().filter(p->ids.contains(p.getId()) || category!=null && ComponentCategory.fromDisplayName(p.getCategory()).orElse(null)==category
                || ModelResolver.normalize(p.getName()+" "+p.getCategory()+" "+p.getManufacturer()).contains(q)).toList();
        if(matches.isEmpty() && resolution.status()==ModelResolver.Status.SUGGESTED) {
            var candidates=resolution.candidates().stream().limit(3).map(c->"Найди "+c.name()).toList();
            return new Reply("Точное название не найдено. Возможно, вы имели в виду один из этих товаров? Подтвердите выбор.",
                    "Точное название не найдено. Выберите подходящий товар.",List.of(),List.of(),null,candidates);
        }
        if (matches.isEmpty()) return new Reply("В текущем каталоге ничего не найдено по запросу «" + query + "». Уточните название или категорию.");
        StringJoiner answer = new StringJoiner("\n", "В каталоге:\n", "\nЦены и остатки могут измениться.");
        matches.stream().limit(10).forEach(p -> answer.add(p.getName() + " — " + money(BigDecimal.valueOf(p.getPrice())) + (p.getStock() > 0 ? ", в наличии: " + p.getStock() : ", нет в наличии")));
        if (matches.size() > 10) answer.add("Показано 10 из " + matches.size() + ". Уточните запрос.");
        return new Reply(answer.toString(), "Нашёл товары в текущем каталоге. Цены и наличие могут измениться.", List.of(),
                List.of(new ProductsContent(matches.stream().limit(10).map(ProductInfo::from).toList(), matches.size())), null, List.of());
    }
    private Reply recommend() {
        if (knowledge.components().isEmpty()) return new Reply("В каталоге пока нет комплектующих для подбора. Бюджет " + money(budget) + " запомнил. " + warnings());
        var result = new BuildRecommendationService().recommend(knowledge.components(), BuildRecommendationService.Request.forUsage(budget, usage));
        if (result.components().isEmpty()) return new Reply(String.join("\n", result.explanations()) + "\nБюджет: " + money(budget) + ".\n" + warnings());
        StringJoiner answer = new StringJoiner("\n");
        answer.add(result.status() == BuildRecommendationService.Status.READY ? "Вариант сборки:" : "Черновой вариант — совместимость ещё не подтверждена:");
        result.components().forEach(c -> answer.add(c.category().displayName() + ": " + c.name() + " — " + money(c.price())));
        answer.add("Итого: " + money(result.purchaseTotal()) + " из " + money(budget));
        result.explanations().forEach(answer::add);
        result.compatibility().findings().stream().filter(f -> f.status() != CompatibilityService.Status.COMPATIBLE).limit(5).forEach(f -> answer.add("• " + f.message()));
        if (!knowledge.warnings().isEmpty()) answer.add(warnings());
        var ids = result.components().stream().map(CatalogComponent::productId).toList();
        var selected = result.components().stream().map(c -> products.stream().filter(p -> p.getId() == c.productId()).findFirst().orElseThrow()).toList();
        var findings = result.compatibility().findings().stream().filter(f -> f.status() != CompatibilityService.Status.COMPATIBLE).map(CompatibilityService.Finding::message).toList();
        var limitations = new ArrayList<>(result.compatibility().limitations());
        limitations.addAll(result.explanations()); limitations.addAll(knowledge.warnings());
        boolean verified = result.status() == BuildRecommendationService.Status.READY;
        var card = new BuildContent(selected.stream().map(ProductInfo::from).toList(), result.purchaseTotal(), budget, usage, verified, findings, limitations);
        return new Reply(answer.toString(), verified ? "Подготовил вариант в вашем бюджете." : "Подготовил черновой вариант. Перед покупкой нужно проверить характеристики деталей.",
                selected, List.of(card), null, List.of("Что такое совместимость?"));
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
