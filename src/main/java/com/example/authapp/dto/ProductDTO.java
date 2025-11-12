package com.example.authapp.dto;

/**
 * Data Transfer Object для Product
 * Используется ТОЛЬКО для работы с Gson и Supabase
 * Содержит простые типы (int, String, double), БЕЗ JavaFX Property!
 */
public class ProductDTO {
    public int id;
    public String name;
    public String description;
    public double price;
    public int stock;
    public String imageUrl;
    public String category;
    public String manufacturer;

    // Пустой конструктор для Gson
    public ProductDTO() {}

    // Конструктор со всеми полями
    public ProductDTO(int id, String name, String description, double price,
                      int stock, String imageUrl, String category, String manufacturer) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.imageUrl = imageUrl;
        this.category = category;
        this.manufacturer = manufacturer;
    }

    @Override
    public String toString() {
        return "ProductDTO{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", price=" + price +
                '}';
    }
}