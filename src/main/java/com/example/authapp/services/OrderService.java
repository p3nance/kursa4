package com.example.authapp.services;

import com.example.authapp.dto.OrderDTO;
import com.example.authapp.models.Cart;
import com.example.authapp.network.SupabaseHttp;
import com.example.authapp.repositories.OrderRepository;
import config.SessionManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class OrderService {
    private final CartService cartService;
    private String pendingFingerprint;
    private UUID pendingRequestId;
    public OrderService(CartService cartService, ProductService productService) { this.cartService = cartService; }
    public List<OrderDTO> getUserOrderHistory() throws Exception {
        var lease = SessionManager.requireSession();
        try (var scope = SupabaseHttp.forSession(lease)) {
            return OrderRepository.getUserOrders(lease.userId());
        }
    }
    /** Retains the request id after an uncertain network result, so Retry cannot create a second order. */
    public synchronized int createOrderFromCart(String promoCode) throws Exception {
        var lease = SessionManager.requireSession();
        try (var scope = SupabaseHttp.forSession(lease)) {
        String userId = lease.userId();
        List<Cart.CartItem> items = List.copyOf(cartService.getCurrentCart().getItems());
        if (items.isEmpty()) throw new IllegalArgumentException("Корзина пуста");
        JSONArray payload = new JSONArray();
        items.stream().sorted(Comparator.comparingInt(item -> item.getProduct().getId())).forEach(item -> {
            if (item.getQuantity() <= 0) throw new IllegalArgumentException("Количество должно быть больше нуля");
            payload.put(new JSONObject().put("product_id", item.getProduct().getId()).put("quantity", item.getQuantity()));
        });
        String fingerprint = userId + ":" + (promoCode == null ? "" : promoCode.trim()) + ":" + payload;
        if (!fingerprint.equals(pendingFingerprint)) {
            pendingFingerprint = fingerprint; pendingRequestId = UUID.randomUUID();
        }
        int orderId = OrderRepository.checkout(pendingRequestId, payload, promoCode);
        // Server removes purchased cart rows within the same transaction. Do not issue a second DELETE.
        SessionManager.runIfCurrent(lease, () -> { cartService.getCurrentCart().clear(); cartService.clearAppliedPromoCode(); });
        pendingFingerprint = null; pendingRequestId = null;
        return orderId;
        }
    }
}
