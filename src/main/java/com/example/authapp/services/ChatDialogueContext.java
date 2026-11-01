package com.example.authapp.services;

import java.util.List;

/** Authoritative state, separate from bounded conversational hints. No account or credentials. */
record ChatDialogueContext(ChatBotService.RequestSummary request, boolean buildActive,
                           List<String> comparisonModels, String comparisonPurpose,
                           String comparisonDetails, List<String> recentMessages, List<String> lastContentKinds) {
    ChatDialogueContext {
        comparisonModels = List.copyOf(comparisonModels); recentMessages = List.copyOf(recentMessages);lastContentKinds=List.copyOf(lastContentKinds);
    }
    ChatDialogueContext(ChatBotService.RequestSummary request,boolean buildActive,List<String> models,String purpose,String details,List<String> history) {
        this(request,buildActive,models,purpose,details,history,List.of());
    }
    ChatDialogueContext withContent(List<String> kinds) {return new ChatDialogueContext(request,buildActive,comparisonModels,comparisonPurpose,comparisonDetails,recentMessages,kinds);}
    static ChatDialogueContext empty(ChatBotService.RequestSummary request) {
        return new ChatDialogueContext(request, false, List.of(), "", "", List.of());
    }
}
