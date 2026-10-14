package com.campus.trade.repository.product;

import com.campus.trade.entity.product.ProductComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;

public interface ProductCommentRepository extends JpaRepository<ProductComment, Long> {
    List<ProductComment> findByProductIdOrderByCreatedAtDesc(Long productId);
    long countByProductId(Long productId);
    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, LocalDateTime start, LocalDateTime end);
}
