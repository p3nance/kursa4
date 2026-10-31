package com.example.authapp.services;

import com.example.authapp.network.Money;
import java.math.BigDecimal;
import com.example.authapp.models.Cart;
import com.example.authapp.models.Cart.CartItem;
import com.example.authapp.models.Product;
import com.example.authapp.models.PromoCode;
import com.example.authapp.dto.CartItemDTO;
import com.example.authapp.repositories.CartRepository;
import com.example.authapp.network.SupabaseHttp;
import config.SessionManager;
import java.util.ArrayList;
import java.util.List;

/** Each multi-request operation pins one account/epoch and publishes atomically. */
public class CartService {
    private volatile PromoCode appliedPromoCode;
    public void loadUserCart() throws Exception {
        if(SessionManager.getUserId()==null)return;
        var lease=SessionManager.requireSession();
        try(var scope=SupabaseHttp.forSession(lease)){
            List<CartItemDTO> rows=CartRepository.loadCartFromSupabase(lease.userId());
            List<CartItem> loaded=new ArrayList<>();
            for(CartItemDTO dto:rows){
                Product product=new Product(dto.productId,dto.productName,"",dto.price,1,dto.productImage,"","");
                loaded.add(new CartItem(product,dto.quantity));
            }
            SessionManager.runIfCurrent(lease,()->Cart.getInstance().replaceItems(loaded));
        } catch(Exception ex){throw new Exception("Не удалось загрузить корзину. Проверьте текущий аккаунт и повторите действие.",ex);}
    }
    public void addProductToCart(Product product,int quantity) throws Exception {
        if(product==null)throw new IllegalArgumentException("Товар не может быть null");
        if(quantity<=0)throw new IllegalArgumentException("Количество должно быть больше 0");
        var lease=SessionManager.requireSession();
        try(var scope=SupabaseHttp.forSession(lease)){
            List<CartItemDTO> rows=CartRepository.loadCartFromSupabase(lease.userId());
            CartItemDTO existing=rows.stream().filter(item->item.productId==product.getId()).findFirst().orElse(null);
            if(existing!=null){
                int next=Math.addExact(existing.quantity,quantity);
                if(next>product.getStock())throw new IllegalArgumentException("Недостаточно товара в наличии с учётом вашей корзины");
                CartRepository.updateCartItemInSupabase(existing.cartItemId,next);
            }else{
                if(quantity>product.getStock())throw new IllegalArgumentException("Недостаточно товара в наличии");
                CartRepository.addCartItemToSupabase(lease.userId(),new CartItemDTO(0,product.getId(),product.getName(),product.getPrice(),quantity,product.getImageUrl()));
            }
            SessionManager.runIfCurrent(lease,()->Cart.getInstance().addProduct(product,quantity));
        }catch(Exception ex){throw new Exception("Не удалось добавить товар. Проверьте корзину текущего аккаунта перед повтором.",ex);}
    }
    public void addProductToCart(Product product) throws Exception {addProductToCart(product,1);}
    public void removeFromCart(Product product) throws Exception {
        if(product==null)throw new IllegalArgumentException("Товар не может быть null");
        var lease=SessionManager.requireSession();
        try(var scope=SupabaseHttp.forSession(lease)){
            for(CartItemDTO row:CartRepository.loadCartFromSupabase(lease.userId()))if(row.productId==product.getId()){CartRepository.removeCartItemFromSupabase(row.cartItemId);break;}
            SessionManager.runIfCurrent(lease,()->Cart.getInstance().removeProduct(product));
        }catch(Exception ex){throw new Exception("Не удалось удалить товар. Обновите корзину текущего аккаунта.",ex);}
    }
    public void updateCartItemQuantity(Product product,int quantity) throws Exception {
        if(product==null)throw new IllegalArgumentException("Товар не может быть null");
        if(quantity<=0){removeFromCart(product);return;}
        var lease=SessionManager.requireSession();
        try(var scope=SupabaseHttp.forSession(lease)){
            boolean found=false;
            for(CartItemDTO row:CartRepository.loadCartFromSupabase(lease.userId()))if(row.productId==product.getId()){CartRepository.updateCartItemInSupabase(row.cartItemId,quantity);found=true;break;}
            if(!found)throw new IllegalStateException("Позиция больше не существует. Обновите корзину.");
            SessionManager.runIfCurrent(lease,()->{Cart cart=Cart.getInstance();cart.removeProduct(product);cart.addProduct(product,quantity);});
        }catch(Exception ex){throw new Exception("Не удалось изменить количество. Обновите корзину текущего аккаунта.",ex);}
    }
    public void clearCart() throws Exception {
        var lease=SessionManager.requireSession();
        try(var scope=SupabaseHttp.forSession(lease)){
            CartRepository.clearUserCart(lease.userId());
            SessionManager.runIfCurrent(lease,()->{Cart.getInstance().clear();appliedPromoCode=null;});
        }catch(Exception ex){throw new Exception("Не удалось очистить корзину текущего аккаунта.",ex);}
    }
    public BigDecimal getCartTotal(){return Cart.getInstance().getTotal();}
    public int getCartSize(){return Cart.getInstance().getTotalQuantity();}
    public int getCartItemsCount(){return Cart.getInstance().getUniqueItemsCount();}
    public List<CartItem> getCartItems(){return Cart.getInstance().getItems();}
    public Cart getCurrentCart(){return Cart.getInstance();}
    public BigDecimal applyDiscount(String code) throws Exception {
        if(code==null||code.isBlank())throw new IllegalArgumentException("Промокод не может быть пустым");
        var lease=SessionManager.requireSession();
        try(var scope=SupabaseHttp.forSession(lease)){
            PromoCode promo=new com.example.authapp.repositories.PromoCodeRepository().validatePromoCode(code);
            SessionManager.assertCurrent(lease);
            return Money.discount(getCartTotal(),promo.getDiscountPercent());
        }
    }
    public int getProductQuantity(Product product){return Cart.getInstance().getItems().stream().filter(item->item.getProduct().getId()==product.getId()).mapToInt(CartItem::getQuantity).sum();}
    public boolean isProductInCart(Product product){return getProductQuantity(product)>0;}
    public void setAppliedPromoCode(PromoCode value){appliedPromoCode=value;}
    public PromoCode getAppliedPromoCode(){return appliedPromoCode;}
    public boolean hasAppliedPromoCode(){return appliedPromoCode!=null;}
    public void clearAppliedPromoCode(){appliedPromoCode=null;}
}
