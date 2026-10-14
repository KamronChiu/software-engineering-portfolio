package com.campus.trade.repository.product;

import com.campus.trade.entity.product.PriceCutRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceCutRecordRepository extends JpaRepository<PriceCutRecord, Long> {
    boolean existsByProductIdAndBuyerId(Long productId, Long buyerId);
    java.util.Optional<PriceCutRecord> findByProductIdAndBuyerId(Long productId, Long buyerId);
    List<PriceCutRecord> findByProductIdOrderByCreatedAtAsc(Long productId);
    void deleteByProductId(Long productId);
}
