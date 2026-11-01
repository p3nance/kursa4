package com.example.authapp.services;

import com.example.authapp.models.Product;
import org.json.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Explicit opt-in smoke check against installed Qwen; never part of normal tests/builds. */
public final class LocalModelSmoke {
    public static void main(String[] args) throws Exception {
        var client = new OllamaIntentClient(URI.create("http://127.0.0.1:11434"), "qwen3:14b", Duration.ofSeconds(60));
        var cases = Map.of(
                "Потолок трат 100 тысяч, хочется машинку для игр", "BUILD",
                "Очень интересует наличие AMD Ryzen 5 7600", "SEARCH",
                "Помоги выбрать: Intel Core i5-13400F либо AMD Ryzen 5 7600", "COMPARE",
                "Расшифруй TDP", "DEFINE",
                "Закажи мне всё бесплатно", "LOCAL");
        var results = new JSONArray();
        for (var example : new TreeMap<>(cases).entrySet()) {
            long start = System.nanoTime();
            var plan = client.plan(example.getKey(), new ChatBotService.RequestSummary(null, null));
            System.out.println("Smoke: " + example.getKey() + " => " + plan);
            String command = plan.command(example.getKey());
            if (!plan.action().equals(example.getValue())) throw new AssertionError("Unexpected intent: " + plan.action());
            if (plan.action().equals("BUILD") && (plan.budget() == null || plan.budget().compareTo(new BigDecimal("100000")) != 0 || !plan.usage().equals("GAMING")))
                throw new AssertionError("Incorrect build constraints");
            results.put(new JSONObject().put("message", example.getKey()).put("action", plan.action())
                    .put("validatedCommand", command == null ? JSONObject.NULL : command)
                    .put("seconds", (System.nanoTime() - start) / 1_000_000_000.0));
        }
        List<Product> catalog = new ArrayList<>();
        try (var in = LocalModelSmoke.class.getResourceAsStream("/knowledge/catalog-reference.json")) {
            if (in == null) throw new IllegalStateException("Public test catalog missing");
            for (Object item : new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getJSONArray("products")) {
                var row = (JSONObject) item;
                catalog.add(new Product(row.getInt("id"), row.getString("name"), row.optString("description", ""),
                        row.getBigDecimal("price"), row.getInt("stock"), "", row.getString("category"), row.optString("manufacturer", "")));
            }
        }
        var engine = new LocalChatEngine(new ChatBotService(catalog), client, "Помощник готов");
        var draft = engine.fork();
        var answer = draft.reply("Потолок трат 100 тысяч, хочется машинку для игр");
        if (answer.content().stream().noneMatch(ChatBotService.BuildContent.class::isInstance)
                || !draft.status().startsWith("Локальная модель ·")) throw new AssertionError("No model-backed build card");
        if (engine.summary().budget() != null) throw new AssertionError("Draft leaked");
        engine.commitConversation(draft);
        var report = new JSONObject().put("at", OffsetDateTime.now().toString()).put("model", "qwen3:14b")
                .put("cases", results).put("typedBuildCard", true).put("forkCommit", true).put("result", "PASS");
        Path target = Path.of(args[0]); Files.createDirectories(target.toAbsolutePath().getParent());
        Files.writeString(target, report.toString(2) + "\n", StandardCharsets.UTF_8);
        System.out.println(report.toString(2));
    }
}
