package com.campus.trade.service.trust;

import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.order.OrderStatus;
import com.campus.trade.entity.order.TradeOrder;
import com.campus.trade.repository.order.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TransactionRiskService {
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("500.00");
    private static final BigDecimal LARGE_PRICE_CHANGE_PERCENT = new BigDecimal("25.0");

    private final OrderRepository orders;
    private final TrustScoreService trustScores;

    public TransactionRiskService(OrderRepository orders, TrustScoreService trustScores) {
        this.orders = orders;
        this.trustScores = trustScores;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> assess(Long orderId, Long viewerId) {
        TradeOrder order = orders.findById(orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getBuyerId().equals(viewerId) && !order.getSellerId().equals(viewerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权查看该订单风险提示");
        }
        List<Map<String, Object>> alerts = new ArrayList<>();
        if (order.getTotalAmount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            alerts.add(alert("HIGH_VALUE", "HIGH", "大额交易提醒",
                    "本单金额达到 ¥" + order.getTotalAmount() + "，建议当面验货、核对身份并仅通过平台支付。"));
        }

        Map<String, Object> buyerTrust = trustScores.score(order.getBuyerId());
        long totalOrders = ((Number) buyerTrust.get("totalOrders")).longValue();
        long cancelled = ((Number) buyerTrust.get("cancelledOrders")).longValue();
        if (totalOrders >= 3 && cancelled >= 2 && (double) cancelled / totalOrders >= .30D) {
            alerts.add(alert("FREQUENT_CANCELLATION", "MEDIUM", "频繁取消提醒",
                    "买家近期历史订单取消比例偏高，卖家交付前请再次确认交易时间。"));
        }

        BigDecimal original = safe(order.getOriginalProductAmount()).add(safe(order.getOriginalShippingCost()));
        BigDecimal current = safe(order.getProductAmount()).add(safe(order.getShippingCost()));
        BigDecimal changePercent = original.signum() == 0 ? BigDecimal.ZERO
                : current.subtract(original).abs().multiply(BigDecimal.valueOf(100))
                .divide(original, 1, RoundingMode.HALF_UP);
        if (order.getPriceAdjustmentCount() >= 2 || changePercent.compareTo(LARGE_PRICE_CHANGE_PERCENT) >= 0) {
            alerts.add(alert("ABNORMAL_PRICE_CHANGE", "HIGH", "异常改价提醒",
                    "订单已改价 " + order.getPriceAdjustmentCount() + " 次，当前金额与创建时相差 " + changePercent + "% ，付款前请与卖家确认。"));
        } else if (order.getPriceAdjustmentCount() == 1) {
            alerts.add(alert("PRICE_CHANGED", "LOW", "订单金额已调整",
                    "卖家修改了商品价或运费，创建时 ¥" + original + "，现在 ¥" + current + "。"));
        }

        int counterpartyScore = ((Number) trustScores.score(
                order.getBuyerId().equals(viewerId) ? order.getSellerId() : order.getBuyerId()).get("score")).intValue();
        if (counterpartyScore < 55) {
            alerts.add(alert("LOW_TRUST", "MEDIUM", "交易对象信用提示",
                    "对方 Trust Score 为 " + counterpartyScore + "，建议保留聊天记录并优先校园面交。"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("riskLevel", alerts.stream().anyMatch(item -> "HIGH".equals(item.get("severity"))) ? "HIGH"
                : alerts.stream().anyMatch(item -> "MEDIUM".equals(item.get("severity"))) ? "MEDIUM"
                : alerts.isEmpty() ? "CLEAR" : "LOW");
        result.put("alerts", alerts);
        result.put("checkedAt", LocalDateTime.now());
        return result;
    }

    private Map<String, Object> alert(String code, String severity, String title, String message) {
        return Map.of("code", code, "severity", severity, "title", title, "message", message);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
