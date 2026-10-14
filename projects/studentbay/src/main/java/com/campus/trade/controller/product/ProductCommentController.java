package com.campus.trade.controller.product;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.product.ProductComment;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.repository.product.ProductCommentRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.auth.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products/{productId}/comments")
public class ProductCommentController {
    private final ProductCommentRepository comments;
    private final ProductRepository products;
    private final UserRepository users;
    private final CurrentUserService currentUser;

    public ProductCommentController(ProductCommentRepository comments, ProductRepository products,
                                    UserRepository users, CurrentUserService currentUser) {
        this.comments = comments;
        this.products = products;
        this.users = users;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable Long productId, HttpSession session) {
        Product product = requireProduct(productId);
        Long viewerId = session.getAttribute(CurrentUserService.SESSION_USER_ID) instanceof Long id ? id : null;
        return ApiResponse.success(comments.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(comment -> view(comment, product, viewerId)).toList());
    }

    @PostMapping
    @Transactional
    public ApiResponse<Map<String, Object>> add(@PathVariable Long productId,
                                                 @Valid @RequestBody CommentRequest request,
                                                 HttpSession session) {
        Product product = requireProduct(productId);
        AppUser author = currentUser.require(session);
        ProductComment comment = new ProductComment();
        comment.setProductId(productId);
        comment.setUserId(author.getId());
        comment.setContent(request.content().trim());
        return ApiResponse.success(201, "留言已发布", view(comments.save(comment), product, author.getId()));
    }

    @DeleteMapping("/{commentId}")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long productId, @PathVariable Long commentId,
                                    HttpSession session) {
        Product product = requireProduct(productId);
        Long userId = currentUser.require(session).getId();
        ProductComment comment = comments.findById(commentId)
                .filter(item -> item.getProductId().equals(productId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "留言不存在"));
        if (!comment.getUserId().equals(userId) && !product.getSellerId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权删除该留言");
        }
        comments.delete(comment);
        return ApiResponse.success(200, "留言已删除", null);
    }

    private Product requireProduct(Long id) {
        return products.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "商品不存在"));
    }

    private Map<String, Object> view(ProductComment comment, Product product, Long viewerId) {
        AppUser author = users.findById(comment.getUserId()).orElse(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", comment.getId());
        result.put("userId", comment.getUserId());
        result.put("authorName", author == null ? "校园用户" : author.getDisplayName());
        result.put("studentVerified", author != null && author.isStudentVerified());
        result.put("seller", product.getSellerId().equals(comment.getUserId()));
        result.put("content", comment.getContent());
        result.put("createdAt", comment.getCreatedAt());
        result.put("canDelete", viewerId != null && (viewerId.equals(comment.getUserId()) || viewerId.equals(product.getSellerId())));
        return result;
    }

    public record CommentRequest(@NotBlank @Size(max = 500) String content) {}
}
