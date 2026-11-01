package com.example.authapp.services;

import com.example.authapp.models.Product;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;

/** First integration: normalize otherwise unrecognized messages; all facts/cards remain in Java. */
final class LocalChatEngine implements ChatEngine {
    static final String CONFIG_ERROR = "Отвечаю по правилам: проверьте настройки локальной модели.";
    static final String FALLBACK = "Отвечаю по правилам: локальная модель недоступна или ответ не распознан.";
    private final ChatBotService rules;
    private final OllamaIntentClient client;
    private final String initialStatus;
    private String status;
    private List<String> history=List.of();
    private Mode mode=Mode.RULES;
    private final Object conversationId;
    private long catalogRevision;
    private String diagnostic="";
    private LocalChatPlan lastPlan;
    private ChatBotService.Reply previousReply;
    private ChatBotService.Reply referenceGroup;
    private boolean usedModel;

    LocalChatEngine(ChatBotService rules, OllamaIntentClient client, String initialStatus) {
        this(rules,client,initialStatus,new Object());
    }
    private LocalChatEngine(ChatBotService rules,OllamaIntentClient client,String initialStatus,Object conversationId) {
        this.rules = rules; this.client = client; this.initialStatus = initialStatus; status = initialStatus;
        this.conversationId=conversationId;
    }
    @Override public synchronized ChatEngine fork() {
        var draft = new LocalChatEngine(rules.fork(), client, initialStatus,conversationId); draft.status = status; draft.history=history; draft.mode=mode; draft.catalogRevision=catalogRevision; draft.previousReply=previousReply; draft.referenceGroup=referenceGroup; return draft;
    }
    @Override public void commitConversation(ChatEngine draft) {
        if (!(draft instanceof LocalChatEngine local) || local.client != client || local.conversationId!=conversationId)
            throw new IllegalArgumentException("Несовместимый черновик диалога");
        synchronized (this) { rules.commitConversation(local.rules); status = local.status; history=catalogRevision==local.catalogRevision?local.history:List.of(); previousReply=catalogRevision==local.catalogRevision?local.previousReply:null; referenceGroup=catalogRevision==local.catalogRevision?local.referenceGroup:null; mode=local.mode; diagnostic=local.diagnostic; lastPlan=local.lastPlan; usedModel=local.usedModel; }
    }
    @Override public synchronized ChatBotService.RequestSummary summary() { return rules.summary(); }
    @Override public synchronized void updateCatalog(Collection<Product> products) { rules.updateCatalog(products); history=List.of(); previousReply=null; referenceGroup=null; catalogRevision++; }
    @Override public synchronized String status() { return status; }
    @Override public synchronized Mode mode() {return mode;}
    synchronized String diagnostic() {return diagnostic;}
    synchronized LocalChatPlan lastPlan() {return lastPlan;}
    synchronized boolean usedModel() {return usedModel;}

    @Override public synchronized ChatBotService.Reply reply(String text) {
        status = initialStatus;
        mode=initialStatus.equals(CONFIG_ERROR)?Mode.FALLBACK:Mode.RULES;
        diagnostic="";lastPlan=null;usedModel=false;
        if(rules.buildEditRequest(text)) {
            var answer=rules.reply(text);previousReply=answer;referenceGroup=ChatProductContext.hasOrderedProducts(answer)?answer:null;
            if(new ChatIntentParser().parse(text).intent()==ChatIntentParser.Intent.RESET)history=List.of();else remember(text);
            status="Изменение предложенной сборки по каталогу.";return answer;
        }
        var unsupported=rules.unsupportedAction(text);
        if(unsupported.isPresent()){mode=Mode.CLARIFICATION;status="Действие доступно через соответствующий экран.";return unsupported.get();}
        var itemMoney=ChatItemMoney.problem(text);
        if(itemMoney.isPresent()) {
            mode=Mode.CLARIFICATION;status="Уточните предел цены отдельного товара.";
            return new ChatBotService.Reply(itemMoney.get(),itemMoney.get(),List.of(),List.of(),rules.summary(),List.of());
        }
        var budgetProblem=ChatBudgetInput.problem(text);
        if(budgetProblem.isPresent()) {
            mode=Mode.CLARIFICATION;status="Уточните сумму в рублях.";
            return new ChatBotService.Reply(budgetProblem.get(),budgetProblem.get(),List.of(),List.of(),rules.summary(),List.of("До 100000 рублей"));
        }
        if(new ChatIntentParser().parse(text).intent()==ChatIntentParser.Intent.SEARCH || rules.catalogPriceQuery(text)
                || !rules.hasPendingSelection() && ChatCatalogGroup.question(text)) {
            var filter=ChatCatalogFilter.parse(text);
            if(filter.problem()!=null) {
                mode=Mode.CLARIFICATION;status="Уточните ограничение цены товара.";
                return new ChatBotService.Reply(filter.problem(),filter.problem(),List.of(),List.of(),rules.summary(),List.of());
            }
        }
        if(!rules.hasPendingSelection() && ChatCatalogGroup.question(text) && !rules.hasExplicitModelMention(text)) {
            var result=ChatCatalogGroup.resolve(rules.productReferenceContext(referenceGroup!=null?referenceGroup:previousReply),text,rules.summary());
            if(result.resolved()){
                previousReply=result.reply();
                if(ChatCatalogFilter.parse(text).active())referenceGroup=ChatProductContext.hasOrderedProducts(previousReply)?previousReply:null;
                remember(text);status="Ответ по ценам и наличию показанных товаров.";
            }
            else {mode=Mode.CLARIFICATION;status="Уточните товары для сравнения.";}
            return result.reply();
        }
        if(!rules.hasPendingSelection() && ChatProductContext.referenceQuestion(text)) {
            if(rules.hasExplicitModelMention(text)) {
                var named=rules.namedProductReference(text);
                if(named.isPresent()){previousReply=named.get();referenceGroup=previousReply;remember(text);status="Ответ по названной модели.";return named.get();}
            } else if(previousReply!=null && previousReply.content().stream().anyMatch(ChatBotService.BuildContent.class::isInstance)
                    && !ChatProductContext.applies(previousReply,text)) {
                var explained=ChatGroundedExplanation.explain(previousReply,rules.summary());
                if(explained.isPresent()){remember(text);status="Ответ по данным сборки.";return explained.get();}
            }
        }
        if(!rules.hasPendingSelection() && (ChatProductContext.applies(previousReply,text)
                || ChatProductContext.simpleOrdinal(text) && ChatProductContext.hasOrderedProducts(previousReply)) && !rules.hasExplicitModelMention(text)) {
            var result=ChatProductContext.resolve(referenceFor(text),text,rules.summary());
            if(result.resolved()){rules.rememberBuilds(result.reply());previousReply=result.reply();remember(text);status="Ответ по карточке каталога.";}
            else {mode=Mode.CLARIFICATION;status="Уточните выбранный товар.";}
            return result.reply();
        }
        if (client == null || rules.routing(text)==ChatBotService.Routing.NATIVE) {
            var answer=rules.reply(text);
            previousReply=answer;
            referenceGroup=ChatProductContext.hasOrderedProducts(answer)?answer:null;
            if(text!=null && new ChatIntentParser().parse(text).intent()==ChatIntentParser.Intent.RESET)history=List.of();
            else remember(text);
            return answer;
        }
        try {
            var dialogue=rules.dialogueContext(history).withContent(previousReply==null?List.of():previousReply.content().stream().map(c->c.getClass().getSimpleName()).toList());
            usedModel=true;
            var plan = client.plan(text, dialogue);
            if(Thread.currentThread().isInterrupted())throw new CancellationException("Запрос отменён");
            lastPlan=plan;
            String command = plan.command(text);
            if(plan.action().equals("BUILD") && rules.catalogPriceQuery(text)) {
                mode=Mode.CLARIFICATION;status="Уточните поиск товара.";
                return new ChatBotService.Reply("Запрос относится к цене отдельного товара. Напишите «Найди» и название с пределом цены; бюджет ПК сохранён.",
                        "Уточните поиск товара с пределом цены в рублях.",List.of(),List.of(),rules.summary(),List.of());
            }
            if(plan.action().equals("CONTEXT") && dialogue.comparisonModels().size()!=2) {
                mode=Mode.CLARIFICATION;status="Выберите две модели для сравнения.";
                return new ChatBotService.Reply("Для такого сравнения выберите две точные модели и задачу. Могу сравнить их по характеристикам и доступным тестам.",
                        "Выберите две точные модели для сравнения.",List.of(),List.of(),rules.summary(),List.of("Сравни RTX 4070 SUPER и RTX 4070"));
            }
            if ((plan.action().equals("SEARCH") || plan.action().equals("COMPARE")) && !rules.preservesModelIdentity(text, plan.queries()))
                throw new IllegalArgumentException("Model variant changed by plan");
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("Запрос отменён");
            if(plan.action().equals("EXPLAIN")) {
                if(rules.hasExplicitModelMention(text)) {
                    mode=Mode.CLARIFICATION;status="Уточните модель для поиска.";
                    return new ChatBotService.Reply("В сообщении названа модель. Напишите «Найди» и её точное название, чтобы получить актуальную карточку.",
                            "Уточните модель для поиска.",List.of(),List.of(),rules.summary(),List.of());
                }
                if(ChatProductContext.applies(previousReply,text) || ChatProductContext.simpleOrdinal(text)) {
                    var result=ChatProductContext.resolve(referenceFor(text),text,rules.summary());
                    if(result.resolved()){previousReply=result.reply();remember(text);mode=Mode.LOCAL_MODEL;status="Локальная модель · данные карточки";}
                    else {mode=Mode.CLARIFICATION;status="Уточните выбранный товар.";}
                    return result.reply();
                }
                var explained=ChatGroundedExplanation.explain(previousReply,rules.summary());
                if(explained.isPresent()) {remember(text);mode=Mode.LOCAL_MODEL;status="Локальная модель · объяснение по данным карточки";return explained.get();}
                mode=Mode.CLARIFICATION;status="Сначала получите карточку товара, сборки или сравнения.";
                return new ChatBotService.Reply("Сначала найдите товар, подберите сборку или сравните модели. Затем смогу объяснить результат.",
                        "Сначала получите результат для объяснения.",List.of(),List.of(),rules.summary(),List.of("Собери ПК","Найди Ryzen"));
            }
            if (command != null) {
                var translated = rules.fork();
                var answer = translated.applyLanguagePlan(text,plan);
                rules.commitConversation(translated);
                previousReply=answer;
                referenceGroup=ChatProductContext.hasOrderedProducts(answer)?answer:null;
                remember(text);
                status = "Локальная модель · ответ по каталогу";
                mode=Mode.LOCAL_MODEL;
                return answer;
            }
            status = "Локальная модель: уточните запрос или выберите пример.";
            mode=Mode.CLARIFICATION;
        } catch (LocalChatPlan.InputProblem ex) {
            mode=Mode.CLARIFICATION;status="Уточните бюджет.";diagnostic=ex.getMessage();
            return new ChatBotService.Reply(ex.getMessage(),ex.getMessage(),List.of(),List.of(),rules.summary(),List.of("До 100000 рублей"));
        } catch (IOException | IllegalArgumentException ex) {
            status = FALLBACK;
            mode=Mode.FALLBACK;
            diagnostic=ex.getMessage();
        }
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Запрос отменён");
        return new ChatBotService.Reply("Уточните запрос: могу подобрать ПК, найти товар, сравнить две модели или объяснить термин.",
                "Уточните запрос: могу подобрать ПК, найти товар, сравнить две модели или объяснить термин.",List.of(),List.of(),rules.summary(),List.of("Собери ПК","Найди Ryzen","Что такое TDP?"));
    }
    private void remember(String text) {
        if(text==null || text.isBlank() || text.length()>2000)return;
        var next=new ArrayList<>(history);
        next.add(text.length()>300?text.substring(0,300):text);
        history=List.copyOf(next.subList(Math.max(0,next.size()-4),next.size()));
    }
    private ChatBotService.Reply referenceFor(String text) {
        return rules.productReferenceContext(ChatProductContext.usesGroup(text) && referenceGroup!=null?referenceGroup:previousReply);
    }
}
