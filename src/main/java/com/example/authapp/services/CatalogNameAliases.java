package com.example.authapp.services;

import com.example.authapp.domain.CatalogComponent;
import com.example.authapp.models.Product;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Curated names only. A reused ID, renamed model or different category cannot inherit another SKU's aliases. */
final class CatalogNameAliases {
    private record Entry(String name,String category,List<String> aliases,Set<String> normalizedAliases) {}
    private record Word(Pattern pattern,String replacement) {}
    private static final List<Word> WORDS=words();
    private static List<Word> words() {
        String[][] mappings={
            {"вестерн диджитал|western digital","wd"},{"фрактал дизайн","fractal design"},{"кулер мастер","cooler master"},{"лиан ли","lian li"},{"би квайт","be quiet"},
            {"джи скилл|джискилл|джискил","g skill"},{"нвидиа|нвидия|энвидиа","nvidia"},{"джифорс|гефорс","geforce"},{"амд","amd"},{"интел","intel"},{"райзен","ryzen"},{"радеон","radeon"},
            {"самсунг","samsung"},{"кингстон","kingston"},{"корсар|корсэйр|корсаир","corsair"},{"сигейт|сигэйт","seagate"},{"крушиал|круциал","crucial"},{"асус","asus"},{"мси|эмэсай","msi"},{"гигабайт","gigabyte"},{"асрок","asrock"},
            {"патриот","patriot"},{"дипкул","deepcool"},{"сисоник|сезоник","seasonic"},{"ноктуа|ноктуa","noctua"},{"фантекс","phanteks"},{"нзхт|энзетиксти","nzxt"},{"вд","wd"},
            {"лоджитек|логитек|лоджитэк","logitech"},{"стилсериес|стилсириес","steelseries"},{"хайперикс","hyperx"},{"рейзер|рэйзер","razer"},{"элджи|эл джи","lg"},{"делл","dell"},{"аок","aoc"}
        };
        return Arrays.stream(mappings).map(pair->new Word(Pattern.compile("(?U)(?<![\\p{L}])(?:"+pair[0]+ ")(?![\\p{L}])"),pair[1])).toList();
    }
    static String translateBrands(String value){for(var word:WORDS)value=word.pattern().matcher(value).replaceAll(word.replacement());return value;}
    private static final class Registry {static final Map<Integer,Entry> ENTRIES=read();}
    private static Map<Integer,Entry> read() {
        try(var input=CatalogNameAliases.class.getResourceAsStream("/knowledge/catalog-language-aliases.json")) {
            if(input==null)return Map.of();
            var data=new JSONObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));if(data.getInt("version")!=1)return Map.of();
            Map<Integer,Entry> result=new HashMap<>();
            for(Object row:data.getJSONArray("products")) {
                var entry=(JSONObject)row;var aliases=entry.getJSONArray("aliases").toList().stream().map(String.class::cast).toList();
                if(aliases.isEmpty() || aliases.size()>12 || aliases.stream().anyMatch(a->a.isBlank() || a.length()>240))throw new IllegalArgumentException("Invalid language aliases");
                var normalized=new HashSet<String>();aliases.forEach(a->normalized.add(ModelResolver.normalize(a)));
                if(result.put(entry.getInt("productId"),new Entry(ModelResolver.normalize(entry.getString("catalogName")),entry.getString("category"),List.copyOf(aliases),Set.copyOf(normalized)))!=null)throw new IllegalArgumentException("Duplicate alias identity");
            }
            return Map.copyOf(result);
        } catch(Exception ex){return Map.of();}
    }
    static List<String> forComponent(CatalogComponent part){return aliases(part.productId(),part.name(),part.category().displayName());}
    static List<String> aliases(int id,String name,String category) {
        var entry=Registry.ENTRIES.get(id);
        return entry!=null && entry.name().equals(ModelResolver.normalize(name)) && entry.category().equals(category)?entry.aliases():List.of();
    }
    static Set<Integer> exactProductIds(Collection<Product> products,String query) {
        String normalized=ModelResolver.normalize(query);Set<Integer> ids=new HashSet<>();
        for(var product:products) {
            var entry=Registry.ENTRIES.get(product.getId());
            if(entry!=null && entry.name().equals(ModelResolver.normalize(product.getName())) && entry.category().equals(product.getCategory()) && entry.normalizedAliases().contains(normalized))ids.add(product.getId());
        }
        return Set.copyOf(ids);
    }
}
