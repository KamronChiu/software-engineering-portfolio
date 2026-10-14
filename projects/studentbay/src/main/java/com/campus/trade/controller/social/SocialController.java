package com.campus.trade.controller.social;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.social.Favorite;
import com.campus.trade.entity.social.Report;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.social.FavoriteRepository;
import com.campus.trade.repository.social.ReportRepository;
import com.campus.trade.service.auth.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SocialController {
    private final FavoriteRepository favorites;
    private final ReportRepository reports;
    private final ProductRepository products;
    private final CurrentUserService currentUser;

    public SocialController(FavoriteRepository favorites, ReportRepository reports, ProductRepository products,
                            CurrentUserService currentUser) {
        this.favorites = favorites;
        this.reports = reports;
        this.products = products;
        this.currentUser = currentUser;
    }

    @PostMapping("/favorites/{productId}/toggle")
    public ApiResponse<Map<String, Object>> toggleFavorite(@PathVariable Long productId, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        requireProduct(productId);
        boolean active;
        var existing = favorites.findByUserIdAndProductId(userId, productId);
        if (existing.isPresent()) {
            favorites.delete(existing.get());
            active = false;
        } else {
            Favorite favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setProductId(productId);
            favorites.save(favorite);
            active = true;
        }
        return ApiResponse.success(Map.of("active", active, "count", favorites.countByProductId(productId)));
    }

    @PostMapping("/favorites/{productId}")
    public ApiResponse<Map<String, Object>> addFavorite(@PathVariable Long productId, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        requireProduct(productId);
        favorites.findByUserIdAndProductId(userId, productId).orElseGet(() -> {
            Favorite favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setProductId(productId);
            return favorites.save(favorite);
        });
        return ApiResponse.success(Map.of("active", true, "count", favorites.countByProductId(productId)));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<Map<String, Object>>> favorites(HttpSession session) {
        Long userId = currentUser.require(session).getId();
        return ApiResponse.success(favorites.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(favorite -> productView(requireProduct(favorite.getProductId()), favorite.getId())).toList());
    }

    @PostMapping("/reports")
    public ApiResponse<Report> report(@Valid @RequestBody ReportRequest request, HttpSession session) {
        Long userId = currentUser.require(session).getId();
        Product product = requireProduct(request.productId());
        if (product.getSellerId().equals(userId)) throw new BusinessException(HttpStatus.BAD_REQUEST, "不能举报自己的商品");
        Report report = new Report();
        report.setReporterId(userId);
        report.setProductId(product.getId());
        report.setReason(request.reason());
        report.setDetail(request.detail());
        return ApiResponse.success(201, "举报已提交", reports.save(report));
    }

    private Product requireProduct(Long id) {
        return products.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "商品不存在"));
    }

    private Map<String, Object> productView(Product product, Long favoriteId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("favoriteId", favoriteId);
        result.put("id", product.getId());
        result.put("title", product.getTitle());
        result.put("price", product.getPrice());
        result.put("imageUrl", product.getImageUrl());
        result.put("condition", product.getCondition());
        result.put("locationName", product.getLocationName());
        result.put("status", product.getStatus());
        return result;
    }

    public record ReportRequest(@NotNull Long productId, @NotBlank @Size(max = 80) String reason,
                                @Size(max = 1000) String detail) {}
}
