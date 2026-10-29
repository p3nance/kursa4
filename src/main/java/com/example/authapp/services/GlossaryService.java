package com.example.authapp.services;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** Local, editable glossary with provenance. Matching is lexical, not generated text. */
public final class GlossaryService {
    public record Term(String term,List<String> aliases,String definition,String simpleExplanation,
                       String category,List<String> relatedTerms,URI source,LocalDate reviewedAt) {
        public Term {
            Objects.requireNonNull(term);Objects.requireNonNull(definition);Objects.requireNonNull(simpleExplanation);
            Objects.requireNonNull(category);Objects.requireNonNull(source);Objects.requireNonNull(reviewedAt);
            aliases=List.copyOf(aliases);relatedTerms=List.copyOf(relatedTerms);
            if(term.isBlank() || definition.isBlank() || !Set.of("http","https").contains(source.getScheme()))
                throw new IllegalArgumentException("Некорректная словарная статья");
        }
    }
    private final List<Term> terms;
    public GlossaryService() { this(loadDefault()); }
    public GlossaryService(Collection<Term> terms) { this.terms=List.copyOf(terms); }
    public List<Term> all() { return terms; }
    public List<Term> search(String query,int limit) {
        if(limit<1 || query==null || query.isBlank()) return List.of();
        String normalized=normalize(query);
        return terms.stream().map(t->Map.entry(t,score(t,normalized))).filter(e->e.getValue()>0)
                .sorted(Comparator.<Map.Entry<Term,Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(e->e.getKey().term())).limit(Math.min(limit,50)).map(Map.Entry::getKey).toList();
    }
    private static int score(Term term,String query) {
        List<String> names=new ArrayList<>(term.aliases());names.add(term.term());
        int score=0;
        for(String name:names) {
            String n=normalize(name);
            if(n.equals(query)) score=Math.max(score,1000);
            else if((" "+query+" ").contains(" "+n+" ")) score=Math.max(score,100+n.length());
        }
        return score;
    }
    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
    }
    private static List<Term> loadDefault() {
        try(var input=GlossaryService.class.getResourceAsStream("/knowledge/glossary.ru.json")) {
            if(input==null) throw new IOException("Не найден словарь /knowledge/glossary.ru.json");
            var root=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("version").getAsInt()!=1) throw new IOException("Неизвестная версия словаря");
            List<Term> result=new ArrayList<>();
            for(var element:root.getAsJsonArray("terms")) {
                var o=element.getAsJsonObject();
                result.add(new Term(o.get("term").getAsString(),strings(o.getAsJsonArray("aliases")),
                        o.get("definition").getAsString(),o.get("simpleExplanation").getAsString(),
                        o.get("category").getAsString(),strings(o.getAsJsonArray("relatedTerms")),
                        URI.create(o.get("source").getAsString()),LocalDate.parse(o.get("reviewedAt").getAsString())));
            }
            return List.copyOf(result);
        } catch(IOException | RuntimeException ex) { throw new IllegalStateException("Не удалось загрузить словарь",ex); }
    }
    private static List<String> strings(JsonArray array) {
        List<String> strings=new ArrayList<>();array.forEach(e->strings.add(e.getAsString()));return strings;
    }
}
