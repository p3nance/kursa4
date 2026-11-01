package com.example.authapp.domain;

import com.example.authapp.dto.ProductDTO;
import com.example.authapp.models.Product;
import com.example.authapp.services.*;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.util.ArrayList;

/** Runs against the built client JAR from an empty working directory, without real credentials. */
public final class PackagedKnowledgeProbe {
    public static void main(String[] args) throws Exception {
        if(Files.exists(Path.of("knowledge")))throw new AssertionError("Smoke directory must not contain external knowledge");
        System.clearProperty("techstore.knowledge.dir");
        var products=new ArrayList<Product>();var gson=new Gson();
        try(var stream=PackagedKnowledgeProbe.class.getResourceAsStream("/knowledge/catalog-reference.json")) {
            if(stream==null)throw new AssertionError("Missing public test catalog");
            try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                for(var item:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("products")) {
                    var dto=gson.fromJson(item,ProductDTO.class);products.add(new Product(dto.id,dto.name,dto.description,dto.price,dto.stock,"",dto.category,dto.manufacturer));
                }
            }
        }
        var language=new ChatBotService(products).reply("Найди Лоджитек Джи Про Икс TKL");
        if(language.content().stream().filter(ChatBotService.ProductsContent.class::isInstance).map(ChatBotService.ProductsContent.class::cast)
                .flatMap(c->c.items().stream()).noneMatch(p->p.id()==83))throw new AssertionError("Bundled Russian catalog aliases unavailable");
        var data=new LocalKnowledgeRepository().load(products);
        if(!data.warnings().isEmpty()||data.components().size()!=67)throw new AssertionError("Bundled specs not applied: "+data.warnings());
        long verified=data.components().stream().filter(c->c.specs().fact(ComponentSpecs.MODEL_ID).filter(ComponentSpecs.Fact::verified).isPresent()).count();
        if(verified<20)throw new AssertionError("Bundled identities missing: "+verified);
        var reply=new ChatBotService(products).reply("Сравни 14700K и 14600K");
        if(reply.content().stream().noneMatch(ChatBotService.BenchmarksContent.class::isInstance))throw new AssertionError("Bundled benchmark comparison unavailable");
        var gaming=new ChatBotService(products).reply("Сравни Ryzen 7 7800X3D и Ryzen 5 7600X");
        if(gaming.content().stream().filter(ChatBotService.BenchmarksContent.class::isInstance)
                .map(ChatBotService.BenchmarksContent.class::cast).flatMap(c->c.comparisons().stream())
                .noneMatch(c->c.left().value().compareTo(new java.math.BigDecimal("438.3"))==0
                        && c.right().value().compareTo(new java.math.BigDecimal("311.5"))==0
                        && c.left().context().unit().equals("FPS")))
            throw new AssertionError("Bundled verified gaming results unavailable");
        var gpuGaming=new ChatBotService(products).reply("Сравни RTX 4060 и RX 7600 для игр в Starfield 1440p");
        if(gpuGaming.content().stream().filter(ChatBotService.BenchmarksContent.class::isInstance)
                .map(ChatBotService.BenchmarksContent.class::cast).flatMap(c->c.comparisons().stream())
                .noneMatch(c->c.left().value().compareTo(new java.math.BigDecimal("48.5"))==0
                        && c.right().value().compareTo(new java.math.BigDecimal("46.6"))==0
                        && c.left().context().conditions().getOrDefault("review_cohort","")
                            .equals("gn-7600xt-2024-03-07-starfield-1440p-high-gpu")))
            throw new AssertionError("Bundled verified1440p GPU results unavailable");
        var blackMyth=new ChatBotService(products).reply("Сравни RTX4060 и RX7600 для игр в Black Myth: Wukong1440p");
        if(!blackMyth.lead().contains("не совпадает") || blackMyth.content().stream()
                .filter(ChatBotService.BenchmarksContent.class::isInstance).map(ChatBotService.BenchmarksContent.class::cast)
                .flatMap(c->c.comparisons().stream()).noneMatch(c->c.left().value().compareTo(new java.math.BigDecimal("36.9"))==0
                        &&c.right().value().compareTo(new java.math.BigDecimal("31.6"))==0))
            throw new AssertionError("Bundled Black Myth data or visible platform-description conflict unavailable");
        var f1Bot=new ChatBotService(products);
        var f1New=f1Bot.reply("Сравни Ryzen5 7600 и Ryzen7 7800X3D для игр в F1 25 1080p");
        if(f1New.content().stream().filter(ChatBotService.BenchmarksContent.class::isInstance).map(ChatBotService.BenchmarksContent.class::cast)
                .flatMap(c->c.comparisons().stream()).noneMatch(c->c.left().context().version().equals("25")
                        &&c.left().value().compareTo(new java.math.BigDecimal("217.0"))==0&&c.right().value().compareTo(new java.math.BigDecimal("302.3"))==0))
            throw new AssertionError("Bundled exact7600non-X F1 25 comparison unavailable");
        var reversed=f1Bot.reply("Поменяй местами");
        if(reversed.content().stream().filter(ChatBotService.BenchmarksContent.class::isInstance).map(ChatBotService.BenchmarksContent.class::cast)
                .flatMap(c->c.comparisons().stream()).noneMatch(c->c.left().value().compareTo(new java.math.BigDecimal("302.3"))==0
                        &&c.right().value().compareTo(new java.math.BigDecimal("217.0"))==0&&c.differencePercent().compareTo(new java.math.BigDecimal("39.31"))==0))
            throw new AssertionError("Bundled comparison order or reciprocal percentage base unavailable");
        var cpuPlatforms=new ChatBotService(products).reply("сравни i9-14900k и ryzen 7 7800x3d");
        if(!cpuPlatforms.lead().contains("двух тестовых платформ") || !cpuPlatforms.text().contains("распределение памяти по CPU")
                || cpuPlatforms.content().stream().filter(ChatBotService.BenchmarksContent.class::isInstance)
                .map(ChatBotService.BenchmarksContent.class::cast).flatMap(c->c.comparisons().stream())
                .noneMatch(c->c.left().value().compareTo(new java.math.BigDecimal("384.5"))==0
                        && c.right().value().compareTo(new java.math.BigDecimal("438.3"))==0
                        && c.differencePercent().compareTo(new java.math.BigDecimal("-12.27"))==0
                        && "PUBLISHED_PLATFORM_COMPARISON".equals(c.left().context().conditions().get("comparison_kind"))))
            throw new AssertionError("Bundled CPU platform comparison or its visible scope unavailable");
        var popAir=data.components().stream().filter(c->c.productId()==54).findFirst().orElseThrow();
        var kraken=data.components().stream().filter(c->c.productId()==71).findFirst().orElseThrow();
        if(popAir.specs().fact(ComponentSpecs.RADIATOR_MOUNTS).filter(ComponentSpecs.Fact::verified).isEmpty()
                ||new CompatibilityService().assess(java.util.List.of(popAir,kraken),false).findings().stream()
                .noneMatch(f->f.code().equals("radiator_fit")&&f.status()==CompatibilityService.Status.INCOMPATIBLE))
            throw new AssertionError("Bundled radiator matrix or verified Pop Air/360 conflict unavailable");
        System.setProperty("techstore.knowledge.dir","explicit-empty-override");
        var empty=new LocalKnowledgeRepository().load(products);
        if(empty.components().stream().anyMatch(c->c.specs().fact(ComponentSpecs.MODEL_ID).filter(ComponentSpecs.Fact::verified).isPresent()))
            throw new AssertionError("Explicit local override unexpectedly loaded bundled facts");
        System.out.println("PASS: packaged JAR loads verified specs and benchmark comparison without external knowledge; explicit override retained.");
    }
}
