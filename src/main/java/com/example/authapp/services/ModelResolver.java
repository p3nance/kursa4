package com.example.authapp.services;

import com.example.authapp.domain.CatalogComponent;
import com.example.authapp.domain.ComponentCategory;
import com.example.authapp.domain.ComponentSpecs;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Catalog-bound aliases. A possible spelling correction is never an automatic model selection. */
public final class ModelResolver {
    public enum Status { EXACT, AMBIGUOUS, SUGGESTED, NOT_FOUND }
    public record Resolution(Status status, List<CatalogComponent> candidates) {
        public Resolution { Objects.requireNonNull(status); candidates=List.copyOf(candidates); }
    }
    private record Entry(String alias, List<CatalogComponent> candidates, Pattern inText) {}
    private record Mention(int start, int end, List<CatalogComponent> candidates) {}
    private static final Pattern INTEL=Pattern.compile("\\b(i[3579])\\s+(\\d{4,5}(?:ks|kf|k|f|t)?)\\b");
    private static final Pattern RYZEN=Pattern.compile("\\bryzen\\s+(?:([3579])\\s+)?(\\d{4,5}(?:x3d|xt|ge|x|f|g)?)\\b");
    private static final Pattern GPU=Pattern.compile("\\b(rtx|gtx|rx)\\s*(\\d{3,4})(?:\\s*(ti|super|xtx|xt))?(?:\\s+(super))?\\b");
    private static final Pattern CAPACITY=Pattern.compile("\\b(\\d{1,3}gb)\\b");
    private static final Pattern DIGITS=Pattern.compile("\\d+");
    private static final Pattern CPU_VARIANT=Pattern.compile("\\b\\d{4,5}(x3d|ks|kf|ge|k|f|x|g|t)\\b");
    private static final Pattern GPU_VARIANT=Pattern.compile("\\b(ti\\s+super|xtx|super|ti|xt)\\b");
    private final List<Entry> entries;
    private final Map<String,List<CatalogComponent>> compactAliases;

    public ModelResolver(Collection<CatalogComponent> catalog) {
        Objects.requireNonNull(catalog);
        Map<String,LinkedHashMap<Integer,CatalogComponent>> aliases=new LinkedHashMap<>();
        catalog.stream().sorted(Comparator.comparingInt(CatalogComponent::productId)).forEach(component->{
            var names=new LinkedHashSet<String>();
            names.add(normalize(component.name()));
            component.specs().fact(ComponentSpecs.MODEL_ID).filter(ComponentSpecs.Fact::verified)
                    .ifPresent(f->names.add(normalize(f.value())));
            if(component.category()==ComponentCategory.CPU) cpuAliases(component.name(),names);
            if(component.category()==ComponentCategory.GPU) gpuAliases(component.name(),names);
            for(String name:names) if(!name.isBlank()) aliases.computeIfAbsent(name,k->new LinkedHashMap<>())
                    .put(component.productId(),component);
        });
        var built=new ArrayList<Entry>();
        var compact=new LinkedHashMap<String,LinkedHashMap<Integer,CatalogComponent>>();
        aliases.forEach((alias,items)->{
            var candidates=List.copyOf(items.values());
            String regex=Arrays.stream(alias.split(" ")).map(Pattern::quote).reduce((a,b)->a+"\\s*"+b).orElse("");
            built.add(new Entry(alias,candidates,Pattern.compile("(?<![\\p{L}\\p{N}])"+regex+"(?![\\p{L}\\p{N}])")));
            var merged=compact.computeIfAbsent(compact(alias),k->new LinkedHashMap<>());
            items.forEach(merged::put);
        });
        entries=List.copyOf(built);
        Map<String,List<CatalogComponent>> immutable=new LinkedHashMap<>();
        compact.forEach((key,items)->immutable.put(key,List.copyOf(items.values())));
        compactAliases=Collections.unmodifiableMap(immutable);
    }

    public Resolution resolve(String query) {
        if(query==null || query.length()>2000) return none();
        String normalized=normalize(query), key=compact(normalized);
        if(key.isEmpty()) return none();
        var exact=compactAliases.get(key);
        if(exact!=null) return resolved(exact);
        // Numeric identity and explicit variant tokens must survive a proposed spelling correction.
        List<String> numbers=numbers(normalized);
        if(numbers.isEmpty() || key.length()<4) return none();
        var possible=new LinkedHashMap<Integer,CatalogComponent>();
        int best=2;
        for(var entry:entries) {
            if(!numbers.equals(numbers(entry.alias())) || !sameExplicitVariants(normalized,entry.alias())) continue;
            int distance=oneEditDistance(key,compact(entry.alias()));
            if(distance>1 || distance>best) continue;
            if(distance<best) {best=distance;possible.clear();}
            entry.candidates().forEach(c->possible.put(c.productId(),c));
        }
        return possible.isEmpty()?none():new Resolution(Status.SUGGESTED,sorted(possible.values()));
    }

    /** Unambiguous mentions, in textual order. The longest alias consumes an overlapping shorter alias. */
    public List<CatalogComponent> mentions(String text) {
        if(text==null || text.length()>2000) return List.of();
        String normalized=normalize(text);
        var found=new ArrayList<Mention>();
        for(var entry:entries) {
            Matcher matcher=entry.inText().matcher(normalized);
            while(matcher.find()) {
                String before=normalized.substring(0,matcher.start()), after=normalized.substring(matcher.end());
                var candidates=entry.candidates().stream().filter(c->consistentNeighbors(c,before,after)).toList();
                if(!candidates.isEmpty()) found.add(new Mention(matcher.start(),matcher.end(),candidates));
            }
        }
        found.sort(Comparator.comparingInt(Mention::start).thenComparing(Comparator.comparingInt(Mention::end).reversed()));
        var result=new LinkedHashMap<Integer,CatalogComponent>();
        int consumed=-1;
        for(var mention:found) {
            if(mention.start()<consumed) continue;
            consumed=mention.end();
            if(mention.candidates().size()==1) {
                var component=mention.candidates().getFirst(); result.putIfAbsent(component.productId(),component);
            }
        }
        return List.copyOf(result.values());
    }

    public static String normalize(String value) {
        if(value==null) return "";
        String s=Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('ё','е');
        s=s.replaceAll("райзен", "ryzen").replaceAll("интел", "intel")
                .replaceAll("нвидиа", "nvidia").replaceAll("джифорс|гефорс", "geforce")
                .replaceAll("радеон", "radeon").replaceAll("супер", "super")
                .replaceAll("(?U)(?<=\\d)х3д\\b", "x3d");
        s=s.replaceAll("[\\p{Pd}_/\\\\.,:;!?()\\[\\]{}\\\"'«»]+", " ");
        s=s.replaceAll("\\b(intel|core|amd|ryzen|radeon|geforce|nvidia|rtx|gtx|rx)(?=\\d)", "$1 ")
                .replaceAll("\\b(i[3579])(?=\\d{4,5})", "$1 ")
                .replaceAll("(?<=\\d)(?=ti\\b|super\\b|xtx\\b|xt\\b)", " ")
                .replaceAll("\\bti(?=super\\b)", "ti ")
                .replaceAll("(?U)\\b(\\d+)\\s*(?:гб|gb)\\b", "$1gb")
                .replaceAll("(?U)\\b(\\d+)\\s*(?:тб|tb)\\b", "$1tb")
                .replaceAll("\\b(\\d{4,5})\\s+(x3d|ks|kf|ge|k|f|x|g|t)\\b", "$1$2");
        return s.replaceAll("(?U)\\s+", " ").strip();
    }

    private static void cpuAliases(String name,Set<String> aliases) {
        String normalized=normalize(name);
        Matcher intel=INTEL.matcher(normalized);
        if(intel.find()) {
            String tier=intel.group(1),number=intel.group(2);
            aliases.add(number);aliases.add(tier+" "+number);aliases.add("intel "+tier+" "+number);
            aliases.add("core "+tier+" "+number);aliases.add("intel core "+tier+" "+number);
        }
        Matcher ryzen=RYZEN.matcher(normalized);
        if(ryzen.find()) {
            String tier=ryzen.group(1),number=ryzen.group(2);
            aliases.add(number);aliases.add("ryzen "+number);aliases.add("amd ryzen "+number);
            if(tier!=null) {aliases.add("ryzen "+tier+" "+number);aliases.add("amd ryzen "+tier+" "+number);}
        }
    }
    private static void gpuAliases(String name,Set<String> aliases) {
        String normalized=normalize(name);Matcher matcher=GPU.matcher(normalized);
        if(!matcher.find()) return;
        String prefix=matcher.group(1), model=matcher.group(2);
        if(matcher.group(3)!=null) model+=" "+matcher.group(3);
        if(matcher.group(4)!=null) model+=" "+matcher.group(4);
        var bases=new LinkedHashSet<String>();bases.add(model);bases.add(prefix+" "+model);
        if(prefix.equals("rx")) {bases.add("radeon "+prefix+" "+model);bases.add("amd radeon "+prefix+" "+model);}
        else {bases.add("geforce "+prefix+" "+model);bases.add("nvidia "+prefix+" "+model);bases.add("nvidia geforce "+prefix+" "+model);}
        aliases.addAll(bases);
        Matcher capacity=CAPACITY.matcher(normalized);
        if(capacity.find()) for(String base:bases) aliases.add(base+" "+capacity.group(1));
    }
    private static boolean consistentNeighbors(CatalogComponent c,String before,String after) {
        String name=normalize(c.name());
        Matcher manufacturer=Pattern.compile("\\b(intel|amd|nvidia)\\b(?:\\s+(?:core|ryzen|radeon|geforce|rtx|gtx|rx|i[3579]|[3579]))*\\s*$").matcher(before);
        if(manufacturer.find() && !name.matches(".*\\b"+manufacturer.group(1)+"\\b.*")) return false;
        Matcher tier=Pattern.compile("\\b(i[3579])\\s*$").matcher(before);
        if(tier.find() && !name.contains(tier.group(1)+" ")) return false;
        Matcher ryzenTier=Pattern.compile("\\bryzen\\s+([3579])\\s*$").matcher(before);
        if(ryzenTier.find() && !name.contains("ryzen "+ryzenTier.group(1)+" ")) return false;
        Matcher family=Pattern.compile("\\b(rtx|gtx|rx|ryzen)(?:\\s+[3579])?\\s*$").matcher(before);
        if(family.find() && !name.matches(".*\\b"+family.group(1)+"\\b.*")) return false;
        Matcher suffix=Pattern.compile("^\\s+(ti\\s*super|xtx|super|ti|xt)(?=\\s|\\d|$)").matcher(after);
        if(suffix.find()) return false; // A longer alias must include an explicitly written variant.
        Matcher capacity=Pattern.compile("^\\s+(\\d{1,3}gb)\\b").matcher(after);
        if(!capacity.find()) return true;
        String requested=capacity.group(1);
        return CAPACITY.matcher(name).results().anyMatch(m->m.group(1).equals(requested));
    }
    private static boolean sameExplicitVariants(String query,String alias) {
        Matcher q=CPU_VARIANT.matcher(query),a=CPU_VARIANT.matcher(alias);
        if(q.find() && (!a.find() || !q.group(1).equals(a.group(1)))) return false;
        Matcher qGpu=GPU_VARIANT.matcher(query),aGpu=GPU_VARIANT.matcher(alias);
        return !qGpu.find() || aGpu.find() && qGpu.group(1).equals(aGpu.group(1));
    }
    private static List<String> numbers(String text) {
        return DIGITS.matcher(text).results().map(r->r.group()).toList();
    }
    private static String compact(String value) {return value.replace(" ","");}
    private static Resolution resolved(List<CatalogComponent> candidates) {
        return new Resolution(candidates.size()==1?Status.EXACT:Status.AMBIGUOUS,candidates);
    }
    private static Resolution none() {return new Resolution(Status.NOT_FOUND,List.of());}
    private static List<CatalogComponent> sorted(Collection<CatalogComponent> items) {
        return items.stream().sorted(Comparator.comparingInt(CatalogComponent::productId)).toList();
    }
    /** 0, 1 or 2 (more than one edit); an adjacent transposition counts as one typo. */
    private static int oneEditDistance(String a,String b) {
        if(a.equals(b)) return 0;
        if(Math.abs(a.length()-b.length())>1) return 2;
        if(a.length()==b.length()) {
            int first=-1,second=-1;
            for(int i=0;i<a.length();i++) if(a.charAt(i)!=b.charAt(i)) {
                if(first<0) first=i;else if(second<0) second=i;else return 2;
            }
            if(second<0) return 1;
            return second==first+1 && a.charAt(first)==b.charAt(second) && a.charAt(second)==b.charAt(first)?1:2;
        }
        String shorter=a.length()<b.length()?a:b,longer=a.length()<b.length()?b:a;
        int i=0,j=0;
        while(i<shorter.length() && j<longer.length()) {
            if(shorter.charAt(i)==longer.charAt(j)) {i++;j++;}
            else if(i==j) j++;else return 2;
        }
        return 1;
    }
}
