package com.example.authapp.services;

import com.example.authapp.models.Product;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;

/** A draft is committed only after the controller's generation/account check. */
public interface ChatEngine {
    enum Mode { RULES, LOCAL_MODEL, FALLBACK, CLARIFICATION }
    ChatEngine fork();
    void commitConversation(ChatEngine draft);
    ChatBotService.Reply reply(String text);
    ChatBotService.RequestSummary summary();
    void updateCatalog(Collection<Product> products);
    String status();
    Mode mode();

    static ChatEngine create(Collection<Product> products) {
        var rules = new ChatBotService(products);
        String engine = setting("engine", "TECHSTORE_CHAT_ENGINE", "hybrid");
        if (engine.equals("rules")) return new LocalChatEngine(rules, null, "Помощник готов");
        if (!engine.equals("hybrid")) return new LocalChatEngine(rules, null, LocalChatEngine.CONFIG_ERROR);
        try {
            var client = new OllamaIntentClient(
                    URI.create(setting("url", "TECHSTORE_CHAT_URL", "http://127.0.0.1:11434")),
                    setting("model", "TECHSTORE_CHAT_MODEL", "qwen3:14b"), Duration.ofSeconds(60));
            return new LocalChatEngine(rules, client, "Помощник готов");
        } catch (IllegalArgumentException ex) {
            return new LocalChatEngine(rules, null, LocalChatEngine.CONFIG_ERROR);
        }
    }

    private static String setting(String name, String environment, String fallback) {
        String value = System.getProperty("techstore.chat." + name);
        if (value == null || value.isBlank()) value = System.getenv(environment);
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
