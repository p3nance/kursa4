package com.example.authapp.services;

import com.example.authapp.domain.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CancellationException;
import static com.example.authapp.domain.ComponentSpecs.*;

/** Bounded, deterministic search. It never invents performance scores or orders products. */
public final class BuildRecommendationService {
    public enum Usage { OFFICE, GAMING, WORKSTATION }
    public enum Status { READY, NEEDS_VERIFICATION, NO_MATCH, SEARCH_LIMIT }
    public record Request(BigDecimal budget, Usage usage, int minRamGb, int minStorageGb,
                          Map<ComponentCategory,CatalogComponent> ownedComponents, boolean requireVerified) {
        public Request {
            Objects.requireNonNull(budget); Objects.requireNonNull(usage);
            if (budget.signum() <= 0 || minRamGb < 1 || minStorageGb < 1)
                throw new IllegalArgumentException("Нужны положительные бюджет и минимальные объёмы памяти");
            ownedComponents = Map.copyOf(ownedComponents);
            for (var e : ownedComponents.entrySet()) if(e.getKey()!=e.getValue().category())
                throw new IllegalArgumentException("Категория имеющегося компонента не совпадает с ключом");
        }
        public static Request forBudget(BigDecimal budget) {
            return forUsage(budget,Usage.GAMING);
        }
        public static Request forUsage(BigDecimal budget,Usage usage) {
            return switch(usage) {
                case OFFICE -> new Request(budget,usage,8,256,Map.of(),false);
                case GAMING -> new Request(budget,usage,16,500,Map.of(),false);
                case WORKSTATION -> new Request(budget,usage,32,1000,Map.of(),false);
            };
        }
    }
    public record Recommendation(Status status, List<CatalogComponent> components, BigDecimal purchaseTotal,
                                 CompatibilityService.Result compatibility, List<String> explanations,
                                 int combinationsExamined, boolean searchTruncated) {
        public Recommendation { components=List.copyOf(components); explanations=List.copyOf(explanations); }
    }
    private final CompatibilityService compatibility = new CompatibilityService();
    private final int maxCandidatesPerCategory;
    private final int maxCombinations;
    public BuildRecommendationService() { this(12,50_000); }
    public BuildRecommendationService(int maxCandidatesPerCategory,int maxCombinations) {
        if(maxCandidatesPerCategory<1 || maxCombinations<1) throw new IllegalArgumentException("Лимиты должны быть положительными");
        this.maxCandidatesPerCategory=maxCandidatesPerCategory; this.maxCombinations=maxCombinations;
    }
    public Recommendation recommend(Collection<CatalogComponent> catalog,Request request) {
        Objects.requireNonNull(catalog); Objects.requireNonNull(request);
        checkCancelled();
        var groups = new EnumMap<ComponentCategory,List<CatalogComponent>>(ComponentCategory.class);
        var messages = new ArrayList<String>(); boolean capped=false;
        for(var category:ComponentCategory.values()) {
            checkCancelled();
            var owned=request.ownedComponents().get(category);
            if(owned!=null) {
                if(!capacityPermits(owned,request)) messages.add("Имеющийся компонент не отвечает минимуму объёма: "+category.displayName());
                groups.put(category,List.of(owned)); continue;
            }
            var candidates=catalog.stream().peek(c->checkCancelled()).filter(c->c.category()==category && c.stock()>0 && c.price().signum()>0)
                    .filter(c->capacityPermits(c,request)).sorted(Comparator.comparing(CatalogComponent::price)
                            .thenComparingInt(CatalogComponent::productId)).toList();
            if(candidates.isEmpty()) messages.add("Нет подходящего товара в наличии: "+category.displayName());
            if(candidates.size()>maxCandidatesPerCategory) capped=true;
            groups.put(category,candidates.stream().limit(maxCandidatesPerCategory).toList());
        }
        if(!messages.isEmpty()) return new Recommendation(Status.NO_MATCH,List.of(),BigDecimal.ZERO,
                compatibility.assess(List.of()),messages,0,capped);
        Search search=new Search(request,groups,capped);
        search.visit(0,new ArrayList<>(),BigDecimal.ZERO);
        checkCancelled();
        if(search.best==null) {
            String reason=search.truncated?"Поиск ограничен: среди проверенных вариантов сборка не найдена.":
                    "Сборка в бюджете с указанными ограничениями не найдена.";
            return new Recommendation(search.truncated?Status.SEARCH_LIMIT:Status.NO_MATCH,List.of(),BigDecimal.ZERO,
                    compatibility.assess(List.of()),List.of(reason,"Бюджет учитывает только покупаемые компоненты."),search.examined,search.truncated);
        }
        var assessment=compatibility.assess(search.best);
        boolean unknownCapacity=search.best.stream().anyMatch(c->capacityUnknown(c,request));
        Status status=assessment.status()==CompatibilityService.Status.COMPATIBLE && !unknownCapacity?Status.READY:Status.NEEDS_VERIFICATION;
        var explanations=new ArrayList<String>();
        explanations.add("Подобраны все 8 категорий системного блока из доступного каталога; периферия и ОС не включены.");
        explanations.add("Приоритет — проверенная совместимость и минимальная цена среди рассмотренных вариантов. Цена не используется как оценка производительности.");
        explanations.add("Базовая сборка, без прогноза FPS и скорости рабочих задач. Минимальные объёмы — стартовые требования, их можно изменить.");
        explanations.add("Сценарий: "+request.usage()+". Минимум RAM: "+request.minRamGb()+" ГБ; накопитель: "+request.minStorageGb()+" ГБ.");
        if(status==Status.NEEDS_VERIFICATION) explanations.add("Это черновик: не хватает проверенных характеристик. Перед покупкой требуется проверка совместимости и объёмов.");
        if(search.truncated) explanations.add("Поиск ограничен числом кандидатов/комбинаций; глобальная оптимальность не гарантируется.");
        explanations.add("Цена и остатки — снимок каталога; перепроверьте перед добавлением в корзину. Имеющиеся детали в стоимость покупки не входят.");
        return new Recommendation(status,search.best,search.bestPrice,assessment,explanations,search.examined,search.truncated);
    }
    private static void checkCancelled() {
        if(Thread.currentThread().isInterrupted()) throw new CancellationException("Подбор отменён");
    }
    private static boolean capacityPermits(CatalogComponent c,Request r) {
        int min=c.category()==ComponentCategory.RAM?r.minRamGb():c.category()==ComponentCategory.STORAGE?r.minStorageGb():0;
        if(min==0) return true;
        return c.specs().fact(CAPACITY_GB).map(f->!f.verified() || f.value()>=min).orElse(true);
    }
    private static boolean capacityUnknown(CatalogComponent c,Request r) {
        if(c.category()!=ComponentCategory.RAM && c.category()!=ComponentCategory.STORAGE) return false;
        return c.specs().fact(CAPACITY_GB).map(f->!f.verified()).orElse(true);
    }
    private final class Search {
        private final Request request;
        private final Map<ComponentCategory,List<CatalogComponent>> groups;
        private final BigDecimal[] minimumRemaining=new BigDecimal[ComponentCategory.values().length+1];
        private List<CatalogComponent> best;
        private BigDecimal bestPrice;
        private int bestUnknown=Integer.MAX_VALUE,examined,visitedNodes;
        private boolean truncated;
        Search(Request r,Map<ComponentCategory,List<CatalogComponent>> groups,boolean truncated) {
            request=r;this.groups=groups;this.truncated=truncated;
            minimumRemaining[ComponentCategory.values().length]=BigDecimal.ZERO;
            for(int i=ComponentCategory.values().length-1;i>=0;i--) {
                var category=ComponentCategory.values()[i];
                var minimum=r.ownedComponents().containsKey(category)?BigDecimal.ZERO:groups.get(category).getFirst().price();
                minimumRemaining[i]=minimumRemaining[i+1].add(minimum);
            }
        }
        void visit(int index,List<CatalogComponent> selected,BigDecimal price) {
            checkCancelled();
            if(visitedNodes++>=maxCombinations) {truncated=true;return;}
            if(price.add(minimumRemaining[index]).compareTo(request.budget())>0) return;
            if(index==ComponentCategory.values().length) {
                examined++;
                var result=compatibility.assess(selected);
                if(result.hasConflict()) return;
                int unknown=(int)result.findings().stream().filter(f->f.status()==CompatibilityService.Status.INSUFFICIENT_DATA).count();
                unknown+=(int)selected.stream().filter(c->capacityUnknown(c,request)).count();
                if(request.requireVerified() && unknown>0) return;
                if(best==null || unknown<bestUnknown || (unknown==bestUnknown && price.compareTo(bestPrice)<0)) {
                    best=List.copyOf(selected);bestPrice=price;bestUnknown=unknown;
                }
                return;
            }
            var category=ComponentCategory.values()[index];
            for(var candidate:groups.get(category)) {
                checkCancelled();
                BigDecimal cost=request.ownedComponents().containsKey(category)?BigDecimal.ZERO:candidate.price();
                BigDecimal next=price.add(cost);
                if(next.compareTo(request.budget())>0) continue;
                selected.add(candidate);
                if(!compatibility.assess(selected,false).hasConflict()) visit(index+1,selected,next);
                selected.remove(selected.size()-1);
                if(visitedNodes>=maxCombinations) {truncated=true;break;}
            }
        }
    }
}
