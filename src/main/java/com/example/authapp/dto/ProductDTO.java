package com.example.authapp.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Data Transfer Object для Product
 * Используется ТОЛЬКО для работы с Gson и Supabase
 * Содержит простые типы (int, String, double), БЕЗ JavaFX Property!
 */
public class ProductDTO {

    @SerializedName("id")
    public int id;

    @SerializedName("name")
    public String name;

    @SerializedName("description")
    public String description;

    @SerializedName("price")
    public double price;

    @SerializedName("stock")
    public int stock;

    @SerializedName("image_url")  // ✅ Маппим на правильное имя из БД
    public String imageUrl;

    @SerializedName("category")
    public String category;

    @SerializedName("manufacturer")
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
