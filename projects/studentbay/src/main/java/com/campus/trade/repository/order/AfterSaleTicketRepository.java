package com.campus.trade.repository.order;

import com.campus.trade.entity.order.AfterSaleStatus;
import com.campus.trade.entity.order.AfterSaleTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AfterSaleTicketRepository extends JpaRepository<AfterSaleTicket, Long> {
    Optional<AfterSaleTicket> findByOrderId(Long orderId);
    List<AfterSaleTicket> findAllByOrderByCreatedAtDesc();
    long countByStatusIn(List<AfterSaleStatus> statuses);
}
