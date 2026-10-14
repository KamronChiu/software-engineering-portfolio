package com.campus.trade.repository.social;

import com.campus.trade.entity.social.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    Optional<Favorite> findByUserIdAndProductId(Long userId, Long productId);
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByProductId(Long productId);
}
