package com.campus.trade.repository.order;

import com.campus.trade.entity.order.LogisticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LogisticsEventRepository extends JpaRepository<LogisticsEvent, Long> {
    List<LogisticsEvent> findByOrderIdOrderByOccurredAtDesc(Long orderId);
    long countByOrderId(Long orderId);
}
