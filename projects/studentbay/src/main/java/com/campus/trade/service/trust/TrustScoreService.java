package com.campus.trade.service.trust;

import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.order.OrderStatus;
import com.campus.trade.entity.order.TradeOrder;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.social.ReportStatus;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.repository.order.OrderRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.review.TradeReviewRepository;
import com.campus.trade.repository.social.ReportRepository;
import com.campus.trade.repository.user.TransactionRatingRepository;
import com.campus.trade.repository.user.UserRepository;
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
public class TrustScoreService {
    private final UserRepository users;
    private final OrderRepository orders;
    private final TransactionRatingRepository ratings;
    private final TradeReviewRepository tradeReviews;
    private final ReportRepository reports;
    private final ProductRepository products;

    public TrustScoreService(UserRepository users, OrderRepository orders,
                             TransactionRatingRepository ratings, TradeReviewRepository tradeReviews,
                             ReportRepository reports,
                             ProductRepository products) {
        this.users = users;
        this.orders = orders;
        this.ratings = ratings;
        this.tradeReviews = tradeReviews;
        this.reports = reports;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> score(Long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
        List<TradeOrder> related = orders.findAll().stream()
                .filter(order -> order.getBuyerId().equals(userId) || order.getSellerId().equals(userId))
                .toList();
        long completed = related.stream().filter(order -> order.getStatus() == OrderStatus.COMPLETED).count();
        long cancelled = related.stream().filter(order -> order.getStatus() == OrderStatus.CANCELLED).count();
        long upheldReports = reports.findAll().stream()
                .filter(report -> report.getStatus() == ReportStatus.RESOLVED)
                .map(report -> products.findById(report.getProductId()).orElse(null))
                .filter(product -> product != null && product.getSellerId().equals(userId))
                .count();
        Map<String, Integer> ratingValues = new LinkedHashMap<>();
        ratings.findByTargetUserIdOrderByCreatedAtDesc(userId).forEach(rating ->
                ratingValues.put(rating.getOrderId() + ":" + rating.getRaterId(), rating.getScore()));
        // develop's richer review flow also feeds Trust Score; deduplicate compatibility ratings per order/reviewer.
        tradeReviews.findByRevieweeIdOrderByCreatedAtDesc(userId).forEach(review ->
                ratingValues.put(review.getOrderId() + ":" + review.getReviewerId(), review.getRating()));
        long ratingCount = ratingValues.size();
        double averageRating = ratingValues.values().stream().mapToInt(Integer::intValue).average().orElse(0D);

        int verificationImpact = user.isStudentVerified() ? 15 : 0;
        int tradeImpact = (int) Math.min(15, completed * 3);
        int ratingImpact = ratingCount == 0 ? 0 : (int) Math.round((averageRating - 3D) * 6D);
        int reportImpact = -(int) Math.min(25, upheldReports * 8);
        double cancellationRate = related.isEmpty() ? 0D : (double) cancelled / related.size();
        int cancellationImpact = related.size() < 3 ? 0 : -(int) Math.min(15, Math.round(cancellationRate * 20D));
        int value = Math.max(0, Math.min(100,
                50 + verificationImpact + tradeImpact + ratingImpact + reportImpact + cancellationImpact));

        List<Map<String, Object>> factors = new ArrayList<>();
        factors.add(factor("校园邮箱认证", verificationImpact,
                user.isStudentVerified() ? "已完成校园身份认证" : "完成认证可提升信任分"));
        factors.add(factor("成功交易", tradeImpact, completed + " 笔订单已完成"));
        factors.add(factor("交易评分", ratingImpact,
                ratingCount == 0 ? "暂时没有交易评分" : decimal(averageRating) + " / 5（" + ratingCount + " 条）"));
        factors.add(factor("成立举报", reportImpact, upheldReports + " 条举报经平台确认"));
        factors.add(factor("取消稳定性", cancellationImpact,
                related.isEmpty() ? "暂无交易记录" : percent(cancellationRate) + " 订单取消率"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("displayName", user.getDisplayName());
        result.put("score", value);
        result.put("level", level(value));
        result.put("levelLabel", levelLabel(value));
        result.put("verified", user.isStudentVerified());
        result.put("completedTrades", completed);
        result.put("averageRating", ratingCount == 0 ? null : decimal(averageRating));
        result.put("ratingCount", ratingCount);
        result.put("upheldReports", upheldReports);
        result.put("cancelledOrders", cancelled);
        result.put("totalOrders", related.size());
        result.put("factors", factors);
        result.put("updatedAt", LocalDateTime.now());
        return result;
    }

    private Map<String, Object> factor(String label, int impact, String detail) {
        return Map.of("label", label, "impact", impact, "detail", detail);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private String percent(double value) {
        return BigDecimal.valueOf(value * 100D).setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private String level(int value) {
        if (value >= 85) return "EXCELLENT";
        if (value >= 70) return "GOOD";
        if (value >= 55) return "NORMAL";
        return "WATCH";
    }

    private String levelLabel(int value) {
        if (value >= 85) return "非常可信";
        if (value >= 70) return "信用良好";
        if (value >= 55) return "信用正常";
        return "建议谨慎交易";
    }
}
