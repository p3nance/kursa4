package com.example.authapp.services;

import com.example.authapp.models.User;

public class AppStateManager {
    private static AppStateManager instance;
    private User currentUser;
    private CartService cartService;
    private PromoCodeService promoCodeService;
    private ProductService productService;

    private AppStateManager() {}

    public static synchronized AppStateManager getInstance() {
        if (instance == null) {
            instance = new AppStateManager();
            instance.initializeDefaultServices();
        }
        return instance;
    }

    private void initializeDefaultServices() {
        this.cartService = new CartService();
        this.promoCodeService = new PromoCodeService();
        this.productService = new ProductService();
    }

    public void initializeServices(CartService cartService, PromoCodeService promoService, ProductService productService) {
        this.cartService = cartService;
        this.promoCodeService = promoService;
        this.productService = productService;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public CartService getCartService() {
        return cartService;
    }

    public PromoCodeService getPromoCodeService() {
        return promoCodeService;
    }

    public ProductService getProductService() {
        return productService;
    }

    public void clearAppState() {
        try {
            if (cartService != null) {
                cartService.clearCart();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        currentUser = null;
    }
}
