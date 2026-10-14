package com.campus.trade.repository.product;

import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.product.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.time.LocalDateTime;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    List<Product> findBySellerIdOrderByCreatedAtDesc(Long sellerId);
    List<Product> findByStatusOrderByCreatedAtDesc(ProductStatus status);
    List<Product> findByCategoryAndIdNotAndStatusNotOrderByCreatedAtDesc(String category, Long id, ProductStatus status);
    long countBySellerId(Long sellerId);
    long countBySellerIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long sellerId, LocalDateTime start, LocalDateTime end);
}
