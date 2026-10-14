package com.campus.trade.service.payment;

import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.kernel.Config;
import com.alipay.easysdk.payment.common.models.AlipayTradeQueryResponse;
import com.alipay.easysdk.payment.page.models.AlipayTradePagePayResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.dto.order.OrderResponse;
import com.campus.trade.entity.order.OrderStatus;
import com.campus.trade.service.order.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class AlipaySandboxService {
    private final OrderService orders;
    private final boolean enabled;
    private final String gatewayHost;
    private final String appId;
    private final String merchantPrivateKey;
    private final String alipayPublicKey;
    private final String baseUrl;

    public AlipaySandboxService(OrderService orders,
                                @Value("${app.alipay.enabled:false}") boolean enabled,
                                @Value("${app.alipay.gateway-host:openapi.alipaydev.com}") String gatewayHost,
                                @Value("${app.alipay.app-id:}") String appId,
                                @Value("${app.alipay.merchant-private-key:}") String merchantPrivateKey,
                                @Value("${app.alipay.alipay-public-key:}") String alipayPublicKey,
                                @Value("${app.alipay.base-url:http://localhost:8080}") String baseUrl) {
        this.orders = orders;
        this.enabled = enabled;
        this.gatewayHost = gatewayHost;
        this.appId = appId;
        this.merchantPrivateKey = merchantPrivateKey;
        this.alipayPublicKey = alipayPublicKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public boolean configured() {
        return enabled && !blank(appId) && !blank(merchantPrivateKey) && !blank(alipayPublicKey);
    }

    public synchronized String createPagePayment(Long orderId, Long buyerId) {
        requireConfigured();
        OrderResponse order = orders.getOrderForUser(orderId, buyerId);
        if (!order.buyerId().equals(buyerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有买家可以发起支付");
        }
        if (order.status() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有待付款订单可以发起支付宝支付");
        }
        try {
            configure();
            String subject = order.items().isEmpty() ? "StudentBay 校园订单"
                    : "StudentBay · " + order.items().get(0).productTitle();
            AlipayTradePagePayResponse response = Factory.Payment.Page().pay(
                    subject, order.orderNo(), order.totalAmount().toPlainString(),
                    baseUrl + "/api/payments/alipay/return");
            if (response == null || response.body == null || response.body.isBlank()) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "支付宝沙箱未返回支付页面");
            }
            return response.body;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "支付宝沙箱调用失败：" + exception.getMessage());
        }
    }

    public synchronized boolean verifyNotificationAndConfirm(Map<String, String> parameters) {
        requireConfigured();
        String orderNo = parameters.get("out_trade_no");
        String transactionId = parameters.get("trade_no");
        String tradeStatus = parameters.get("trade_status");
        String totalAmount = parameters.get("total_amount");
        if (blank(orderNo) || blank(transactionId) || blank(totalAmount) || !appId.equals(parameters.get("app_id"))) {
            return false;
        }
        if (!paid(tradeStatus)) {
            return false;
        }
        try {
            configure();
            if (!Factory.Payment.Common().verifyNotify(parameters)) return false;
            orders.confirmExternalPayment(orderNo, "ALIPAY_SANDBOX", transactionId, new BigDecimal(totalAmount));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * A browser return only proves that the buyer came back. Verify its signature and query Alipay before
     * changing local order state; the asynchronous notification remains the primary confirmation channel.
     */
    public synchronized boolean verifyReturnAndConfirm(Map<String, String> parameters) {
        requireConfigured();
        String orderNo = parameters.get("out_trade_no");
        if (blank(orderNo) || !appId.equals(parameters.get("app_id"))) return false;
        try {
            configure();
            if (!Factory.Payment.Common().verifyNotify(parameters)) return false;
            AlipayTradeQueryResponse result = Factory.Payment.Common().query(orderNo);
            if (result == null || !"10000".equals(result.code) || !orderNo.equals(result.outTradeNo)
                    || blank(result.tradeNo) || blank(result.totalAmount) || !paid(result.tradeStatus)) {
                return false;
            }
            orders.confirmExternalPayment(orderNo, "ALIPAY_SANDBOX", result.tradeNo,
                    new BigDecimal(result.totalAmount));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean paid(String tradeStatus) {
        return "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
    }

    private void configure() {
        Config config = new Config();
        config.protocol = "https";
        config.gatewayHost = gatewayHost;
        config.signType = "RSA2";
        config.appId = appId.trim();
        config.merchantPrivateKey = merchantPrivateKey.trim();
        config.alipayPublicKey = alipayPublicKey.trim();
        config.notifyUrl = baseUrl + "/api/payments/alipay/notify";
        Factory.setOptions(config);
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "支付宝沙箱尚未配置，请设置 ALIPAY_APP_ID、应用私钥和支付宝公钥");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
