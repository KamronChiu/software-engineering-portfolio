package com.campus.trade.repository.product;

import com.campus.trade.entity.product.ProductBrowse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductBrowseRepository extends JpaRepository<ProductBrowse, Long> {

    List<ProductBrowse> findByUserIdOrderByViewedAtDesc(Long userId);

    Optional<ProductBrowse> findByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserId(Long userId);

    void deleteByProductId(Long productId);
}
