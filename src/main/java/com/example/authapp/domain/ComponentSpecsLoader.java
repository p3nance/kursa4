package com.example.authapp.domain;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import static com.example.authapp.domain.ComponentSpecs.*;

/** Versioned local sidecar, keyed by product ID. Does not query or alter Supabase. */
public final class ComponentSpecsLoader {
    public record Identity(String catalogName, ComponentCategory category) {}
    public record Dataset(Map<Integer,ComponentSpecs> specs, Map<Integer,Identity> identities) {
        public Dataset { specs=Map.copyOf(specs);identities=Map.copyOf(identities); }
    }
    public record Applied(List<CatalogComponent> components, List<String> warnings) {
        public Applied { components=List.copyOf(components);warnings=List.copyOf(warnings); }
    }
    private static final List<Attribute<?>> ATTRIBUTES=List.of(SOCKET,MODEL_ID,MEMORY_TYPE,FORM_FACTOR,
            STORAGE_INTERFACE,PSU_FORM_FACTOR,MAX_POWER_WATTS,PSU_WATTS,MIN_PSU_WATTS,LENGTH_MM,
            MAX_GPU_LENGTH_MM,HEIGHT_MM,MAX_COOLER_HEIGHT_MM,COOLING_WATTS,CAPACITY_GB,
            MAX_MEMORY_GB,MODULE_COUNT,MEMORY_SLOTS,SUPPORTED_CPU_MODELS,SUPPORTED_SOCKETS,
            SUPPORTED_FORM_FACTORS,SUPPORTED_STORAGE_INTERFACES,POWER_CONNECTORS,
            REQUIRED_POWER_CONNECTORS,SUPPORTED_PSU_FORM_FACTORS);
    public Map<Integer,ComponentSpecs> load(Path path) throws IOException {
        return loadDataset(path).specs();
    }
    public Dataset loadDataset(Path path) throws IOException {
        if(Files.size(path)>2_000_000) throw new IOException("Файл характеристик превышает 2 МБ");
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            if(root.get("version").getAsBigDecimal().intValueExact()!=1) throw new IllegalArgumentException("Неизвестная версия характеристик");
            Map<Integer,ComponentSpecs> result=new HashMap<>();
            Map<Integer,Identity> identities=new HashMap<>();
            for(var item:root.getAsJsonArray("products")) {
                var object=item.getAsJsonObject(); int id=object.get("productId").getAsBigDecimal().intValueExact();
                if(id<=0 || result.containsKey(id)) throw new IllegalArgumentException("Неверный или повторный productId");
                if(object.has("catalogName")) {
                    var name=object.get("catalogName");
                    if(!name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString() || name.getAsString().isBlank())
                        throw new IllegalArgumentException("Неверное имя привязки к каталогу");
                    ComponentCategory category=null;
                    if(object.has("catalogCategory")) category=ComponentCategory.fromDisplayName(object.get("catalogCategory").getAsString())
                            .orElseThrow(()->new IllegalArgumentException("Неверная категория привязки к каталогу"));
                    identities.put(id,new Identity(name.getAsString(),category));
                } else if(object.has("catalogCategory")) throw new IllegalArgumentException("Категории привязки нужно имя товара");
                var builder=ComponentSpecs.builder();
                for(var entry:object.getAsJsonObject("facts").entrySet()) {
                    var attribute=ATTRIBUTES.stream().filter(a->a.name().equals(entry.getKey())).findFirst()
                            .orElseThrow(()->new IllegalArgumentException("Неизвестная характеристика: "+entry.getKey()));
                    put(builder,attribute,entry.getValue().getAsJsonObject());
                }
                result.put(id,builder.build());
            }
            return new Dataset(result,identities);
        } catch(RuntimeException ex) { throw new IOException("Некорректный файл характеристик: "+ex.getMessage(),ex); }
    }
    private static <T> void put(Builder builder,Attribute<T> attribute,JsonObject fact) {
        JsonElement json=fact.get("value"); Object value;
        if(attribute.type()==String.class) {
            if(!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Ожидалась строка");
            value=json.getAsString();
        } else if(attribute.type()==Integer.class) {
            if(!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Ожидалось число");
            value=json.getAsBigDecimal().intValueExact();
        } else {
            Set<String> values=new LinkedHashSet<>();
            for(var v:json.getAsJsonArray()) {
                if(!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Ожидался массив строк");
                values.add(v.getAsString());
            }
            value=Set.copyOf(values);
        }
        builder.set(attribute,attribute.type().cast(value),fact.get("source").getAsString(),
                Verification.valueOf(fact.get("verification").getAsString()));
    }
    public List<CatalogComponent> apply(Collection<CatalogComponent> catalog,Map<Integer,ComponentSpecs> verifiedSpecs) {
        return catalog.stream().map(c->new CatalogComponent(c.productId(),c.name(),c.category(),c.price(),c.stock(),
                verifiedSpecs.getOrDefault(c.productId(),c.specs()))).toList();
    }
    /** Authored model facts cannot silently follow an ID reused for another product. */
    public Applied applyValidated(Collection<CatalogComponent> catalog,Dataset dataset) {
        List<CatalogComponent> result=new ArrayList<>();List<String> warnings=new ArrayList<>();
        for(var component:catalog) {
            var identity=dataset.identities().get(component.productId());
            if(identity!=null && (!normalized(identity.catalogName()).equals(normalized(component.name()))
                    || identity.category()!=null && identity.category()!=component.category())) {
                warnings.add("Характеристики товара №"+component.productId()+" не применены: название или категория изменились. Нужна повторная сверка модели.");
                result.add(component);continue;
            }
            result.add(new CatalogComponent(component.productId(),component.name(),component.category(),component.price(),component.stock(),
                    dataset.specs().getOrDefault(component.productId(),component.specs())));
        }
        return new Applied(result,warnings);
    }
    private static String normalized(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFKC).strip().replaceAll("(?U)\\s+"," ").toLowerCase(Locale.ROOT);
    }
}
