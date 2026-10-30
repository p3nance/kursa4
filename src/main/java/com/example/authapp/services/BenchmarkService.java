package com.example.authapp.services;

import com.google.gson.*;
import java.io.*;
import java.math.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Imports normalized measurements, never scrapes websites or manufactures scores. */
public final class BenchmarkService {
    public enum Direction { HIGHER_IS_BETTER, LOWER_IS_BETTER }
    public record Context(String suite,String version,String workload,String metric,String unit,
                          Direction direction,Map<String,String> conditions) {
        public Context {
            suite=required(suite);version=required(version);workload=required(workload);
            metric=required(metric);unit=required(unit);Objects.requireNonNull(direction);
            Objects.requireNonNull(conditions);
            var copy=new TreeMap<String,String>();
            conditions.forEach((k,v)->copy.put(required(k),required(v))); conditions=Map.copyOf(copy);
            if(conditions.isEmpty()) throw new IllegalArgumentException("Условия теста обязательны");
        }
    }
    public record Result(String modelId,Context context,BigDecimal value,URI source,LocalDate measuredAt,LocalDate publishedAt) {
        public Result {
            modelId=required(modelId);Objects.requireNonNull(context);Objects.requireNonNull(value);
            Objects.requireNonNull(source);
            if(measuredAt==null && publishedAt==null) throw new IllegalArgumentException("Нужна дата измерения или публикации");
            if(value.signum()<=0) throw new IllegalArgumentException("Результат теста должен быть положительным");
            if(!Set.of("http","https").contains(Objects.requireNonNullElse(source.getScheme(),"").toLowerCase(Locale.ROOT))
                    || source.getHost()==null) throw new IllegalArgumentException("Требуется HTTP(S) ссылка на источник");
        }
        /** Legacy datasets contain the actual measurement date only. */
        public Result(String modelId,Context context,BigDecimal value,URI source,LocalDate measuredAt) {
            this(modelId,context,value,source,measuredAt,null);
        }
        public String dateDescription() {
            if(measuredAt!=null) return "измерено " + measuredAt + (publishedAt==null?"":"; опубликовано " + publishedAt);
            return "опубликовано " + publishedAt + "; точная дата измерения не указана";
        }
    }
    private record MeasurementKey(String modelId,Context context,URI source,LocalDate measuredAt,LocalDate publishedAt) {}
    public record Comparison(Result left,Result right,String betterModelId,BigDecimal differencePercent,String explanation) {}
    private final List<Result> results;
    public BenchmarkService() { this(List.of()); }
    public BenchmarkService(Collection<Result> results) { this.results=List.copyOf(results); }
    public List<Result> resultsFor(String modelId) {
        return results.stream().filter(r->r.modelId().equalsIgnoreCase(modelId)).toList();
    }
    public List<Comparison> compareModels(String leftModel,String rightModel) {
        List<Comparison> comparisons=new ArrayList<>();
        for(var a:resultsFor(leftModel)) for(var b:resultsFor(rightModel)) compare(a,b).ifPresent(comparisons::add);
        return List.copyOf(comparisons);
    }
    public Optional<Comparison> compare(Result left,Result right) {
        Objects.requireNonNull(left);Objects.requireNonNull(right);
        if(!left.context().equals(right.context())) return Optional.empty();
        int ordering=left.value().compareTo(right.value());
        String better=ordering==0?"":((ordering>0)==(left.context().direction()==Direction.HIGHER_IS_BETTER)?left.modelId():right.modelId());
        var difference=left.value().subtract(right.value()).multiply(BigDecimal.valueOf(100))
                .divide(right.value(),2,RoundingMode.HALF_UP);
        return Optional.of(new Comparison(left,right,better,difference,
                "Разница значения слева относительно справа; вывод относится только к этому тесту и условиям. "+
                        "Общая производительность и FPS других задач из результата не следуют."));
    }
    public List<Result> load(Path path) throws IOException {
        if(Files.size(path)>5_000_000) throw new IOException("Файл бенчмарков превышает 5 МБ");
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) { return load(reader); }
    }
    /** The caller owns the reader; measurement validation is identical for files and resources. */
    public List<Result> load(Reader reader) throws IOException {
        try {
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            if(root.get("version").getAsBigDecimal().intValueExact()!=1) throw new IllegalArgumentException("Неизвестная версия бенчмарков");
            List<Result> imported=new ArrayList<>();Set<MeasurementKey> keys=new HashSet<>();
            for(var item:root.getAsJsonArray("results")) {
                var o=item.getAsJsonObject();var c=o.getAsJsonObject("context");
                Map<String,String> conditions=new TreeMap<>();
                c.getAsJsonObject("conditions").entrySet().forEach(e->conditions.put(e.getKey(),e.getValue().getAsString()));
                Context context=new Context(c.get("suite").getAsString(),c.get("version").getAsString(),
                        c.get("workload").getAsString(),c.get("metric").getAsString(),c.get("unit").getAsString(),
                        Direction.valueOf(c.get("direction").getAsString()),conditions);
                var result=new Result(o.get("modelId").getAsString(),context,o.get("value").getAsBigDecimal(),
                        URI.create(o.get("source").getAsString()),optionalDate(o,"measuredAt"),optionalDate(o,"publishedAt"));
                var key=new MeasurementKey(result.modelId().toLowerCase(Locale.ROOT),context,result.source(),result.measuredAt(),result.publishedAt());
                if(!keys.add(key)) throw new IllegalArgumentException("Повторное измерение");
                imported.add(result);
            }
            return List.copyOf(imported);
        } catch(RuntimeException ex) { throw new IOException("Некорректный файл бенчмарков: "+ex.getMessage(),ex); }
    }
    private static LocalDate optionalDate(JsonObject object,String field) {
        var value=object.get(field);
        return value==null || value.isJsonNull()?null:LocalDate.parse(value.getAsString());
    }
    private static String required(String value) {
        if(value==null || value.isBlank()) throw new IllegalArgumentException("Пустое обязательное поле теста");
        return value.trim();
    }
}
