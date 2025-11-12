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
            throw new Exception("Товар не найден");
        } catch (Exception e) {
            throw new Exception("Ошибка получения товара: " + e.getMessage());
        }
    }

    public void addProduct(String name, double price, String description, int stock, String imageUrl, String category) throws Exception {
        if (name == null || name.isEmpty() || price <= 0 || stock < 0) {
            throw new IllegalArgumentException("Некорректные данные товара");
        }

        Product product = new Product(0, name, description, price, stock, imageUrl, category, "");
        productRepository.addProduct(product);
    }

    public void updateProduct(int productId, String name, double price, int stock, String description, String category, String manufacturer) throws Exception {
        Product product = getProductById(productId);
        product.setName(name);
        product.setPrice(price);
        product.setStock(stock);
        product.setDescription(description);
        product.setCategory(category);
        product.setManufacturer(manufacturer);
        productRepository.updateProduct(product);
    }

    public void deleteProduct(int productId) throws Exception {
        productRepository.deleteProduct(productId);
    }

    public void updateProductStock(int productId, int newStock) throws Exception {
        Product product = getProductById(productId);
        product.setStock(newStock);
        productRepository.updateProduct(product);
    }
}