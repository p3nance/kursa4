package com.example.authapp.models;

import java.util.ArrayList;
import java.util.List;

/** Synchronized local cart. Callers receive detached item/product snapshots. */
public final class Cart {
    private static final Cart INSTANCE=new Cart();
    private final List<CartItem> items=new ArrayList<>();
    private Cart() {}
    public static Cart getInstance(){return INSTANCE;}
    public static final class CartItem {
        private final Product product;
        private int quantity;
        public CartItem(Product product,int quantity){this.product=copyProduct(product);this.quantity=quantity;}
        public Product getProduct(){return copyProduct(product);}
        public synchronized int getQuantity(){return quantity;}
        public synchronized void setQuantity(int quantity){this.quantity=quantity;}
        private synchronized CartItem snapshot(){return new CartItem(product,quantity);}
    }
    private static Product copyProduct(Product p){
        return new Product(p.getId(),p.getName(),p.getDescription(),p.getPrice(),p.getStock(),p.getImageUrl(),p.getCategory(),p.getManufacturer());
    }
    public synchronized void addProduct(Product product){addProduct(product,1);}
    public synchronized void addProduct(Product product,int quantity){
        if(product==null||quantity<=0)return;
        for(CartItem item:items)if(item.product.getId()==product.getId()){item.quantity+=quantity;return;}
        items.add(new CartItem(product,quantity));
    }
    public synchronized void removeProduct(Product product){if(product!=null)items.removeIf(item->item.product.getId()==product.getId());}
    public synchronized void clear(){items.clear();}
    public synchronized void replaceItems(List<CartItem> replacement){items.clear();for(CartItem item:replacement)if(item.getQuantity()>0)items.add(item.snapshot());}
    public synchronized List<CartItem> getItems(){return items.stream().map(CartItem::snapshot).toList();}
    public synchronized double getTotal(){return items.stream().mapToDouble(item->item.product.getPrice()*item.quantity).sum();}
    public synchronized int getTotalQuantity(){return items.stream().mapToInt(item->item.quantity).sum();}
    public synchronized int getUniqueItemsCount(){return items.size();}
}
