package com.example.authapp.domain;

import java.util.*;
import java.util.stream.Collectors;

/** A complete list of mounting locations can still have incomplete physical clearances. */
public record RadiatorMountMatrix(Coverage coverage, List<Rule> rules) {
    public enum Coverage { COMPLETE, PARTIAL }
    public enum Position {
        TOP("Сверху"), FRONT("Спереди"), REAR("Сзади"), SIDE("Сбоку"), BOTTOM("Снизу");
        private final String label;
        Position(String label) { this.label=label; }
        public String label() { return label; }
    }
    /** A bare-radiator upper bound can exclude a mount; it never replaces the complete assembly clearance. */
    public record Rule(Position position, Set<Integer> nominalSizesMm, Integer maxLengthMm,
                       Integer maxWidthMm, Integer maxAssemblyThicknessMm, List<String> unresolvedConditions,
                       Integer radiatorThicknessUpperBoundMm) {
        public Rule(Position position,Set<Integer> nominalSizesMm,Integer maxLengthMm,Integer maxWidthMm,
                    Integer maxAssemblyThicknessMm,List<String> unresolvedConditions) {
            this(position,nominalSizesMm,maxLengthMm,maxWidthMm,maxAssemblyThicknessMm,unresolvedConditions,null);
        }
        public Rule {
            Objects.requireNonNull(position);
            nominalSizesMm=Set.copyOf(nominalSizesMm);
            if(nominalSizesMm.isEmpty() || nominalSizesMm.stream().anyMatch(n->n<=0))
                throw new IllegalArgumentException("Нужны положительные номинальные классы радиатора");
            for(Integer limit:Arrays.asList(maxLengthMm,maxWidthMm,maxAssemblyThicknessMm,radiatorThicknessUpperBoundMm))
                if(limit!=null && limit<=0)throw new IllegalArgumentException("Предел радиатора должен быть положительным");
            unresolvedConditions=List.copyOf(unresolvedConditions);
            if(unresolvedConditions.stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException("Пустое условие установки радиатора");
        }
    }
    public RadiatorMountMatrix {
        Objects.requireNonNull(coverage);
        rules=List.copyOf(rules);
        Set<String> seen=new HashSet<>();
        for(var rule:rules)for(int nominal:rule.nominalSizesMm())
            if(!seen.add(rule.position()+":"+nominal))
                throw new IllegalArgumentException("Повторная позиция и класс радиатора");
    }
    /** Only the mounting classes are summarized here; no physical fit is implied. */
    public String summary() {
        Map<Position,SortedSet<Integer>> groups=new EnumMap<>(Position.class);
        for(var rule:rules)groups.computeIfAbsent(rule.position(),p->new TreeSet<>()).addAll(rule.nominalSizesMm());
        String positions=groups.entrySet().stream().map(e->e.getKey().label()+": "+e.getValue().stream()
                .map(Object::toString).collect(Collectors.joining("/"))+" мм").collect(Collectors.joining("; "));
        if(positions.isEmpty())positions=coverage==Coverage.COMPLETE?"Штатные крепления отсутствуют":"Крепления не описаны";
        return positions+". "+(coverage==Coverage.COMPLETE?"Все штатные позиции перечислены.":"Перечень позиций частичный.")
                +" Это номинальные классы; размеры и зазоры проверяются отдельно.";
    }
}
