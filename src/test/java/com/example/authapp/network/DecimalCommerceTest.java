package com.example.authapp.network;

import com.example.authapp.dto.*;
import com.example.authapp.models.Cart;
import com.example.authapp.models.Product;
import com.example.authapp.models.PromoCode;
import com.example.authapp.services.ChatBotService;
import com.example.authapp.services.PromoCodeService;
import com.example.authapp.ui.CatalogQuery;
import com.google.gson.Gson;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DecimalCommerceTest {
    private final Gson gson = new Gson();
    private final Cart cart = Cart.getInstance();
    @BeforeEach void clearBefore() { cart.clear(); }
    @AfterEach void clearAfter() { cart.clear(); }

    @Test void preservesJsonDigitsThroughProductChatAndCart() {
        var dto = gson.fromJson("{\"id\":1,\"name\":\"Decimal\",\"price\":9007199254740993.17}", ProductDTO.class);
        var product = new Product(dto.id, dto.name, "", dto.price, 10, "", "Процессоры", "");
        var copy = ChatBotService.ProductInfo.from(product).toProduct();
        cart.addProduct(copy, 3);
        assertEquals(new BigDecimal("9007199254740993.17"), copy.getPrice());
        assertEquals(new BigDecimal("27021597764222979.51"), cart.getTotal());
        assertEquals(dto.price, new JSONObject(gson.toJson(dto)).getBigDecimal("price"));
        assertInstanceOf(Number.class, new JSONObject(gson.toJson(dto)).get("price"));
    }
    @Test void roundsDiscountAfterSummingCurrencyLines() {
        cart.addProduct(product(1, "10.05"), 3);
        var service = new PromoCodeService();
        var promo = new PromoCode(1, "TEN", new BigDecimal("10.00"), 10, 0, "2099-12-31", true);
        assertEquals(new BigDecimal("30.15"), cart.getTotal());
        assertEquals(new BigDecimal("3.02"), service.calculateDiscount(cart.getTotal(), promo));
        assertEquals(new BigDecimal("27.13"), service.calculateFinalPrice(cart.getTotal(), promo));
        assertEquals(new BigDecimal("0.00"), Money.discount(cart.getTotal(), BigDecimal.ZERO));
        assertEquals(cart.getTotal(), Money.discount(cart.getTotal(), new BigDecimal("100")));
    }
    @Test void sumsSmallFractionsAndKeepsHistoricalItemAndOrderTotals() {
        cart.addProduct(product(1, "0.10"), 3);cart.addProduct(product(2, "0.20"));
        assertEquals(new BigDecimal("0.50"), cart.getTotal());
        var item = new OrderItemDTO(1, 5, 1, "Fraction", 3, new BigDecimal("0.10"), "");
        var order = new OrderDTO(5, "user", cart.getTotal(), "pending", "2026-10-30", 1, "THIRD",
                Money.discount(cart.getTotal(), new BigDecimal("33.33")), new BigDecimal("33.33"));
        assertEquals(new BigDecimal("0.30"), item.subtotal);
        assertEquals(new BigDecimal("0.33"), order.finalAmount);
        assertEquals(order.finalAmount, gson.fromJson(gson.toJson(order), OrderDTO.class).finalAmount);
        var cartItem=gson.fromJson("{\"price\":0.10,\"quantity\":3}",CartItemDTO.class);
        assertEquals(new BigDecimal("0.30"), Money.lineTotal(cartItem.price, cartItem.quantity));
    }
    @Test void priceSortingAndBudgetBoundariesDoNotCollapseAdjacentAmounts() {
        var cheaper=product(2,"9007199254740993.16");var dearer=product(1,"9007199254740993.17");
        var query=new CatalogQuery("","Все",Set.of(),null,null,false,CatalogQuery.Sort.PRICE_ASC);
        assertEquals(List.of(cheaper,dearer),query.apply(List.of(dearer,cheaper)));
        query=new CatalogQuery("","Все",Set.of(),null,cheaper.getPrice(),false,CatalogQuery.Sort.PRICE_ASC);
        assertEquals(List.of(cheaper),query.apply(List.of(dearer,cheaper)));
    }
    @Test void localizedPriceInputRejectsSilentRoundingAndNonCurrencyValues() {
        assertEquals(new BigDecimal("1234.50"),Money.parsePrice(" 1234,50 "));
        assertEquals(new BigDecimal("0.01"),Money.parsePrice("0.01"));
        for(String input:List.of("-0.01","NaN","Infinity","1e3","1.005","1,2,3",""))
            assertThrows(IllegalArgumentException.class,()->Money.parsePrice(input),input);
        assertThrows(IllegalArgumentException.class,()->Money.payable(BigDecimal.ONE,new BigDecimal("1.01")));
        assertThrows(IllegalArgumentException.class,()->Money.percent(new BigDecimal("100.01")));
        assertThrows(IllegalArgumentException.class,()->Money.lineTotal(BigDecimal.ONE,-1));
        var p=product(1,"1.25");assertThrows(IllegalArgumentException.class,()->p.setPrice(new BigDecimal("-0.01")));
        assertEquals(new BigDecimal("1.25"),p.getPrice());
    }
    private static Product product(int id,String price) {
        return new Product(id,"Decimal "+id,"",new BigDecimal(price),30,"","Процессоры","");
    }
}
