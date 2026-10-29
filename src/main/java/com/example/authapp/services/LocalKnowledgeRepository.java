package com.example.authapp.services;

import com.example.authapp.domain.*;
import com.example.authapp.models.Product;
import java.io.IOException;
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
        Path directory = Path.of(System.getProperty("techstore.knowledge.dir", "knowledge"));
        Path specs = directory.resolve("component-specs.json");
        if (Files.exists(specs)) {
            try {
                var loader=new ComponentSpecsLoader();var applied=loader.applyValidated(components,loader.loadDataset(specs));
                components=applied.components();warnings.addAll(applied.warnings());
            }
            catch (IOException ex) { warnings.add("Локальные характеристики не загружены: проверьте формат component-specs.json. Совместимость не подтверждена."); }
        }
        BenchmarkService benchmarks = new BenchmarkService();
        Path tests = directory.resolve("benchmarks.json");
        if (Files.exists(tests)) {
            try { benchmarks = new BenchmarkService(benchmarks.load(tests)); }
            catch (IOException ex) { warnings.add("Данные бенчмарков не загружены: проверьте формат benchmarks.json."); }
        }
        return new Snapshot(components, benchmarks, warnings);
    }
}
