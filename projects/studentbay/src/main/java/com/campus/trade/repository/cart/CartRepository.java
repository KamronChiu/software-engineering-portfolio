package com.campus.trade.repository.cart;

import com.campus.trade.entity.cart.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface CartRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);
    long countByUserId(Long userId);
    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, LocalDateTime start, LocalDateTime end);
}
