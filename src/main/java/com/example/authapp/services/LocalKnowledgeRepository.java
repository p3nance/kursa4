package com.example.authapp.services;

import com.example.authapp.domain.*;
import com.example.authapp.models.Product;
import java.io.IOException;
import java.io.Reader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit local data source. Broken data is reported, never treated as verified. */
public final class LocalKnowledgeRepository {
    public record Snapshot(List<CatalogComponent> components, BenchmarkService benchmarks, List<String> warnings) {
        public Snapshot { components = List.copyOf(components); warnings = List.copyOf(warnings); }
    }
    public Snapshot load(Collection<Product> products) {
        List<String> warnings = new ArrayList<>();
        List<CatalogComponent> components = new ArrayList<>();
        for (Product product : products) {
            if (ComponentCategory.fromDisplayName(product.getCategory()).isEmpty()) continue;
            try { components.add(CatalogComponent.fromProduct(product)); }
            catch (IllegalArgumentException ex) { warnings.add("Товар «" + product.getName() + "» пропущен: некорректные данные каталога."); }
        }
        String configured = System.getProperty("techstore.knowledge.dir");
        Path directory = Path.of(configured == null ? "knowledge" : configured);
        boolean useBundled = configured == null;
        Path specs = directory.resolve("component-specs.json");
        if (Files.exists(specs) || useBundled) {
            try {
                var loader=new ComponentSpecsLoader();ComponentSpecsLoader.Dataset data;
                if(Files.exists(specs))data=loader.loadDataset(specs);
                else try(var reader=bundled("component-specs.json")){data=loader.loadDataset(reader);}
                var applied=loader.applyValidated(components,data);
                components=applied.components();warnings.addAll(applied.warnings());
            }
            catch (IOException ex) { warnings.add("Локальные характеристики не загружены: проверьте формат component-specs.json. Совместимость не подтверждена."); }
        }
        BenchmarkService benchmarks = new BenchmarkService();
        Path tests = directory.resolve("benchmarks.json");
        if (Files.exists(tests) || useBundled) {
            try {
                if(Files.exists(tests))benchmarks=new BenchmarkService(benchmarks.load(tests));
                else try(var reader=bundled("benchmarks.json")){benchmarks=new BenchmarkService(benchmarks.load(reader));}
            }
            catch (IOException ex) { warnings.add("Данные бенчмарков не загружены: проверьте формат benchmarks.json."); }
        }
        return new Snapshot(components, benchmarks, warnings);
    }
    private static Reader bundled(String name) throws IOException {
        var stream=LocalKnowledgeRepository.class.getResourceAsStream("/knowledge/"+name);
        if(stream==null)throw new IOException("Встроенный справочник отсутствует: "+name);
        return new InputStreamReader(stream,StandardCharsets.UTF_8);
    }
}
