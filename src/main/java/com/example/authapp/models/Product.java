package com.example.authapp.models;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import java.math.BigDecimal;
import com.example.authapp.network.Money;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.util.StringConverter;

public class Product {
    private final IntegerProperty id;
    private final StringProperty name;
    private final StringProperty description;
    private final ReadOnlyObjectWrapper<BigDecimal> price;
    private final IntegerProperty stock;
    private final StringProperty imageUrl;
    private final StringProperty category;
    private final StringProperty manufacturer;

    // Полный конструктор
    public Product(int id, String name, String description, double price,
                   int stock, String imageUrl, String category, String manufacturer) {
        this(id, name, description, Money.amount(price), stock, imageUrl, category, manufacturer);
    }

    public Product(int id, String name, String description, BigDecimal price,
                   int stock, String imageUrl, String category, String manufacturer) {
        this.id = new SimpleIntegerProperty(id);
        this.name = new SimpleStringProperty(name);
        this.description = new SimpleStringProperty(description);
        this.price = new ReadOnlyObjectWrapper<>(Money.amount(price));
        this.stock = new SimpleIntegerProperty(stock);
        this.imageUrl = new SimpleStringProperty(imageUrl);
        this.category = new SimpleStringProperty(category);
        this.manufacturer = new SimpleStringProperty(manufacturer);
    }

    // Конструктор без manufacturer
    public Product(int id, String name, String description, double price, int stock, String imageUrl, String category) {
        this(id, name, description, price, stock, imageUrl, category, "");
    }

    // Конструктор без id (для новых товаров)
    public Product(String name, String description, double price, int stock, String imageUrl, String category, String manufacturer) {
        this(0, name, description, price, stock, imageUrl, category, manufacturer);
    }

    // Пустой конструктор
    public Product() {
        this(0, "", "", 0.0, 0, "", "", "");
    }

    // Getters
    public int getId() { return id.get(); }
    public String getName() { return name.get(); }
    public String getDescription() { return description.get(); }
    public BigDecimal getPrice() { return price.get(); }
    public int getStock() { return stock.get(); }
    public String getImageUrl() { return imageUrl.get(); }
    public String getCategory() { return category.get(); }
    public String getManufacturer() { return manufacturer.get(); }

    // Setters
    public void setId(int id) { this.id.set(id); }
    public void setName(String value) { name.set(value); }
    public void setDescription(String value) { description.set(value); }
    public void setPrice(double value) { setPrice(Money.amount(value)); }
    public void setPrice(BigDecimal value) { price.set(Money.amount(value)); }
    public void setStock(int value) { stock.set(value); }
    public void setImageUrl(String value) { imageUrl.set(value); }
    public void setCategory(String value) { category.set(value); }
    public void setManufacturer(String value) { manufacturer.set(value); }

    // Properties для JavaFX
    public IntegerProperty idProperty() { return id; }
    public StringProperty nameProperty() { return name; }
    public StringProperty descriptionProperty() { return description; }
    public ReadOnlyObjectProperty<BigDecimal> priceProperty() { return price.getReadOnlyProperty(); }
    public IntegerProperty stockProperty() { return stock; }
    public StringProperty imageUrlProperty() { return imageUrl; }
    public StringProperty categoryProperty() { return category; }
    public StringProperty manufacturerProperty() { return manufacturer; }

    @Override
    public String toString() {
        String n = getName() != null && !getName().isEmpty() ? getName() : "Без названия";
        String c = getCategory() != null && !getCategory().isEmpty() ? getCategory() : "Без категории";
        BigDecimal p = getPrice();
        return String.format("%s (%s) — %,.2f руб.", n, c, p);
    }

    public static StringConverter<Product> stringConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Product product) {
                if (product == null) return "";
                String n = product.getName() != null && !product.getName().isEmpty()
                        ? product.getName()
                        : "Без названия";
                String c = product.getCategory() != null && !product.getCategory().isEmpty()
                        ? product.getCategory()
                        : "";
                if (c.isEmpty()) return n;
                return c + " | " + n;
            }

            @Override
            public Product fromString(String string) {
                return null; // не нужен для ComboBox
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product)) return false;
        Product product = (Product) o;
        return id.get() == product.id.get();
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id.get());
    }
}
