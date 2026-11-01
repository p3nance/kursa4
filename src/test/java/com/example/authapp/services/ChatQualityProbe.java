package com.example.authapp.services;

import com.example.authapp.models.Product;
import org.json.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Opt-in paired evaluation on authored public-catalog scenarios; no production account or writes. */
public final class ChatQualityProbe {
    public static void main(String[] args) throws Exception {
        var catalog=loadCatalog();
        List<BenchmarkService.Result> published;
        try(var input=ChatQualityProbe.class.getResourceAsStream("/knowledge/benchmarks.json")) {
            if(input==null)throw new IllegalStateException("Packaged benchmark reference missing");
            published=new BenchmarkService().load(new java.io.InputStreamReader(input,StandardCharsets.UTF_8));
        }
        var cases=resource(args.length>2?args[2]:"/knowledge/chat-quality-cases.json").getJSONArray("cases");
        var client=new OllamaIntentClient(URI.create("http://127.0.0.1:11434"),"qwen3:14b",Duration.ofSeconds(60));
        var rows=new JSONArray();var report=new JSONObject().put("at",OffsetDateTime.now().toString())
                .put("model","qwen3:14b").put("description","Paired regression on authored scenarios, not production data or a held-out benchmark")
                .put("cases",rows);
        Path target=Path.of(args[0]);Files.createDirectories(target.toAbsolutePath().getParent());
        for(String mode:List.of("rules","hybrid")) {
            var timings=new ArrayList<Double>();var modelTimings=new ArrayList<Double>();int passed=0,total=0,modelCalls=0,accepted=0,fallbacks=0;
            for(Object item:cases) {
                var test=(JSONObject)item;
                if(args.length>1 && !args[1].isBlank() && !Arrays.asList(args[1].split(",")).contains(test.getString("id")))continue;
                var engine=new LocalChatEngine(new ChatBotService(catalog),mode.equals("rules")?null:client,"Помощник готов");
                ChatBotService.Reply setupReply=null;
                for(Object setup:test.getJSONArray("setup"))setupReply=engine.reply((String)setup);
                var before=engine.summary();var failures=new ArrayList<String>();
                long start=System.nanoTime();ChatBotService.Reply answer=null;
                try {
                    answer=engine.reply(test.getString("message"));
                    check(test.getJSONObject("expect"),answer,before,failures);
                    checkReference(test.getJSONObject("expect"),answer,setupReply,failures);
                    checkFacts(answer,catalog,test.getJSONObject("expect"),failures);
                    checkBenchmarks(test.getJSONObject("expect"),answer,published,failures);
                } catch(Exception ex) {failures.add(ex.getClass().getSimpleName()+": "+ex.getMessage());}
                double seconds=(System.nanoTime()-start)/1_000_000_000.0;timings.add(seconds);total++;
                if(failures.isEmpty())passed++;
                if(engine.usedModel()){modelCalls++;modelTimings.add(seconds);}
                if(engine.mode()==ChatEngine.Mode.LOCAL_MODEL)accepted++;if(engine.mode()==ChatEngine.Mode.FALLBACK)fallbacks++;
                var row=new JSONObject().put("id",test.getString("id")).put("group",test.getString("group"))
                        .put("engine",mode).put("responseMode",engine.mode().name()).put("message",test.getString("message"))
                        .put("seconds",seconds).put("pass",failures.isEmpty()).put("failures",new JSONArray(failures));
                row.put("diagnostic",engine.diagnostic()).put("plan",engine.lastPlan()==null?JSONObject.NULL:engine.lastPlan().toString());
                row.put("modelRequested",engine.usedModel());
                if(answer!=null)row.put("text",answer.text()).put("summary",summary(answer.request()))
                        .put("content",new JSONArray(answer.content().stream().map(c->c.getClass().getSimpleName()).toList()));
                rows.put(row);System.out.println(mode+" "+test.getString("id")+" "+(failures.isEmpty()?"PASS":failures));
                Files.writeString(target,report.toString(2)+"\n",StandardCharsets.UTF_8);
            }
            Collections.sort(timings);
            Collections.sort(modelTimings);
            report.put(mode,new JSONObject().put("passed",passed).put("total",total).put("modelResponses",modelCalls)
                    .put("acceptedModelResponses",accepted).put("fallbacks",fallbacks).put("model_p50_seconds",percentile(modelTimings,.50)).put("model_p95_seconds",percentile(modelTimings,.95))
                    .put("p50_seconds",percentile(timings,.50)).put("p95_seconds",percentile(timings,.95)));
        }
        report.put("completed",true);Files.writeString(target,report.toString(2)+"\n",StandardCharsets.UTF_8);
        System.out.println("QUALITY "+report.getJSONObject("rules")+" / "+report.getJSONObject("hybrid"));
        if(report.getJSONObject("hybrid").getInt("passed")!=report.getJSONObject("hybrid").getInt("total"))throw new AssertionError("Hybrid quality regression; see saved report");
    }
    static List<Product> loadCatalog() throws Exception {
        var catalog=new ArrayList<Product>();
        for(Object item:resource("/knowledge/catalog-reference.json").getJSONArray("products")) {
            var row=(JSONObject)item;catalog.add(new Product(row.getInt("id"),row.getString("name"),row.optString("description",""),
                    row.getBigDecimal("price"),row.getInt("stock"),"",row.getString("category"),row.optString("manufacturer","")));
        }return List.copyOf(catalog);
    }
    private static JSONObject resource(String name) throws Exception {
        try(var in=ChatQualityProbe.class.getResourceAsStream(name)) {
            if(in==null)throw new IllegalStateException("Fixture missing: "+name);
            return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    private static double percentile(List<Double> data,double quantile) {return data.isEmpty()?0:data.get(Math.max(0,(int)Math.ceil(data.size()*quantile)-1));}
    private static JSONObject summary(ChatBotService.RequestSummary summary) {
        return new JSONObject().put("budget",summary.budget()==null?JSONObject.NULL:summary.budget())
                .put("usage",summary.usage()==null?JSONObject.NULL:summary.usage().name()).put("ram",summary.minRamGb()==null?JSONObject.NULL:summary.minRamGb())
                .put("storage",summary.minStorageGb()==null?JSONObject.NULL:summary.minStorageGb()).put("verified",summary.requireVerified()).put("ownedCount",summary.ownedParts().size());
    }
    private static void check(JSONObject expected,ChatBotService.Reply answer,ChatBotService.RequestSummary before,List<String> failures) {
        var actual=summary(answer.request());var content=answer.content().stream().map(c->c.getClass().getSimpleName()).toList();
        for(String key:List.of("budget","usage","ram","storage","verified","ownedCount"))if(expected.has(key)) {
            boolean equal=expected.isNull(key)?actual.isNull(key):key.equals("budget")?!actual.isNull(key)&&expected.getBigDecimal(key).compareTo(actual.getBigDecimal(key))==0:expected.get(key).equals(actual.get(key));
            if(!equal)failures.add(key+" expected="+expected.get(key)+" actual="+actual.get(key));
        }
        if(expected.has("content")) {
            String want=expected.getString("content");if(want.equals("none")?!content.isEmpty():!content.contains(want))failures.add("content expected="+want+" actual="+content);
        }
        if(expected.has("anyContent") && expected.getJSONArray("anyContent").toList().stream().noneMatch(content::contains))failures.add("No requested content card");
        String text=answer.text().toLowerCase(Locale.ROOT);
        if(expected.optBoolean("buildOrReason") && !content.contains("BuildContent") && !text.contains("сборка не найдена"))failures.add("No build or explicit search-limit explanation");
        if(expected.has("contains") && !text.contains(expected.getString("contains").toLowerCase(Locale.ROOT)))failures.add("Missing text: "+expected.getString("contains"));
        if(expected.has("containsAll"))for(Object word:expected.getJSONArray("containsAll"))if(!text.contains(word.toString().toLowerCase(Locale.ROOT)))failures.add("Missing text: "+word);
        if(expected.has("notContains"))for(Object word:expected.getJSONArray("notContains"))if(text.contains(word.toString().toLowerCase(Locale.ROOT)))failures.add("Unexpected text: "+word);
        if(expected.optBoolean("unchanged") && !summary(before).similar(actual))failures.add("Conversation state changed");
    }
    private static void checkFacts(ChatBotService.Reply answer,List<Product> catalog,JSONObject expected,List<String> failures) {
        var products=new ArrayList<ChatBotService.ProductInfo>();
        for(var content:answer.content()) {
            if(content instanceof ChatBotService.ProductsContent found)products.addAll(found.items());
            if(content instanceof ChatBotService.BuildContent build) {
                products.addAll(build.parts());
                var sum=build.parts().stream().filter(p->!build.ownedProductIds().contains(p.id())).map(ChatBotService.ProductInfo::price).reduce(BigDecimal.ZERO,BigDecimal::add);
                if(sum.compareTo(build.total())!=0)failures.add("Build total mismatch");
                boolean over=build.total().compareTo(build.budget())>0;
                if(expected.optBoolean("overBudgetDraft")) {
                    if(!over || build.verified() || !answer.text().contains("Бюджет превышен") || build.findings().stream().noneMatch(f->f.startsWith("Бюджет превышен"))
                            || answer.request().budget().compareTo(build.budget())!=0)failures.add("Over-budget draft was not explicitly and honestly reported");
                } else if(over)failures.add("Unexpected build budget overrun");
            }
            if(content instanceof ChatBotService.SpecificationsContent specs){products.add(specs.left());products.add(specs.right());}
            if(content instanceof ChatBotService.PriceComparisonContent prices){products.add(prices.left());products.add(prices.right());
                if(prices.left().price().subtract(prices.right().price()).abs().compareTo(prices.difference())!=0)failures.add("Price difference mismatch");}
        }
        for(var p:products)if(catalog.stream().noneMatch(real->real.getId()==p.id()&&real.getName().equals(p.name())&&real.getPrice().compareTo(p.price())==0&&real.getStock()==p.stock()))failures.add("Product facts not in fixture: "+p.id());
    }
    private static void checkReference(JSONObject expected,ChatBotService.Reply answer,ChatBotService.Reply setup,List<String> failures) {
        if(expected.has("replacementProductId")) {
            var builds=answer.content().stream().filter(ChatBotService.BuildContent.class::isInstance).map(ChatBotService.BuildContent.class::cast).toList();
            int id=expected.getInt("replacementProductId");
            if(builds.size()!=1 || builds.getFirst().parts().stream().noneMatch(p->p.id()==id))failures.add("Requested replacement is absent from build");
            else {
                var actual=builds.getFirst();
                if(expected.has("retainedProductId") && actual.parts().stream().noneMatch(p->p.id()==expected.getInt("retainedProductId")))failures.add("Previous replacement was lost");
                if(expected.has("preserveSetupExceptCategory") && setup!=null) {
                    var originals=setup.content().stream().filter(ChatBotService.BuildContent.class::isInstance).map(ChatBotService.BuildContent.class::cast).toList();
                    int index=expected.optInt("setupBuildIndex",1)-1;String category=expected.getString("preserveSetupExceptCategory");
                    if(index>=originals.size() || !originals.get(index).parts().stream().filter(p->!p.category().equals(category)).toList().equals(actual.parts().stream().filter(p->!p.category().equals(category)).toList()))failures.add("Unrequested build parts changed");
                }
            }
        }
        var ids=answer.content().stream().flatMap(c->c instanceof ChatBotService.ProductsContent p?p.items().stream():
                c instanceof ChatBotService.PriceComparisonContent p?List.of(p.left(),p.right()).stream():java.util.stream.Stream.<ChatBotService.ProductInfo>empty()).map(ChatBotService.ProductInfo::id).toList();
        if(expected.has("productIds") && !ids.equals(expected.getJSONArray("productIds").toList()))failures.add("Wrong referenced product IDs: "+ids);
        if(expected.has("specificationIds")) {
            var compared=answer.content().stream().filter(ChatBotService.SpecificationsContent.class::isInstance)
                    .map(ChatBotService.SpecificationsContent.class::cast).flatMap(c->List.of(c.left().id(),c.right().id()).stream()).toList();
            if(!compared.equals(expected.getJSONArray("specificationIds").toList()))failures.add("Wrong specification pair order: "+compared);
        }
        if(expected.has("setupBuildCategory") && setup!=null) {
            var wanted=setup.content().stream().filter(ChatBotService.BuildContent.class::isInstance).map(ChatBotService.BuildContent.class::cast)
                    .flatMap(c->c.parts().stream()).filter(p->p.category().equals(expected.getString("setupBuildCategory"))).map(ChatBotService.ProductInfo::id).distinct().toList();
            if(wanted.isEmpty() || !wanted.equals(ids))failures.add("Reference differs from setup build: "+ids+" vs "+wanted);
        }
        if(expected.has("setupVariant") && setup!=null) {
            var variants=setup.content().stream().filter(ChatBotService.BuildContent.class::isInstance).map(ChatBotService.BuildContent.class::cast).toList();
            var result=answer.content().stream().filter(ChatBotService.BuildContent.class::isInstance).map(ChatBotService.BuildContent.class::cast).toList();
            int index=expected.getInt("setupVariant")-1;
            if(index>=variants.size() || result.size()!=1 || !variants.get(index).equals(result.getFirst()))failures.add("Wrong setup variant selected");
        }
    }
    private static void checkBenchmarks(JSONObject expected,ChatBotService.Reply answer,List<BenchmarkService.Result> published,List<String> failures) {
        var comparisons=answer.content().stream().filter(ChatBotService.BenchmarksContent.class::isInstance)
                .map(ChatBotService.BenchmarksContent.class::cast).flatMap(c->c.comparisons().stream()).toList();
        for(var comparison:comparisons) {
            if(!published.contains(comparison.left())||!published.contains(comparison.right()))failures.add("Benchmark facts not in packaged reference");
            var computed=new BenchmarkService().compare(comparison.left(),comparison.right());
            if(computed.isEmpty()||computed.get().differencePercent().compareTo(comparison.differencePercent())!=0)
                failures.add("Benchmark context or numeric difference mismatch");
        }
        if(!expected.has("benchmark"))return;
        var wanted=expected.getJSONObject("benchmark");
        if(comparisons.size()!=1){failures.add("Expected one benchmark context, actual="+comparisons.size());return;}
        var actual=comparisons.getFirst();
        if(!actual.left().context().suite().equals(wanted.getString("suite")))failures.add("Wrong benchmark game");
        if(wanted.has("version")&&!actual.left().context().version().equals(wanted.getString("version")))failures.add("Wrong benchmark game version");
        if(actual.left().value().compareTo(wanted.getBigDecimal("left"))!=0||actual.right().value().compareTo(wanted.getBigDecimal("right"))!=0)
            failures.add("Wrong benchmark values or pair order");
        if(actual.differencePercent().compareTo(wanted.getBigDecimal("difference"))!=0)failures.add("Wrong expected benchmark percentage");
    }
}
