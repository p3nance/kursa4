package com.example.authapp.domain;

import java.util.*;
import java.util.regex.*;
import static com.example.authapp.domain.ComponentSpecs.*;

/** Conservative legacy import. No performance data is inferred from names or prose. */
public final class DescriptionSpecImporter {
    private DescriptionSpecImporter() {}
    private static final Pattern FIELD = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}_])([a-z_]+)\\s*:\\s*([\\p{L}\\p{N}_.+/-]+)");
    public static ComponentSpecs parse(String description) {
        var builder = ComponentSpecs.builder();
        if (description == null || description.isBlank()) return builder.build();
        Map<String, String> fields = new HashMap<>(); Set<String> ambiguous = new HashSet<>();
        Matcher matcher = FIELD.matcher(description);
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT), value = matcher.group(2);
            String previous = fields.putIfAbsent(key, value);
            if (previous != null && !previous.equalsIgnoreCase(value)) ambiguous.add(key);
        }
        ambiguous.forEach(fields::remove);
        removeConflictingAliases(fields,"memory","memory_type");
        removeConflictingAliases(fields,"gpu_power","max_power_watts");
        putText(builder, fields, "socket", SOCKET);
        putText(builder, fields, "memory", MEMORY_TYPE);
        putText(builder, fields, "memory_type", MEMORY_TYPE);
        putText(builder, fields, "form_factor", FORM_FACTOR);
        putText(builder, fields, "storage_interface", STORAGE_INTERFACE);
        putNumber(builder, fields, "psu_power", PSU_WATTS);
        putNumber(builder, fields, "gpu_power", MAX_POWER_WATTS);
        putNumber(builder, fields, "max_power_watts", MAX_POWER_WATTS);
        putNumber(builder, fields, "length_mm", LENGTH_MM);
        putNumber(builder, fields, "height_mm", HEIGHT_MM);
        putNumber(builder, fields, "capacity_gb", CAPACITY_GB);
        // TDP is deliberately not equated to maximum electrical power.
        return builder.build();
    }
    private static void removeConflictingAliases(Map<String,String> fields,String a,String b) {
        if(fields.containsKey(a) && fields.containsKey(b) && !fields.get(a).equalsIgnoreCase(fields.get(b))) {
            fields.remove(a);fields.remove(b);
        }
    }
    private static void putText(Builder b, Map<String,String> f, String key, Attribute<String> a) {
        if (f.containsKey(key)) b.set(a, f.get(key), "legacy description: " + key, Verification.IMPORTED);
    }
    private static void putNumber(Builder b, Map<String,String> f, String key, Attribute<Integer> a) {
        if (!f.containsKey(key) || !f.get(key).matches("[0-9]{1,6}")) return;
        int value = Integer.parseInt(f.get(key));
        if (value > 0) b.set(a, value, "legacy description: " + key, Verification.IMPORTED);
    }
}
