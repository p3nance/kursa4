package com.example.authapp.domain;

import com.example.authapp.models.Product;
import com.example.authapp.network.Money;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/** Client composition: ownership changes the purchase, never the compatibility composition. */
public final class BuildSelection {
    private final EnumMap<ComponentCategory,Product> selected=new EnumMap<>(ComponentCategory.class);
    private final EnumSet<ComponentCategory> owned=EnumSet.noneOf(ComponentCategory.class);
    private final Map<ComponentCategory,Product> view=Collections.unmodifiableMap(selected);
    public Map<ComponentCategory,Product> products() { return view; }
    public Set<ComponentCategory> ownedCategories() { return Set.copyOf(owned); }
    public void select(ComponentCategory category,Product product) {
        if(ComponentCategory.fromDisplayName(product.getCategory()).orElse(null)!=category)
            throw new IllegalArgumentException("Категория выбранного товара не совпадает");
        Product previous=selected.get(category);
        if(previous==null || !sameIdentity(previous,product))owned.remove(category);
        selected.put(category,copy(product));
    }
    public void remove(ComponentCategory category) { selected.remove(category);owned.remove(category); }
    public void clear() { selected.clear();owned.clear(); }
    public boolean isOwned(ComponentCategory category) { return owned.contains(category); }
    public void setOwned(ComponentCategory category,boolean value) {
        if(!selected.containsKey(category))throw new IllegalArgumentException("Сначала выберите деталь");
        if(value)owned.add(category);else owned.remove(category);
    }
    public List<Product> allProducts() { return selected.values().stream().map(BuildSelection::copy).toList(); }
    public List<Product> purchases() { return selected.entrySet().stream().filter(e->!owned.contains(e.getKey())).map(e->copy(e.getValue())).toList(); }
    public List<Product> ownedProducts() { return selected.entrySet().stream().filter(e->owned.contains(e.getKey())).map(e->copy(e.getValue())).toList(); }
    public BigDecimal purchaseTotal() { return selected.entrySet().stream().filter(e->!owned.contains(e.getKey())).map(e->e.getValue().getPrice()).reduce(Money.ZERO,BigDecimal::add); }
    /** Price/stock refresh is safe; reused IDs must not transfer a user's selected or owned model. */
    public Set<ComponentCategory> refresh(Map<Integer,Product> current) {
        var removed=EnumSet.noneOf(ComponentCategory.class);
        for(var category:List.copyOf(selected.keySet())) {
            Product previous=selected.get(category),fresh=current.get(previous.getId());
            if(fresh==null)continue;
            if(!sameIdentity(previous,fresh)){remove(category);removed.add(category);}
            else selected.put(category,copy(fresh));
        }
        return Set.copyOf(removed);
    }
    public static boolean sameIdentity(Product left,Product right) {
        var leftCategory=ComponentCategory.fromDisplayName(left.getCategory());
        var rightCategory=ComponentCategory.fromDisplayName(right.getCategory());
        boolean sameCategory=leftCategory.isPresent() && rightCategory.isPresent()
                ? leftCategory.equals(rightCategory)
                : normalize(left.getCategory()).equals(normalize(right.getCategory()));
        return left.getId()==right.getId() && normalize(left.getName()).equals(normalize(right.getName())) && sameCategory;
    }
    private static String normalize(String value) { return Normalizer.normalize(Objects.toString(value,""),Normalizer.Form.NFKC).strip().replaceAll("(?U)\\s+"," ").toLowerCase(Locale.ROOT); }
    private static Product copy(Product p) { return new Product(p.getId(),p.getName(),p.getDescription(),p.getPrice(),p.getStock(),p.getImageUrl(),p.getCategory(),p.getManufacturer()); }
}
