package com.example.authapp.models;

public class Product {
    private String name;
    private String description;
    private String imageUrl;
    private String category;
    private double price;
    private String manufacturer;

    public Product() {}

    // Важно — правильный порядок аргументов для парсинга из Supabase!
    public Product(String name, String description, double price, String category, String manufacturer, String imageUrl) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.manufacturer = manufacturer;
        this.imageUrl = imageUrl;
    }

    // Для совместимости
    public Product(String name, String description, String imageUrl, String category, double price) {
        this(name, description, price, category, "", imageUrl);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
}
