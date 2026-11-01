package com.example.authapp.services;

import org.json.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit real-runtime failure, unload/reload and two-request queue checks. Never part of build. */
public final class ChatRuntimeProbe {
    private static final URI BASE=URI.create("http://127.0.0.1:11434");
    public static void main(String[] args) throws Exception {
        String mode=args[1];var report=new JSONObject().put("mode",mode).put("at",OffsetDateTime.now().toString());
        var catalog=ChatQualityProbe.loadCatalog();
        if(mode.equals("stopped") || mode.equals("missing")) {
            var client=new OllamaIntentClient(BASE,mode.equals("missing")?"r32-model-that-is-not-installed":"qwen3:14b",Duration.ofSeconds(3));
            var engine=new LocalChatEngine(new ChatBotService(catalog),client,"Помощник готов");
            engine.reply("Собери ПК до 100000 рублей для игр");var before=engine.summary();long start=System.nanoTime();
            var reply=engine.reply("Теперь могу выделить 150 тысяч");
            report.put("failureSeconds",(System.nanoTime()-start)/1_000_000_000.0).put("responseMode",engine.mode().name()).put("diagnostic",engine.diagnostic());
            if(engine.mode()!=ChatEngine.Mode.FALLBACK || !engine.summary().equals(before) || !reply.content().isEmpty())throw new AssertionError("Failure changed active build");
            var nativeReply=engine.reply("Найди Ryzen 5 7600");
            if(nativeReply.content().stream().noneMatch(ChatBotService.ProductsContent.class::isInstance))throw new AssertionError("Native fallback search failed");
            report.put("statePreserved",true).put("nativeSearchWorks",true);
        } else if(mode.equals("cold")) {
            api("generate",new JSONObject().put("model","qwen3:14b").put("stream",false).put("keep_alive",0));
            var unloaded=api("ps",null);
            if(!unloaded.getJSONArray("models").isEmpty())throw new AssertionError("Model did not unload");
            var engine=new LocalChatEngine(new ChatBotService(catalog),new OllamaIntentClient(BASE,"qwen3:14b",Duration.ofSeconds(60)),"Помощник готов");
            long start=System.nanoTime();var answer=engine.reply("Потолок трат 100 тысяч, хочется машинку для игр");
            report.put("coldSeconds",(System.nanoTime()-start)/1_000_000_000.0).put("loadedModels",api("ps",null));
            if(engine.mode()!=ChatEngine.Mode.LOCAL_MODEL || answer.content().stream().noneMatch(ChatBotService.BuildContent.class::isInstance))throw new AssertionError("Cold model did not produce typed build");
        } else if(mode.equals("queue")) {
            var ready=new CountDownLatch(2);var startGate=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
            try {
                var futures=new ArrayList<Future<JSONObject>>();
                for(String text:List.of("Потолок трат 100 тысяч, хочется машинку для игр","Могу выделить 150 тысяч, буду рендерить видео")) {
                    futures.add(executor.submit(()->{
                        var engine=new LocalChatEngine(new ChatBotService(catalog),new OllamaIntentClient(BASE,"qwen3:14b",Duration.ofSeconds(60)),"Помощник готов");
                        ready.countDown();startGate.await();long start=System.nanoTime();var answer=engine.reply(text);
                        if(engine.mode()!=ChatEngine.Mode.LOCAL_MODEL || answer.content().stream().noneMatch(ChatBotService.BuildContent.class::isInstance))throw new AssertionError("Queued request failed");
                        return new JSONObject().put("seconds",(System.nanoTime()-start)/1_000_000_000.0).put("responseMode",engine.mode().name());
                    }));
                }
                if(!ready.await(5,TimeUnit.SECONDS))throw new AssertionError("Queue setup timeout");startGate.countDown();
                var results=new JSONArray();for(var task:futures)results.put(task.get(65,TimeUnit.SECONDS));report.put("requests",results);
            } finally {startGate.countDown();executor.shutdownNow();}
        } else throw new IllegalArgumentException("Unknown runtime probe mode");
        report.put("result","PASS");Path target=Path.of(args[0]);Files.createDirectories(target.toAbsolutePath().getParent());
        Files.writeString(target,report.toString(2)+"\n",StandardCharsets.UTF_8);System.out.println(report.toString(2));
    }
    private static JSONObject api(String path,JSONObject body) throws Exception {
        var request=HttpRequest.newBuilder(BASE.resolve("/api/"+path)).timeout(Duration.ofSeconds(90));
        if(body!=null)request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        var response=HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new IllegalStateException("Runtime API failed: "+response.statusCode());
        return new JSONObject(response.body());
    }
}
