package com.example.authapp.services;

import com.example.authapp.models.Product;
import com.example.authapp.repositories.ProductRepository;
import java.util.ArrayList;
import java.util.List;

public class ProductService {
    private final ProductRepository productRepository;

    public ProductService() {
        this.productRepository = new ProductRepository();
    }

    public List<Product> getAllProducts() throws Exception {
        try {
            return productRepository.getAllProducts();
        } catch (Exception e) {
            throw new Exception("Ошибка получения товаров: " + e.getMessage());
        }
    }

    public List<Product> getProductsByCategory(String category) throws Exception {
        try {
            List<Product> all = productRepository.getAllProducts();
            List<Product> filtered = new ArrayList<>();
            for (Product p : all) {
                if (p.getCategory() != null && p.getCategory().equalsIgnoreCase(category)) {
                    filtered.add(p);
                }
            }
            return filtered;
        } catch (Exception e) {
            throw new Exception("Ошибка получения товаров по категории: " + e.getMessage());
        }
    }

    public Product getProductById(int id) throws Exception {
        try {
            List<Product> all = productRepository.getAllProducts();
            for (Product p : all) {
                if (p.getId() == id) {
                    return p;
                }
            }
            return null;
        } catch (Exception e) {
            throw new Exception("Ошибка получения товара: " + e.getMessage());
        }
    }
}
