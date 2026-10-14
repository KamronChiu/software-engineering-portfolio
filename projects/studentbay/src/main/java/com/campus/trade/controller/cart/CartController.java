package com.campus.trade.controller.cart;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.cart.CartItem;
import com.campus.trade.entity.product.PriceCutRecord;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.product.ProductStatus;
import com.campus.trade.repository.cart.CartRepository;
import com.campus.trade.repository.product.PriceCutRecordRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.service.auth.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartRepository cart;
    private final ProductRepository products;
    private final PriceCutRecordRepository priceCutRecords;
    private final CurrentUserService currentUser;

    public CartController(CartRepository cart, ProductRepository products,
                          PriceCutRecordRepository priceCutRecords, CurrentUserService currentUser) {
        this.cart = cart;
        this.products = products;
        this.priceCutRecords = priceCutRecords;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpSession session) {
        Long userId = currentUser.require(session).getId();
        return ApiResponse.success(cart.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList());
    }

    @PostMapping("/{productId}")
    public ApiResponse<Map<String, Object>> add(@PathVariable Long productId, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        Product product = products.findById(productId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "商品不存在"));
        if (product.getStatus() != ProductStatus.AVAILABLE) {
            throw new BusinessException(HttpStatus.CONFLICT, "商品当前不可购买");
        }
        if (product.getSellerId().equals(userId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不能购买自己发布的商品");
        }
        CartItem item = cart.findByUserIdAndProductId(userId, productId).orElseGet(CartItem::new);
        item.setUserId(userId);
        item.setProductId(productId);
        item.setQuantity(1);
        return ApiResponse.success(view(cart.save(item)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        CartItem item = cart.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "购物车商品不存在"));
        if (!item.getUserId().equals(userId)) throw new BusinessException(HttpStatus.FORBIDDEN, "无权操作");
        cart.delete(item);
        return ApiResponse.success(null);
    }

    @GetMapping("/count")
    public ApiResponse<Long> count(HttpSession session) {
        return ApiResponse.success(cart.countByUserId(currentUser.require(session).getId()));
    }

    private Map<String, Object> view(CartItem item) {
        Product product = products.findById(item.getProductId()).orElseThrow();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.getId());
        result.put("productId", product.getId());
        result.put("sellerId", product.getSellerId());
        result.put("title", product.getTitle());
        // 买家「砍一刀」后的专属价贯穿购物车：该买家查看为专属价，其余买家与卖家仍为挂牌价
        PriceCutRecord cut = priceCutRecords.findByProductIdAndBuyerId(product.getId(), item.getUserId()).orElse(null);
        result.put("price", cut != null ? cut.getPriceAfter() : product.getPrice());
        result.put("originalPrice", product.getPrice());
        result.put("cutByMe", cut != null);
        result.put("imageUrl", product.getImageUrl());
        result.put("condition", product.getCondition());
        result.put("locationName", product.getLocationName());
        result.put("status", product.getStatus());
        result.put("quantity", item.getQuantity());
        return result;
    }
}
