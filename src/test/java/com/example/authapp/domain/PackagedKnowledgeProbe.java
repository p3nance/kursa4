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
        System.setProperty("techstore.knowledge.dir","explicit-empty-override");
        var empty=new LocalKnowledgeRepository().load(products);
        if(empty.components().stream().anyMatch(c->c.specs().fact(ComponentSpecs.MODEL_ID).filter(ComponentSpecs.Fact::verified).isPresent()))
            throw new AssertionError("Explicit local override unexpectedly loaded bundled facts");
        System.out.println("PASS: packaged JAR loads verified specs and benchmark comparison without external knowledge; explicit override retained.");
    }
}
