package com.campus.trade.service.trust;

import com.campus.trade.entity.order.OrderStatus;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.review.ReviewType;
import com.campus.trade.repository.order.OrderRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.review.TradeReviewRepository;
import com.campus.trade.repository.social.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 我的空间「买家信用 / 卖家信用」评分服务。
 *
 * 指标口径：
 * - 卖出商品数：作为卖家完成的订单数（status = COMPLETED）
 * - 买进商品数：作为买家完成的订单数（status = COMPLETED）
 * - 好评数：收到的双向评价中 rating >= 4 的数量（卖家信用取买家评价我，买家信用取卖家评价我）
 * - 商品举报数：我发布的商品被举报的总数
 *
 * 信用百分比 = 基准 50 + 成功交易数 ×6（上限 +30）+ 好评数 ×6（上限 +30）
 *             − 商品被举报数 ×12（上限 −36），范围 0–100，四舍五入取整。
 */
@Service
public class SpaceCreditService {

    private final OrderRepository orders;
    private final TradeReviewRepository tradeReviews;
    private final ProductRepository products;
    private final ReportRepository reports;

    public SpaceCreditService(OrderRepository orders, TradeReviewRepository tradeReviews,
                              ProductRepository products, ReportRepository reports) {
        this.orders = orders;
        this.tradeReviews = tradeReviews;
        this.products = products;
        this.reports = reports;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> credit(Long userId) {
        long sold = orders.countBySellerIdAndStatus(userId, OrderStatus.COMPLETED);
        long bought = orders.countByBuyerIdAndStatus(userId, OrderStatus.COMPLETED);
        long sellerGood = goodReviews(userId, ReviewType.BUYER_REVIEWS_SELLER);
        long buyerGood = goodReviews(userId, ReviewType.SELLER_REVIEWS_BUYER);
        long reported = reportedCount(userId);

        int sellerCredit = creditValue(sold, sellerGood, reported);
        int buyerCredit = creditValue(bought, buyerGood, reported);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("sellerCredit", sellerCredit);
        result.put("buyerCredit", buyerCredit);
        result.put("sellerLevel", levelLabel(sellerCredit));
        result.put("buyerLevel", levelLabel(buyerCredit));
        result.put("soldCount", sold);
        result.put("boughtCount", bought);
        result.put("sellerGoodReviews", sellerGood);
        result.put("buyerGoodReviews", buyerGood);
        result.put("reportedCount", reported);
        result.put("rule", "信用百分比 = 基准 50 + 成功交易数 ×6（上限 +30）+ 好评数 ×6（上限 +30）− 商品被举报数 ×12（上限 −36），范围 0–100");
        return result;
    }

    private long goodReviews(Long userId, ReviewType type) {
        return tradeReviews.findByRevieweeIdAndReviewTypeOrderByCreatedAtDesc(userId, type).stream()
                .filter(review -> review.getRating() >= 4)
                .count();
    }

    private long reportedCount(Long userId) {
        List<Long> productIds = products.findBySellerIdOrderByCreatedAtDesc(userId).stream()
                .map(Product::getId)
                .toList();
        if (productIds.isEmpty()) {
            return 0;
        }
        return reports.countByProductIdIn(productIds);
    }

    private int creditValue(long trades, long goodReviews, long reported) {
        int value = 50;
        value += (int) Math.min(30, trades * 6);
        value += (int) Math.min(30, goodReviews * 6);
        value -= (int) Math.min(36, reported * 12);
        return Math.max(0, Math.min(100, value));
    }

    private String levelLabel(int value) {
        if (value >= 85) return "非常可信";
        if (value >= 70) return "信用良好";
        if (value >= 55) return "信用正常";
        return "建议谨慎交易";
    }
}
