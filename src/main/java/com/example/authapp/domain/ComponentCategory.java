package com.example.authapp.domain;

import java.util.Arrays;
import java.util.Optional;

public enum ComponentCategory {
    CPU("Процессоры"), MOTHERBOARD("Материнские платы"), GPU("Видеокарты"),
    RAM("Оперативная память"), STORAGE("SSD-накопители"), PSU("Блоки питания"),
    CASE("Корпуса"), COOLER("Охлаждение");

    private final String displayName;
    ComponentCategory(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
    public static Optional<ComponentCategory> fromDisplayName(String name) {
        if (name == null) return Optional.empty();
        return Arrays.stream(values()).filter(v -> v.displayName.equalsIgnoreCase(name.trim())
                || v.name().equalsIgnoreCase(name.trim())).findFirst();
    }
}
