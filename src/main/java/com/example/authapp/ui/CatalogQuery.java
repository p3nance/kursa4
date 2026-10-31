package com.example.authapp.ui;

import com.example.authapp.models.Product;
import java.math.BigDecimal;
import java.util.*;

/** Actual catalog filtering; the UI does not synthesize counts, prices or stock. */
public record CatalogQuery(String text, String category, Set<String> manufacturers, BigDecimal minPrice,
                           BigDecimal maxPrice, boolean inStockOnly, Sort sort) {
    public enum Sort { DEFAULT("По умолчанию"), PRICE_ASC("Сначала дешевле"), PRICE_DESC("Сначала дороже"), NAME("По названию");
        private final String label; Sort(String label){this.label=label;}@Override public String toString(){return label;}}
    public CatalogQuery {
        text=Objects.requireNonNullElse(text,"").trim().toLowerCase(Locale.ROOT);
        category=Objects.requireNonNullElse(category,"Все");manufacturers=Set.copyOf(manufacturers);Objects.requireNonNull(sort);
        if(minPrice!=null && minPrice.signum()<0 || maxPrice!=null && maxPrice.signum()<0 || minPrice!=null&&maxPrice!=null&&minPrice.compareTo(maxPrice)>0)
            throw new IllegalArgumentException("Проверьте диапазон цены: минимум не должен превышать максимум");
    }
    public List<Product> apply(Collection<Product> catalog) {
        Comparator<Product> comparator=switch(sort){case DEFAULT->Comparator.comparingInt(Product::getId);case NAME->Comparator.comparing(p->Objects.toString(p.getName(),""),String.CASE_INSENSITIVE_ORDER);
            case PRICE_ASC->Comparator.comparing(Product::getPrice);case PRICE_DESC->Comparator.comparing(Product::getPrice).reversed();};
        return catalog.stream().filter(p->p.getPrice().signum() >= 0)
                .filter(p->category.equals("Все")||category.equalsIgnoreCase(Objects.toString(p.getCategory(),"")))
                .filter(p->manufacturers.isEmpty()||manufacturers.contains(Objects.toString(p.getManufacturer(),"")))
                .filter(p->!inStockOnly||p.getStock()>0)
                .filter(p->(Objects.toString(p.getName(),"")+" "+Objects.toString(p.getCategory(),"")+" "+Objects.toString(p.getManufacturer(),"")).toLowerCase(Locale.ROOT).contains(text))
                .filter(p->minPrice==null||p.getPrice().compareTo(minPrice)>=0)
                .filter(p->maxPrice==null||p.getPrice().compareTo(maxPrice)<=0)
                .sorted(comparator.thenComparingInt(Product::getId)).toList();
    }
}
