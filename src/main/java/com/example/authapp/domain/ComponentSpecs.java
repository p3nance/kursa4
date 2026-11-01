package com.example.authapp.domain;

import java.util.*;

/** Facts retain provenance per attribute. Imported descriptions are never verified facts. */
public final class ComponentSpecs {
    public enum Verification { VERIFIED, IMPORTED, UNKNOWN }
    public record Attribute<T>(String name, Class<T> type) {}
    public record Fact<T>(T value, String source, Verification verification) {
        public Fact {
            Objects.requireNonNull(value); Objects.requireNonNull(verification);
            source = Objects.requireNonNullElse(source, "").trim();
            if (verification == Verification.VERIFIED && source.isBlank())
                throw new IllegalArgumentException("Для проверенной характеристики нужен источник");
        }
        public boolean verified() { return verification == Verification.VERIFIED; }
    }

    public static final Attribute<String> SOCKET = text("socket");
    public static final Attribute<String> MODEL_ID = text("model_id");
    public static final Attribute<String> MEMORY_TYPE = text("memory_type");
    public static final Attribute<String> FORM_FACTOR = text("form_factor");
    public static final Attribute<String> STORAGE_INTERFACE = text("storage_interface");
    public static final Attribute<String> PSU_FORM_FACTOR = text("psu_form_factor");
    public static final Attribute<Integer> MAX_POWER_WATTS = number("max_power_watts");
    public static final Attribute<Integer> PSU_WATTS = number("psu_watts");
    public static final Attribute<Integer> MIN_PSU_WATTS = number("min_psu_watts");
    public static final Attribute<Integer> LENGTH_MM = number("length_mm");
    public static final Attribute<Integer> MAX_GPU_LENGTH_MM = number("max_gpu_length_mm");
    public static final Attribute<String> COOLER_KIND = text("cooler_kind");
    /** Radiator mounting class, e.g. 360; not an air-cooler height or physical radiator dimension. */
    public static final Attribute<Integer> RADIATOR_NOMINAL_MM = number("radiator_nominal_mm");
    public static final Attribute<Integer> RADIATOR_LENGTH_MM = number("radiator_length_mm");
    public static final Attribute<Integer> RADIATOR_WIDTH_MM = number("radiator_width_mm");
    public static final Attribute<Integer> RADIATOR_THICKNESS_MM = number("radiator_thickness_mm");
    /** One stock fan layer; does not describe a push-pull or replacement-fan configuration. */
    public static final Attribute<Integer> RADIATOR_FAN_THICKNESS_MM = number("radiator_fan_thickness_mm");
    public static final Attribute<RadiatorMountMatrix> RADIATOR_MOUNTS = new Attribute<>("radiator_mounts",RadiatorMountMatrix.class);
    public static final Attribute<Integer> HEIGHT_MM = number("height_mm");
    public static final Attribute<Integer> MAX_COOLER_HEIGHT_MM = number("max_cooler_height_mm");
    public static final Attribute<Integer> COOLING_WATTS = number("cooling_watts");
    public static final Attribute<Integer> CAPACITY_GB = number("capacity_gb");
    public static final Attribute<Integer> MAX_MEMORY_GB = number("max_memory_gb");
    public static final Attribute<Integer> MODULE_COUNT = number("module_count");
    public static final Attribute<Integer> MEMORY_SLOTS = number("memory_slots");
    public static final Attribute<Set<String>> SUPPORTED_CPU_MODELS = strings("supported_cpu_models");
    public static final Attribute<Set<String>> SUPPORTED_SOCKETS = strings("supported_sockets");
    public static final Attribute<Set<String>> SUPPORTED_FORM_FACTORS = strings("supported_form_factors");
    public static final Attribute<Set<String>> SUPPORTED_STORAGE_INTERFACES = strings("supported_storage_interfaces");
    public static final Attribute<Set<String>> POWER_CONNECTORS = strings("power_connectors");
    public static final Attribute<Set<String>> REQUIRED_POWER_CONNECTORS = strings("required_power_connectors");
    public static final Attribute<Set<String>> SUPPORTED_PSU_FORM_FACTORS = strings("supported_psu_form_factors");

    private final Map<Attribute<?>, Fact<?>> facts;
    private ComponentSpecs(Map<Attribute<?>, Fact<?>> facts) { this.facts = Map.copyOf(facts); }
    public static ComponentSpecs empty() { return builder().build(); }
    public static Builder builder() { return new Builder(); }
    @SuppressWarnings("unchecked")
    public <T> Optional<Fact<T>> fact(Attribute<T> attribute) {
        return Optional.ofNullable((Fact<T>) facts.get(attribute));
    }
    public <T> Optional<T> value(Attribute<T> attribute) { return fact(attribute).map(Fact::value); }
    public Set<Attribute<?>> attributes() { return facts.keySet(); }
    @Override public boolean equals(Object other) {
        return other instanceof ComponentSpecs specs && facts.equals(specs.facts);
    }
    @Override public int hashCode() { return facts.hashCode(); }
    @Override public String toString() { return "ComponentSpecs" + facts; }
    public static final class Builder {
        private final Map<Attribute<?>, Fact<?>> facts = new HashMap<>();
        public <T> Builder set(Attribute<T> attribute, T value, String source, Verification verification) {
            Objects.requireNonNull(attribute); Objects.requireNonNull(value);
            if (!attribute.type().isInstance(value)) throw new IllegalArgumentException("Неверный тип " + attribute.name());
            if (value instanceof Integer i && i <= 0) throw new IllegalArgumentException("Значение должно быть положительным");
            if (value instanceof String s) {
                if (s.isBlank()) throw new IllegalArgumentException("Пустая характеристика");
                @SuppressWarnings("unchecked") T trimmed = (T) s.trim(); value = trimmed;
            }
            if (attribute.equals(COOLER_KIND) && !Set.of("AIR", "AIO").contains(value))
                throw new IllegalArgumentException("Тип охлаждения должен быть AIR или AIO");
            if (value instanceof Set<?> values) {
                if (values.stream().anyMatch(v -> !(v instanceof String s) || s.isBlank()))
                    throw new IllegalArgumentException("Ожидался набор непустых строк");
                @SuppressWarnings("unchecked") T immutable = (T) Set.copyOf(values); value = immutable;
            }
            facts.put(attribute, new Fact<>(value, source, verification)); return this;
        }
        public ComponentSpecs build() { return new ComponentSpecs(facts); }
    }
    private static Attribute<String> text(String name) { return new Attribute<>(name, String.class); }
    private static Attribute<Integer> number(String name) { return new Attribute<>(name, Integer.class); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Attribute<Set<String>> strings(String name) { return new Attribute(name, Set.class); }
}
