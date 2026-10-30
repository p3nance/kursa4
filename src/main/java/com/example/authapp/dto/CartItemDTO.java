package com.example.authapp.dto;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;
import com.example.authapp.network.Money;
public class CartItemDTO {

    @SerializedName("id")
    public int cartItemId;

    @SerializedName("user_id")
    public String userId;

    @SerializedName("product_id")
    public int productId;

    @SerializedName("product_name")
    public String productName;

    @SerializedName("price")
    public BigDecimal price = Money.ZERO;

    @SerializedName("quantity")
    public int quantity;

    @SerializedName("product_image")
    public String productImage;

    public CartItemDTO() {}

    public CartItemDTO(int cartItemId, int productId, String productName,
                       BigDecimal price, int quantity, String productImage) {
        this.cartItemId = cartItemId;
        this.productId = productId;
        this.productName = productName;
        this.price = Money.amount(price);
        this.quantity = quantity;
        this.productImage = productImage;
    }
}
