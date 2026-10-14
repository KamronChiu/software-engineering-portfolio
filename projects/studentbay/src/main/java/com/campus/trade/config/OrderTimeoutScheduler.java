package com.campus.trade.config;

import com.campus.trade.service.order.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OrderTimeoutScheduler {
    private final OrderService orders;
    private final long timeoutMinutes;

    public OrderTimeoutScheduler(OrderService orders,
                                 @Value("${app.order.payment-timeout-minutes:30}") long timeoutMinutes) {
        this.orders = orders;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Scheduled(fixedDelayString = "${app.order.timeout-scan-ms:60000}")
    public void cancelExpiredOrders() {
        orders.cancelExpiredUnpaidOrders(LocalDateTime.now().minusMinutes(timeoutMinutes));
    }
}
