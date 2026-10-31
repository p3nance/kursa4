package com.example.authapp.services;

import com.example.authapp.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OwnedBuildConversationTest {
    private final CatalogComponent gpu=part(1,"NVIDIA GeForce RTX 4070 12GB",ComponentCategory.GPU,0,"50000.00");
    private final CatalogComponent cpu=part(2,"AMD Ryzen 5 7600",ComponentCategory.CPU,5,"15000.00");
    private final CatalogComponent intel=part(3,"Intel Core i5-14600K",ComponentCategory.CPU,5,"20000.00");
    @Test void onlyExplicitOwnershipIsHandledAndMetadataIsKeptOutsideModelNames() {
        var state=state();assertFalse(state.respond("Собери ПК с RTX 4070").handled());assertTrue(state.components().isEmpty());
        var answer=state.respond("У меня уже есть RTX 4070, собери остальное до 80 тысяч для игр");
        assertTrue(answer.handled());assertNull(answer.response());assertTrue(answer.remaining().contains("80 тысяч"));
        assertEquals(Map.of(ComponentCategory.GPU,gpu),state.components());assertFalse(state.pending());
        state.clear();assertNull(state.respond("RTX 4070 у меня уже есть, подбери остальное").response());assertEquals(1,state.components().size());
    }
    @Test void aBatchIsAtomicUntilEveryModelHasBeenResolved() {
        var state=state();var answer=state.respond("У меня есть RTX 4070 и Ryzen 9 9990X");
        assertNotNull(answer.response());assertTrue(state.components().isEmpty());assertTrue(state.pending());
        answer=state.respond("Ryzen 5 7600");assertNull(answer.response());assertEquals(2,state.components().size());
        assertEquals(gpu,state.components().get(ComponentCategory.GPU));
    }
    @Test void cancellationDiscardsStagedPartsAndDoesNotRemoveEarlierAcceptedOwnership() {
        var state=state();state.respond("У меня есть Ryzen 5 7600");
        state.respond("У меня есть RTX 4070 и Ryzen 9 9990X");assertTrue(state.pending());
        var result=state.respond("Отменить уточнение");assertNotNull(result.response());assertFalse(state.pending());
        assertEquals(Map.of(ComponentCategory.CPU,cpu),state.components());
    }
    @Test void ambiguousModelRequiresChoiceAndTheChoiceNumberIsNotBuildMetadata() {
        var a=part(4,"ASUS GeForce RTX 4070 Dual 12GB",ComponentCategory.GPU,2,"51000.00");
        var b=part(5,"Palit GeForce RTX 4070 Dual 12GB",ComponentCategory.GPU,2,"52000.00");
        var state=new OwnedBuildConversation(List.of(a,b,cpu));var result=state.respond("У меня есть RTX 4070 и Ryzen 5 7600, собери ПК до 100000 рублей для игр");
        assertNotNull(result.response());assertTrue(state.components().isEmpty());assertTrue(result.remaining().contains("100000"));
        assertNotNull(state.respond("да").response());assertTrue(state.components().isEmpty());
        result=state.respond("2");assertNull(result.response());assertEquals("",result.remaining());
        assertEquals(b,state.components().get(ComponentCategory.GPU));assertEquals(cpu,state.components().get(ComponentCategory.CPU));
    }
    @Test void possibleTypoAndHypotheticalOwnershipNeedConfirmation() {
        var state=state();var answer=state.respond("У меня есть NVIDA GeForce RTX 4070 12GB");
        assertNotNull(answer.response());assertTrue(state.components().isEmpty());assertTrue(answer.response().text().contains("подтверждение"));
        assertNull(state.respond("Да").response());assertEquals(1,state.components().size());
        state.clear();answer=state.respond("Если у меня есть RTX 4070, собери ПК до 100000 рублей");
        assertNotNull(answer.response());assertTrue(state.components().isEmpty());state.respond("нет");assertTrue(state.components().isEmpty());
    }
    @Test void twoOwnedModelsForOneSlotRequireAnExplicitConflictChoice() {
        var state=state();var answer=state.respond("У меня есть Ryzen 5 7600 и i5-14600K");
        assertNotNull(answer.response());assertTrue(state.components().isEmpty());
        assertNull(state.respond("Выбираю имеющуюся деталь: Intel Core i5-14600K").response());
        assertEquals(Map.of(ComponentCategory.CPU,intel),state.components());
    }
    @Test void copiesCommitsAndCatalogRefreshCannotTransferOwnershipToAReusedId() {
        var state=state();state.respond("У меня есть RTX 4070");var draft=state.copy();draft.respond("У меня есть Ryzen 5 7600");
        assertEquals(1,state.components().size());state.commit(draft);assertEquals(2,state.components().size());
        var cheaper=part(1,gpu.name(),ComponentCategory.GPU,8,"1.00");state=state.refreshed(List.of(cheaper,cpu,intel));
        assertEquals(new BigDecimal("1.00"),state.components().get(ComponentCategory.GPU).price());
        var replaced=part(1,"NVIDIA GeForce RTX 4090 24GB",ComponentCategory.GPU,8,"1.00");state=state.refreshed(List.of(replaced,cpu,intel));
        assertFalse(state.components().containsKey(ComponentCategory.GPU));assertNotNull(state.problem());
        state.commit(draft);assertFalse(state.components().containsKey(ComponentCategory.GPU));assertNotNull(state.problem());
        state.respond("Сбросить имеющиеся детали");assertTrue(state.components().isEmpty());assertNull(state.problem());
    }
    @Test void missingOwnedCatalogEntryBlocksBuildInsteadOfBecomingAPurchase() {
        var state=state();state.respond("У меня есть RTX 4070");state=state.refreshed(List.of(cpu,intel));
        assertTrue(state.components().isEmpty());assertNotNull(state.problem());assertTrue(state.problem().text().contains("Не заменяю"));
    }
    @Test void unknownGpuCannotBeReplacedByAnAnswerFromAnotherCategory() {
        var state=state();state.respond("У меня есть RTX 9999");assertNotNull(state.respond("Ryzen 5 7600").response());
        assertTrue(state.components().isEmpty());
    }
    @Test void eachHypotheticalModelNeedsItsOwnConfirmationAndTheBatchRemainsAtomic() {
        var state=state();var answer=state.respond("Если у меня есть RTX 4070 и Ryzen 5 7600, собери ПК");
        assertNotNull(answer.response());assertTrue(state.components().isEmpty());
        assertNotNull(state.respond("Да").response());assertTrue(state.components().isEmpty());
        assertNull(state.respond("Да").response());assertEquals(2,state.components().size());
    }
    @Test void unknownBudgetLikeTextDoesNotLoseThePendingHardwareCategory() {
        var state=state();state.respond("У меня есть RTX 9999");state.respond("100000");
        assertNotNull(state.respond("Ryzen 5 7600").response());assertTrue(state.components().isEmpty());
    }
    @Test void declarationAsAnAnswerPreservesTheOtherStagedParts() {
        var state=state();state.respond("У меня есть RTX 4070 и Ryzen 9 9990X, собери ПК до 150000 рублей для игр");
        assertTrue(state.components().isEmpty());
        assertNull(state.respond("У меня есть Ryzen 5 7600").response());
        assertEquals(Map.of(ComponentCategory.GPU,gpu,ComponentCategory.CPU,cpu),state.components());
    }
    @Test void originalGenericCategorySurvivesCleaningAndRejectsAWrongCategory() {
        for(String type:List.of("видеокарта","оперативная память","накопитель","материнская плата","блок питания","корпус","кулер")) {
            var state=state();state.respond("У меня есть "+type+" Mystery");
            assertNotNull(state.respond("Ryzen 5 7600").response(),type);assertTrue(state.components().isEmpty(),type);
        }
        var state=state();state.respond("У меня есть видеокарта Ryzen 5 7600");assertTrue(state.components().isEmpty());
        assertNull(state.respond("RTX 4070").response());assertEquals(gpu,state.components().get(ComponentCategory.GPU));
    }
    @Test void negatedAndQuotedStatementsCannotGrantOwnership() {
        for(String phrase:List.of("Собери ПК до 100000 рублей для игр, но не считай, что у меня есть RTX 4070","Неверно, что у меня есть RTX 4070","Не надо считать, что у меня есть RTX 4070")) {
            var state=state();assertNotNull(state.respond(phrase).response(),phrase);assertTrue(state.components().isEmpty(),phrase);
        }
        var state=state();assertNotNull(state.respond("«У меня есть RTX 4070»").response());assertTrue(state.components().isEmpty());
        assertNull(state.respond("Да").response());assertEquals(gpu,state.components().get(ComponentCategory.GPU));
        state.clear();assertNull(state.respond("«RTX 4070» у меня уже есть").response());assertEquals(gpu,state.components().get(ComponentCategory.GPU));
    }
    @Test void explicitAbsenceRemovesOnlyContradictedOwnershipAndDoesNotRecommendAutomatically() {
        var state=state();state.respond("У меня есть RTX 4070 и Ryzen 5 7600");
        assertNotNull(state.respond("У меня нет RTX 4070").response());assertEquals(Map.of(ComponentCategory.CPU,cpu),state.components());
        state.respond("У меня есть RTX 4070");state.respond("У меня нет видеокарты");assertFalse(state.components().containsKey(ComponentCategory.GPU));
    }
    @Test void naturalNeedClauseIsBuildMetadataRatherThanAnotherOwnedModel() {
        for(String next:List.of("нужно собрать ПК","нужен компьютер","нужна сборка")) {
            var state=state();var result=state.respond("У меня есть RTX 4070, "+next+" до 80 тысяч для игр");
            assertNull(result.response(),next);assertEquals(Map.of(ComponentCategory.GPU,gpu),state.components());assertTrue(result.remaining().contains("80 тысяч"));
        }
    }
    @Test void nonMatchingCategoryInADeclarationCannotDiscardAPendingSlotOrAStagedGpu() {
        var state=state();state.respond("У меня есть RTX 4070 и Ryzen 9 9990X");
        assertNotNull(state.respond("У меня есть RTX 4070").response());assertTrue(state.components().isEmpty());
        assertNull(state.respond("У меня есть Ryzen 5 7600").response());assertEquals(2,state.components().size());
    }
    @Test void suggestionsAndClarificationsCannotOverrideTheExplicitHardwareCategory() {
        var state=state();state.respond("У меня есть видеокарта Rzyen 5 7600");
        assertNotNull(state.respond("Да").response());assertTrue(state.components().isEmpty());
        assertNull(state.respond("RTX 4070").response());assertEquals(Map.of(ComponentCategory.GPU,gpu),state.components());
        state.clear();state.respond("У меня есть видеокарта Mystery");state.respond("Rzyen 5 7600");
        assertNotNull(state.respond("Да").response());assertTrue(state.components().isEmpty());
        state.respond("RTX 4070");assertEquals(Map.of(ComponentCategory.GPU,gpu),state.components());
        state.clear();state.respond("У меня есть Rzyen 5 7600");
        assertNotNull(state.respond("видеокарта Ryzen 5 7600").response());assertTrue(state.components().isEmpty());
        assertNull(state.respond("Да").response());assertEquals(Map.of(ComponentCategory.CPU,cpu),state.components());
        state.clear();state.respond("У меня есть процессор RTX 4070");assertTrue(state.components().isEmpty());
        assertNull(state.respond("Ryzen 5 7600").response());assertEquals(Map.of(ComponentCategory.CPU,cpu),state.components());
    }
    @Test void conditionalQuotedAndQuestionDenialsPreserveTheAcceptedOwnership() {
        var state=state();state.respond("У меня есть RTX 4070");
        for(String phrase:List.of("Если у меня нет RTX 4070, собери ПК до 100000 рублей","«У меня нет RTX 4070»","Правда ли, что у меня нет RTX 4070?","Если неверно, что у меня есть RTX 4070, собери ПК до 100000 рублей","«Неверно, что у меня есть RTX 4070»","Неверно, что у меня есть RTX 4070?")) {
            assertNotNull(state.respond(phrase).response(),phrase);assertEquals(Map.of(ComponentCategory.GPU,gpu),state.components(),phrase);
        }
        state.respond("У меня нет RTX 4070");assertTrue(state.components().isEmpty());
    }
    @Test void questionsAboutOwnershipRequireConfirmationRatherThanGrantingIt() {
        for(String phrase:List.of("Правда ли, что у меня есть RTX 4070?","У меня есть RTX 4070?")) {
            var state=state();assertNotNull(state.respond(phrase).response(),phrase);assertTrue(state.components().isEmpty(),phrase);
            assertNull(state.respond("Да").response());assertEquals(Map.of(ComponentCategory.GPU,gpu),state.components());
        }
    }

    private OwnedBuildConversation state(){return new OwnedBuildConversation(List.of(gpu,cpu,intel));}
    private static CatalogComponent part(int id,String name,ComponentCategory category,int stock,String price) {
        return new CatalogComponent(id,name,category,new BigDecimal(price),stock,ComponentSpecs.empty());
    }
}
