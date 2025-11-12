package com.example.authapp.dto;

/**
 * Data Transfer Object для CartItem
 */
public class CartItemDTO {
    public int cartItemId;
    public int productId;
    public String productName;
    public double price;
    public int quantity;
    public String productImage;

    public CartItemDTO() {}

    public CartItemDTO(int cartItemId, int productId, String productName,
                       double price, int quantity, String productImage) {
        this.cartItemId = cartItemId;
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
        this.productImage = productImage;
    }
}