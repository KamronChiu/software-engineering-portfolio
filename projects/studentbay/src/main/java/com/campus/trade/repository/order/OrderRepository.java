package com.campus.trade.repository.order;

import com.campus.trade.entity.order.TradeOrder;
import com.campus.trade.entity.order.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<TradeOrder, Long> {

    Optional<TradeOrder> findByOrderNo(String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from TradeOrder o where o.orderNo = :orderNo")
    Optional<TradeOrder> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @EntityGraph(attributePaths = "items")
    @Query("select distinct o from TradeOrder o")
    List<TradeOrder> findAllWithItems();

    List<TradeOrder> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    List<TradeOrder> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    long countByBuyerId(Long buyerId);

    long countByBuyerIdAndStatus(Long buyerId, OrderStatus status);

    long countBySellerIdAndStatus(Long sellerId, OrderStatus status);

    List<TradeOrder> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime cutoff);

    @EntityGraph(attributePaths = "items")
    @Query("select distinct o from TradeOrder o join o.items i where o.buyerId = :buyerId " +
            "and o.sellerId = :sellerId and i.productId = :productId order by o.createdAt desc")
    List<TradeOrder> findConversationOrders(@Param("buyerId") Long buyerId,
                                             @Param("sellerId") Long sellerId,
                                             @Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from TradeOrder o where o.id = :id")
    Optional<TradeOrder> findByIdForUpdate(@Param("id") Long id);
}
