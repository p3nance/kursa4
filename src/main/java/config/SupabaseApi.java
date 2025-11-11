package config;

import com.example.authapp.models.Product;

import java.util.*;

public class SupabaseApi {
    public static List<Product> getPopularProducts() {
        // Тут должна быть интеграция с API Supabase
        // Пример тестовых данных:
        List<Product> products = new ArrayList<>();
        products.add(new Product("Лампа", "Светодиодная лампа", "https://via.placeholder.com/180x120", "Освещение", 120));
        products.add(new Product("Розетка", "Розетка двойная", "https://via.placeholder.com/180x120", "Розетки", 230));
        products.add(new Product("Кабель", "Медный кабель 5м", "https://via.placeholder.com/180x120", "Кабели", 340));
        return products;
    }
}
