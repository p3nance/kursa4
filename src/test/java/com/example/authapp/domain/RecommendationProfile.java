package com.example.authapp.domain;

import com.example.authapp.dto.ProductDTO;
import com.example.authapp.models.Product;
import com.example.authapp.services.*;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Optional local timing report. No network, real keys, pass/fail time thresholds or synthetic performance scores. */
public final class RecommendationProfile {
    public static void main(String[] args) throws Exception {
        var gson=new Gson();var products=new ArrayList<Product>();
        try(var stream=RecommendationProfile.class.getResourceAsStream("/knowledge/catalog-reference.json");
            var reader=new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8)) {
            for(var item:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("products")) {
                var p=gson.fromJson(item,ProductDTO.class);products.add(new Product(p.id,p.name,p.description,p.price,p.stock,"",p.category,p.manufacturer));
            }
        }
        System.clearProperty("techstore.knowledge.dir");
        long start=System.nanoTime();var knowledge=new LocalKnowledgeRepository().load(products);
        double loadMs=(System.nanoTime()-start)/1_000_000.0;
        var results=new JsonArray();
        for(var usage:BuildRecommendationService.Usage.values()) {
            var budget=new BigDecimal(switch(usage){case OFFICE->"100000";case GAMING->"200000";case WORKSTATION->"500000";});
            var request=BuildRecommendationService.Request.forUsage(budget,usage);
            var service=new BuildRecommendationService();
            for(int i=0;i<2;i++)service.recommendAlternatives(knowledge.components(),request,3);
            double[] times=new double[5];List<BuildRecommendationService.Recommendation> choices=List.of();
            for(int i=0;i<times.length;i++) {
                start=System.nanoTime();var next=service.recommendAlternatives(knowledge.components(),request,3);times[i]=(System.nanoTime()-start)/1_000_000.0;
                if(!choices.isEmpty()&&!choices.equals(next))throw new AssertionError("Non-deterministic search");choices=next;
            }
            Arrays.sort(times);var row=new JsonObject();row.addProperty("usage",usage.name());row.addProperty("budgetRub",budget);
            row.addProperty("samples",times.length);row.addProperty("medianMs",times[2]);row.addProperty("worstMs",times[4]);
            row.addProperty("alternatives",choices.stream().filter(c->!c.components().isEmpty()).count());
            row.addProperty("status",choices.getFirst().status().name());row.addProperty("combinationsExamined",choices.getFirst().combinationsExamined());
            row.addProperty("searchTruncated",choices.getFirst().searchTruncated());results.add(row);
        }
        var report=new JsonObject();report.addProperty("generatedAt",Instant.now().toString());
        report.addProperty("scope","Local CPU timings against the public catalog snapshot and embedded client knowledge. No live DB, HTTP latency or GPU/FPS measurement.");
        report.addProperty("javaVersion",System.getProperty("java.version"));report.addProperty("os",System.getProperty("os.name"));
        report.addProperty("availableProcessors",Runtime.getRuntime().availableProcessors());report.addProperty("catalogProducts",products.size());
        report.addProperty("systemBlockProducts",knowledge.components().size());report.addProperty("knowledgeLoadMs",loadMs);
        report.addProperty("warmupsPerScenario",2);report.addProperty("nodeLimitPerScenario",50000);report.add("scenarios",results);
        Path output=Path.of(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println(report);
    }
}
