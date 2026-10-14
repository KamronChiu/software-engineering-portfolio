package com.campus.trade.service.order;

import com.campus.trade.common.BusinessException;
import com.campus.trade.dto.order.*;
import com.campus.trade.entity.activity.ActivityType;
import com.campus.trade.entity.activity.UserActivity;
import com.campus.trade.entity.address.DeliveryAddress;
import com.campus.trade.entity.cart.CartItem;
import com.campus.trade.entity.coupon.Coupon;
import com.campus.trade.entity.order.*;
import com.campus.trade.entity.product.PriceCutRecord;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.product.ProductStatus;
import com.campus.trade.repository.activity.UserActivityRepository;
import com.campus.trade.repository.address.AddressRepository;
import com.campus.trade.repository.cart.CartRepository;
import com.campus.trade.repository.coupon.CouponRepository;
import com.campus.trade.repository.product.PriceCutRecordRepository;
import com.campus.trade.repository.order.LogisticsEventRepository;
import com.campus.trade.repository.order.OrderRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.review.TradeReviewRepository;
import com.campus.trade.service.reward.RewardService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OrderService {
    private static final DateTimeFormatter ORDER_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderRepository orders;
    private final CartRepository cart;
    private final ProductRepository products;
    private final PriceCutRecordRepository priceCutRecords;
    private final AddressRepository addresses;
    private final CouponRepository coupons;
    private final LogisticsEventRepository logisticsEvents;
    private final RewardService rewards;
    private final UserActivityRepository activities;
    private final TradeReviewRepository reviews;
    private final long paymentTimeoutMinutes;

    public OrderService(OrderRepository orders, CartRepository cart, ProductRepository products,
                        PriceCutRecordRepository priceCutRecords,
                        AddressRepository addresses, CouponRepository coupons,
                        LogisticsEventRepository logisticsEvents, RewardService rewards,
                        UserActivityRepository activities, TradeReviewRepository reviews,
                        @Value("${app.order.payment-timeout-minutes:30}") long paymentTimeoutMinutes) {
        this.orders = orders;
        this.cart = cart;
        this.products = products;
        this.priceCutRecords = priceCutRecords;
        this.addresses = addresses;
        this.coupons = coupons;
        this.logisticsEvents = logisticsEvents;
        this.rewards = rewards;
        this.activities = activities;
        this.reviews = reviews;
        this.paymentTimeoutMinutes = paymentTimeoutMinutes;
    }

    /** Compatibility endpoint for the course's first integration test. */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        if (Objects.equals(request.sellerId(), 1L)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "buyer cannot purchase their own product");
        }
        BigDecimal productAmount = money(request.unitPrice().multiply(BigDecimal.valueOf(request.quantity())));
        TradeOrder order = baseOrder(1L, request.sellerId(), productAmount,
                request.shippingCost() == null ? BigDecimal.ZERO : request.shippingCost());
        order.setShippingMethod(normalizeText(request.shippingMethod(), "PICKUP"));
        order.setShippingAddress(normalizeText(request.shippingAddress(), "Campus pickup"));
        order.addItem(item(request.productId(), request.productTitle(), request.unitPrice(), request.quantity()));
        return toResponse(orders.save(order));
    }

    @Transactional
    public List<OrderResponse> checkout(Long buyerId, CheckoutRequest request) {
        List<CartItem> selected = request.cartItemIds().stream().distinct().map(id -> {
            CartItem cartItem = cart.findById(id)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "购物车商品不存在"));
            if (!cartItem.getUserId().equals(buyerId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "无权结算该商品");
            }
            return cartItem;
        }).toList();

        boolean delivery = "DELIVERY".equalsIgnoreCase(request.shippingMethod());
        DeliveryAddress address = delivery ? requireAddress(request.addressId(), buyerId) : null;
        Coupon coupon = request.couponId() == null ? null : requireCoupon(request.couponId(), buyerId);
        boolean couponUsed = false;
        List<TradeOrder> created = new ArrayList<>();

        Map<Long, List<Map.Entry<CartItem, Product>>> sellerGroups = new LinkedHashMap<>();
        for (CartItem cartItem : selected) {
            Product product = products.findById(cartItem.getProductId())
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "商品不存在"));
            if (product.getStatus() != ProductStatus.AVAILABLE) {
                throw new BusinessException(HttpStatus.CONFLICT, product.getTitle() + " 已无法购买");
            }
            if (product.getSellerId().equals(buyerId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "不能购买自己发布的商品");
            }
            sellerGroups.computeIfAbsent(product.getSellerId(), ignored -> new ArrayList<>())
                    .add(Map.entry(cartItem, product));
        }

        for (Map.Entry<Long, List<Map.Entry<CartItem, Product>>> sellerGroup : sellerGroups.entrySet()) {
            List<Map.Entry<CartItem, Product>> groupItems = sellerGroup.getValue();
            BigDecimal productAmount = groupItems.stream()
                    .map(entry -> effectivePrice(entry.getValue(), buyerId))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal shipping = delivery ? new BigDecimal("5.00") : BigDecimal.ZERO;
            TradeOrder order = baseOrder(buyerId, sellerGroup.getKey(), productAmount, shipping);
            order.setShippingMethod(delivery ? "DELIVERY" : "PICKUP");
            order.setPaymentMethod(request.paymentMethod());
            if (address != null) {
                order.setRecipientName(address.getRecipientName());
                order.setRecipientPhone(address.getPhone());
                order.setShippingAddress(address.getDetail());
            } else {
                configurePickup(order, request, groupItems.get(0).getValue());
            }
            if (!couponUsed && coupon != null && productAmount.compareTo(coupon.getMinimumAmount()) >= 0) {
                BigDecimal discount = coupon.getDiscountAmount().min(productAmount);
                order.setDiscountAmount(money(discount));
                order.setCouponCode(coupon.getCode());
                order.setTotalAmount(money(order.getProductAmount().add(order.getShippingCost()).subtract(discount)));
                couponUsed = true;
            }
            for (Map.Entry<CartItem, Product> entry : groupItems) {
                Product product = entry.getValue();
                order.addItem(item(product.getId(), product.getTitle(), effectivePrice(product, buyerId), 1));
            }
            created.add(orders.save(order));
            for (Map.Entry<CartItem, Product> entry : groupItems) {
                Product product = entry.getValue();
                product.setStatus(ProductStatus.RESERVED);
                products.save(product);
                cart.delete(entry.getKey());
            }
        }

        if (coupon != null && couponUsed && coupon.getOwnerUserId() != null) {
            coupon.setUsed(true);
            coupons.save(coupon);
        }
        if (request.useCoins()) {
            applyCoinDiscount(buyerId, created);
        }
        if (selected.size() >= 3) {
            grantMultiBuyCoupon(buyerId);
        }
        return created.stream().map(order -> toResponse(order, buyerId)).toList();
    }

    /**
     * 买家「砍一刀」后的专属价：该买家结算/下单以专属价计，其余买家与卖家仍为挂牌价。
     */
    private BigDecimal effectivePrice(Product product, Long buyerId) {
        return priceCutRecords.findByProductIdAndBuyerId(product.getId(), buyerId)
                .map(PriceCutRecord::getPriceAfter)
                .orElse(product.getPrice());
    }

    private void configurePickup(TradeOrder order, CheckoutRequest request, Product firstProduct) {
        LocalDate date = request.pickupDate() == null ? LocalDate.now() : request.pickupDate();
        LocalTime start = request.pickupStartTime() == null ? LocalTime.of(17, 30) : request.pickupStartTime();
        LocalTime end = request.pickupEndTime() == null ? LocalTime.of(18, 0) : request.pickupEndTime();
        if (!end.isAfter(start)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "结束时间需要晚于开始时间");
        }
        String fallbackLocation = normalizeText(firstProduct.getLocationName(), "图书馆北门");
        String location = normalizeText(request.pickupLocation(), fallbackLocation);
        order.setPickupLocation(location);
        order.setPickupMeetingStartAt(LocalDateTime.of(date, start));
        order.setPickupMeetingEndAt(LocalDateTime.of(date, end));
        order.setShippingAddress(location);
        order.setBuyerPickupCode(generatePickupCode());
        order.setSellerPickupCode(generatePickupCode());
        while (order.getSellerPickupCode().equals(order.getBuyerPickupCode())) {
            order.setSellerPickupCode(generatePickupCode());
        }
    }

    private Coupon requireCoupon(Long couponId, Long buyerId) {
        Coupon coupon = coupons.findById(couponId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "优惠券不存在"));
        boolean ownerMatches = coupon.getOwnerUserId() == null || coupon.getOwnerUserId().equals(buyerId);
        boolean unexpired = coupon.getExpiresAt() == null || coupon.getExpiresAt().isAfter(LocalDateTime.now());
        if (!coupon.isEnabled() || coupon.isUsed() || !ownerMatches || !unexpired) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "优惠券不可用或已失效");
        }
        return coupon;
    }

    private void applyCoinDiscount(Long buyerId, List<TradeOrder> created) {
        BigDecimal payable = created.stream().map(TradeOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int maximumCoins = payable.multiply(new BigDecimal("0.20"))
                .multiply(BigDecimal.valueOf(RewardService.COINS_PER_YUAN))
                .setScale(0, RoundingMode.DOWN).intValue();
        RewardService.CoinUse coinUse = rewards.spendUpTo(buyerId, maximumCoins, "订单金币抵扣");
        int remainingCoins = coinUse.usedCoins();
        for (TradeOrder order : created) {
            if (remainingCoins <= 0) break;
            int orderCapacity = order.getTotalAmount().multiply(BigDecimal.valueOf(RewardService.COINS_PER_YUAN))
                    .setScale(0, RoundingMode.DOWN).intValue();
            int allocatedCoins = Math.min(remainingCoins, orderCapacity);
            BigDecimal discount = BigDecimal.valueOf(allocatedCoins)
                    .divide(BigDecimal.valueOf(RewardService.COINS_PER_YUAN), 2, RoundingMode.DOWN);
            order.setCoinsUsed(allocatedCoins);
            order.setCoinDiscountAmount(discount);
            order.setTotalAmount(money(order.getTotalAmount().subtract(discount)));
            orders.save(order);
            remainingCoins -= allocatedCoins;
        }
    }

    private void grantMultiBuyCoupon(Long buyerId) {
        Coupon reward = new Coupon();
        reward.setCode("MB3-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase());
        reward.setName("多件循环购奖励券");
        reward.setDiscountAmount(new BigDecimal("5.00"));
        reward.setMinimumAmount(new BigDecimal("50.00"));
        reward.setOwnerUserId(buyerId);
        reward.setSource("MULTI_BUY_REWARD");
        reward.setExpiresAt(LocalDateTime.now().plusDays(30));
        coupons.save(reward);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderForUser(Long id, Long userId) {
        TradeOrder order = findOrder(id);
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权查看该订单");
        }
        return toResponse(order, userId);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) { return toResponse(findOrder(id)); }

    @Transactional(readOnly = true)
    public List<OrderResponse> getBuyerOrders(Long buyerId) {
        return orders.findByBuyerIdOrderByCreatedAtDesc(buyerId).stream().map(order -> toResponse(order, buyerId)).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getSellerOrders(Long sellerId) {
        return orders.findBySellerIdOrderByCreatedAtDesc(sellerId).stream().map(order -> toResponse(order, sellerId)).toList();
    }

    @Transactional
    public OrderResponse pay(Long id) {
        TradeOrder order = findOrder(id);
        return pay(id, order.getBuyerId(), order.getPaymentMethod(), "legacy-" + id + "-" + UUID.randomUUID());
    }

    /** 买家支付成功时，为卖家记一条"商品已卖出"动态 */
    private void recordSoldActivities(TradeOrder order) {
        for (OrderItem item : order.getItems()) {
            UserActivity activity = new UserActivity();
            activity.setUserId(order.getSellerId());
            activity.setType(ActivityType.SOLD);
            activity.setProductId(item.getProductId());
            activity.setTitle(item.getProductTitle());
            activities.save(activity);
        }
    }

    @Transactional
    public OrderResponse pay(Long id, Long buyerId, String paymentMethod, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 80) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "支付请求缺少有效的幂等键");
        }
        TradeOrder order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getBuyerId().equals(buyerId)) throw new BusinessException(HttpStatus.FORBIDDEN, "无权支付该订单");
        String normalizedKey = idempotencyKey.trim();
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            if (normalizedKey.equals(order.getPaymentIdempotencyKey())) {
                return toResponse(order, buyerId);
            }
            throw new BusinessException(HttpStatus.CONFLICT, "订单已支付，请勿重复提交支付请求");
        }
        requireStatus(order, OrderStatus.PENDING_PAYMENT, "只有待支付订单可以支付");
        if (order.getPaymentExpiresAt() != null && order.getPaymentExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(HttpStatus.CONFLICT, "订单支付已超时，系统将自动取消并释放商品");
        }
        order.setPaymentIdempotencyKey(normalizedKey);
        order.setPaymentMethod(normalizeText(paymentMethod, "MOCK_PAY"));
        order.setPaidAt(LocalDateTime.now());
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.PAID);
        TradeOrder paid = orders.saveAndFlush(order);
        recordSoldActivities(paid);
        return toResponse(paid, buyerId);
    }

    /** Service-level compatibility for existing integrations; HTTP clients must supply their own key. */
    @Transactional
    public OrderResponse pay(Long id, Long buyerId, String paymentMethod) {
        return pay(id, buyerId, paymentMethod, "service-" + id);
    }

    @Transactional
    public OrderResponse confirmExternalPayment(String orderNo, String paymentMethod, String transactionId,
                                                BigDecimal paidAmount) {
        TradeOrder order = orders.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (order.getPaymentStatus() == PaymentStatus.PAID) return toResponse(order, order.getBuyerId());
        requireStatus(order, OrderStatus.PENDING_PAYMENT, "当前订单不能完成支付");
        if (paidAmount == null || money(paidAmount).compareTo(order.getTotalAmount()) != 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "支付金额与订单应付金额不一致");
        }
        if (order.getPaymentExpiresAt() != null && order.getPaymentExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(HttpStatus.CONFLICT, "订单支付已超时");
        }
        order.setPaymentMethod(paymentMethod);
        String externalPaymentKey = paymentMethod + "-" + transactionId;
        order.setPaymentIdempotencyKey(externalPaymentKey.substring(0, Math.min(80, externalPaymentKey.length())));
        order.setPaidAt(LocalDateTime.now());
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setStatus(OrderStatus.PAID);
        return toResponse(orders.saveAndFlush(order), order.getBuyerId());
    }

    @Transactional
    public int cancelExpiredUnpaidOrders(LocalDateTime cutoff) {
        List<TradeOrder> expired = orders.findByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT, cutoff);
        expired.forEach(order -> cancelAndRelease(order, "支付超时，系统自动取消"));
        return expired.size();
    }

    @Transactional
    public OrderResponse cancelUnpaid(Long id, Long buyerId) {
        TradeOrder order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有买家可以取消该订单");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有未付款订单可以直接取消");
        }
        cancelAndRelease(order, "买家主动取消未付款订单");
        return toResponse(order, buyerId);
    }

    @Transactional
    public OrderResponse adjustPendingPrice(Long id, Long sellerId, AdjustOrderPriceRequest request) {
        TradeOrder order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getSellerId().equals(sellerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有该订单卖家可以改价");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有买家尚未付款的订单可以改价");
        }
        BigDecimal productAmount = money(request.productAmount());
        BigDecimal shippingCost = money(request.shippingCost());
        BigDecimal payable = productAmount.add(shippingCost)
                .subtract(order.getDiscountAmount()).subtract(order.getCoinDiscountAmount());
        order.setProductAmount(productAmount);
        order.setShippingCost(shippingCost);
        order.setTotalAmount(money(payable.max(BigDecimal.ZERO)));
        order.setPriceAdjustedAt(LocalDateTime.now());
        order.setPriceAdjustmentCount(order.getPriceAdjustmentCount() + 1);
        return toResponse(orders.saveAndFlush(order), sellerId);
    }

    private void cancelAndRelease(TradeOrder order, String reason) {
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancellationReason(reason);
        order.getItems().forEach(item -> products.findById(item.getProductId()).ifPresent(product -> {
            if (product.getStatus() == ProductStatus.RESERVED) {
                product.setStatus(ProductStatus.AVAILABLE);
                products.save(product);
            }
        }));
        if (order.getCoinsUsed() > 0) {
            rewards.refundOrderCoins(order.getBuyerId(), order.getCoinsUsed(), order.getOrderNo());
        }
        if (order.getCouponCode() != null) {
            coupons.findByCode(order.getCouponCode()).ifPresent(coupon -> {
                if (coupon.getOwnerUserId() != null && coupon.getOwnerUserId().equals(order.getBuyerId())) {
                    coupon.setUsed(false);
                    coupons.save(coupon);
                }
            });
        }
        orders.save(order);
    }

    @Transactional
    public OrderResponse bookCourier(Long id, Long sellerId, CourierBookingRequest request) {
        TradeOrder order = sellerOrder(id, sellerId);
        requireStatus(order, OrderStatus.PAID, "订单尚未进入待发货状态");
        if (!"DELIVERY".equals(order.getShippingMethod())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "校内自提订单无需预约快递");
        }
        order.setCourierCompany(request.courierCompany());
        order.setPickupTime(request.pickupTime());
        assignCourier(order);
        order.setShipmentStatus(ShipmentStatus.BOOKED);
        order.setEstimatedDeliveryAt(LocalDateTime.now().plusDays(2));
        order.setStatus(OrderStatus.COURIER_BOOKED);
        TradeOrder saved = orders.save(order);
        addLogisticsEvent(saved, ShipmentStatus.BOOKED, "预约取件成功",
                saved.getCourierName() + "已接单，将按预约时间上门取件", "校园快递服务点", LocalDateTime.now());
        return toResponse(saved);
    }

    @Transactional
    public OrderResponse confirmShipment(Long id, Long sellerId, ConfirmShipmentRequest request) {
        TradeOrder order = sellerOrder(id, sellerId);
        requireStatus(order, OrderStatus.COURIER_BOOKED, "请先预约快递取件");
        order.setTrackingNumber(request.trackingNumber());
        order.setShippedAt(LocalDateTime.now());
        order.setShipmentStatus(ShipmentStatus.IN_TRANSIT);
        order.setStatus(OrderStatus.SHIPPED);
        TradeOrder saved = orders.save(order);
        LocalDateTime now = LocalDateTime.now();
        addLogisticsEvent(saved, ShipmentStatus.PICKED_UP, "快递员已取件",
                "商品已由" + saved.getCourierName() + "完成揽收", "校园快递服务点", now);
        addLogisticsEvent(saved, ShipmentStatus.IN_TRANSIT, "运输途中",
                "包裹已离开校园服务点，正在发往目的地", "校园集散中心", now.plusSeconds(1));
        return toResponse(saved);
    }

    @Transactional
    public OrderResponse confirmPickupHandover(Long id, Long sellerId, String buyerCode) {
        TradeOrder order = sellerOrder(id, sellerId);
        requireStatus(order, OrderStatus.PAID, "订单尚未进入待交付状态");
        if (!"PICKUP".equals(order.getShippingMethod())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "快递订单需要先预约取件");
        }
        requirePickupCode(buyerCode, pickupCodeFor(order, true), "请输入买家出示的取件码");
        order.setSellerHandoverConfirmed(true);
        order.setShippedAt(LocalDateTime.now());
        order.setStatus(OrderStatus.SHIPPED);
        return toResponse(orders.save(order), sellerId);
    }

    @Transactional
    public OrderResponse ship(Long id) {
        TradeOrder order = findOrder(id);
        requireStatus(order, OrderStatus.PAID, "only paid orders can be shipped");
        order.setStatus(OrderStatus.SHIPPED);
        order.setShippedAt(LocalDateTime.now());
        return toResponse(orders.saveAndFlush(order));
    }

    @Transactional
    public OrderResponse receive(Long id) {
        TradeOrder order = findOrder(id);
        requireStatus(order, OrderStatus.SHIPPED, "only shipped orders can be completed");
        order.setStatus(OrderStatus.COMPLETED);
        order.setReceivedAt(LocalDateTime.now());
        if (order.getShipmentStatus() != null) {
            order.setShipmentStatus(ShipmentStatus.DELIVERED);
            addLogisticsEvent(order, ShipmentStatus.DELIVERED, "订单已签收",
                    "买家已确认收到并验收商品", order.getShippingAddress(), LocalDateTime.now());
        }
        order.getItems().forEach(orderItem -> products.findById(orderItem.getProductId()).ifPresent(product -> {
            product.setStatus(ProductStatus.SOLD);
            products.save(product);
        }));
        return toResponse(orders.saveAndFlush(order));
    }

    @Transactional
    public OrderResponse receive(Long id, Long buyerId, String sellerCode) {
        TradeOrder order = findOrder(id);
        if (!order.getBuyerId().equals(buyerId)) throw new BusinessException(HttpStatus.FORBIDDEN, "无权确认该订单");
        if ("PICKUP".equals(order.getShippingMethod())) {
            requirePickupCode(sellerCode, pickupCodeFor(order, false), "请输入卖家出示的交付码");
            order.setBuyerReceiptConfirmed(true);
        }
        return receive(id);
    }

    private void requirePickupCode(String actual, String expected, String missingMessage) {
        if (actual == null || actual.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, missingMessage);
        }
        if (!actual.trim().equals(expected)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "取件码不正确，请与对方核对");
        }
    }

    @Transactional
    public LogisticsResponse getLogistics(Long id, Long userId) {
        TradeOrder order = participantOrder(id, userId);
        ensureShipment(order);
        return toLogisticsResponse(order, userId);
    }

    @Transactional
    public LogisticsResponse refreshLogistics(Long id, Long userId) {
        TradeOrder order = participantOrder(id, userId);
        ensureShipment(order);
        LocalDateTime now = LocalDateTime.now();
        if (order.getShipmentStatus() == ShipmentStatus.IN_TRANSIT) {
            order.setShipmentStatus(ShipmentStatus.AT_DESTINATION);
            addLogisticsEvent(order, ShipmentStatus.AT_DESTINATION, "到达目的地配送站",
                    "包裹已完成干线运输，正在进行到站分拣", "目的地校园配送站", now);
        } else if (order.getShipmentStatus() == ShipmentStatus.AT_DESTINATION) {
            order.setShipmentStatus(ShipmentStatus.OUT_FOR_DELIVERY);
            addLogisticsEvent(order, ShipmentStatus.OUT_FOR_DELIVERY, "正在派送",
                    "配送员正在将包裹送往收货地址，请保持电话畅通", order.getShippingAddress(), now);
        } else if (order.getShipmentStatus() == ShipmentStatus.OUT_FOR_DELIVERY
                || order.getShipmentStatus() == ShipmentStatus.DELIVERED) {
            throw new BusinessException(HttpStatus.CONFLICT, "暂时没有新的物流动态");
        } else {
            throw new BusinessException(HttpStatus.CONFLICT, "快递员取件后才能同步运输信息");
        }
        orders.save(order);
        return toLogisticsResponse(order, userId);
    }

    private DeliveryAddress requireAddress(Long addressId, Long buyerId) {
        if (addressId == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择收货地址");
        DeliveryAddress address = addresses.findById(addressId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "收货地址不存在"));
        if (!address.getUserId().equals(buyerId)) throw new BusinessException(HttpStatus.FORBIDDEN, "无权使用该地址");
        return address;
    }

    private TradeOrder sellerOrder(Long id, Long sellerId) {
        TradeOrder order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getSellerId().equals(sellerId)) throw new BusinessException(HttpStatus.FORBIDDEN, "无权操作该订单");
        return order;
    }

    private TradeOrder participantOrder(Long id, Long userId) {
        TradeOrder order = findOrder(id);
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权查看该订单物流");
        }
        return order;
    }

    private void assignCourier(TradeOrder order) {
        String[][] couriers = {
                {"李师傅", "95013 转 6201"},
                {"周师傅", "95013 转 7836"},
                {"王师傅", "95013 转 4518"},
                {"陈师傅", "95013 转 9064"}
        };
        int index = Math.floorMod(order.getId().intValue(), couriers.length);
        order.setCourierName(couriers[index][0]);
        order.setCourierPhone(couriers[index][1]);
        int code = Math.floorMod(order.getId().intValue() * 7919 + 23041, 1_000_000);
        order.setPickupCode(String.format("%06d", code));
    }

    private void ensureShipment(TradeOrder order) {
        if (order.getCourierCompany() == null || order.getCourierCompany().isBlank()) {
            throw new BusinessException(HttpStatus.CONFLICT, "该订单尚未预约快递");
        }
        if (order.getCourierName() == null) assignCourier(order);
        if (order.getEstimatedDeliveryAt() == null) order.setEstimatedDeliveryAt(LocalDateTime.now().plusDays(2));
        if (order.getShipmentStatus() == null) {
            order.setShipmentStatus(switch (order.getStatus()) {
                case COURIER_BOOKED -> ShipmentStatus.BOOKED;
                case SHIPPED -> ShipmentStatus.IN_TRANSIT;
                case COMPLETED -> ShipmentStatus.DELIVERED;
                default -> throw new BusinessException(HttpStatus.CONFLICT, "该订单尚未进入物流阶段");
            });
        }
        orders.save(order);
        if (logisticsEvents.countByOrderId(order.getId()) == 0) {
            addLogisticsEvent(order, order.getShipmentStatus(), "物流信息已建立",
                    "承运平台已接收订单信息", "校园快递服务点", LocalDateTime.now());
        }
    }

    private void addLogisticsEvent(TradeOrder order, ShipmentStatus status, String title,
                                   String description, String location, LocalDateTime occurredAt) {
        LogisticsEvent event = new LogisticsEvent();
        event.setOrderId(order.getId());
        event.setStatus(status);
        event.setTitle(title);
        event.setDescription(description);
        event.setLocation(location);
        event.setOccurredAt(occurredAt);
        logisticsEvents.save(event);
    }

    private LogisticsResponse toLogisticsResponse(TradeOrder order, Long viewerId) {
        List<LogisticsEventResponse> eventResponses = logisticsEvents.findByOrderIdOrderByOccurredAtDesc(order.getId())
                .stream()
                .map(event -> new LogisticsEventResponse(event.getId(), event.getStatus(), event.getTitle(),
                        event.getDescription(), event.getLocation(), event.getOccurredAt()))
                .toList();
        String productTitle = order.getItems().isEmpty() ? "校园闲置商品" : order.getItems().get(0).getProductTitle();
        return new LogisticsResponse(order.getId(), order.getOrderNo(), productTitle, order.getStatus(),
                order.getShipmentStatus(), order.getCourierCompany(), order.getCourierName(), order.getCourierPhone(),
                order.getPickupTime(), order.getPickupCode(), order.getTrackingNumber(), order.getEstimatedDeliveryAt(),
                order.getShippingAddress(), order.getSellerId().equals(viewerId), eventResponses);
    }

    private TradeOrder baseOrder(Long buyerId, Long sellerId, BigDecimal productAmount, BigDecimal shippingCost) {
        TradeOrder order = new TradeOrder();
        order.setOrderNo(generateOrderNo());
        order.setBuyerId(buyerId);
        order.setSellerId(sellerId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setPaymentExpiresAt(LocalDateTime.now().plusMinutes(paymentTimeoutMinutes));
        order.setProductAmount(money(productAmount));
        order.setShippingCost(money(shippingCost));
        order.setOriginalProductAmount(money(productAmount));
        order.setOriginalShippingCost(money(shippingCost));
        order.setPriceAdjustmentCount(0);
        order.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        order.setCoinDiscountAmount(BigDecimal.ZERO.setScale(2));
        order.setCoinsUsed(0);
        order.setTotalAmount(money(productAmount.add(shippingCost)));
        return order;
    }

    private OrderItem item(Long productId, String title, BigDecimal unitPrice, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setProductTitle(title.trim());
        item.setUnitPrice(money(unitPrice));
        item.setQuantity(quantity);
        item.setSubtotal(money(unitPrice.multiply(BigDecimal.valueOf(quantity))));
        return item;
    }

    private TradeOrder findOrder(Long id) {
        return orders.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
    }

    private void requireStatus(TradeOrder order, OrderStatus expected, String message) {
        if (order.getStatus() != expected) throw new BusinessException(HttpStatus.CONFLICT, message);
    }

    private String generateOrderNo() {
        return "SB" + LocalDateTime.now().format(ORDER_TIME_FORMAT)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private String generatePickupCode() {
        return String.format("%06d", Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000));
    }

    private String pickupCodeFor(TradeOrder order, boolean buyerCode) {
        String stored = buyerCode ? order.getBuyerPickupCode() : order.getSellerPickupCode();
        if (stored != null && !stored.isBlank()) return stored;
        long seed = (order.getId() == null ? 1L : order.getId()) * (buyerCode ? 7919L : 6151L) + (buyerCode ? 23041L : 47837L);
        return String.format("%06d", Math.floorMod(seed, 1_000_000));
    }

    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private String normalizeText(String value, String defaultValue) { return value == null || value.isBlank() ? defaultValue : value.trim(); }

    public OrderResponse toResponse(TradeOrder order) {
        return toResponse(order, null);
    }

    public OrderResponse toResponse(TradeOrder order, Long viewerId) {
        List<OrderItemResponse> items = order.getItems().stream().map(item -> new OrderItemResponse(
                item.getId(), item.getProductId(), item.getProductTitle(), item.getUnitPrice(), item.getQuantity(), item.getSubtotal())).toList();
        return new OrderResponse(order.getId(), order.getOrderNo(), order.getBuyerId(), order.getSellerId(),
                order.getStatus(), order.getPaymentStatus(), order.getProductAmount(), order.getShippingCost(),
                order.getDiscountAmount(), order.getCoinDiscountAmount(), order.getCoinsUsed(), order.getTotalAmount(),
                order.getShippingMethod(), order.getShippingAddress(),
                order.getRecipientName(), order.getRecipientPhone(), order.getCouponCode(), order.getPaymentMethod(),
                order.getPaidAt(), order.getPaymentExpiresAt(), order.getCancelledAt(), order.getCancellationReason(),
                order.getPriceAdjustedAt(),
                order.getCourierCompany(), order.getCourierName(), order.getCourierPhone(), order.getPickupTime(),
                order.getPickupCode(), order.getPickupLocation(), order.getPickupMeetingStartAt(), order.getPickupMeetingEndAt(),
                viewerId == null || "DELIVERY".equals(order.getShippingMethod()) ? null
                        : pickupCodeFor(order, order.getBuyerId().equals(viewerId)),
                order.isSellerHandoverConfirmed(), order.isBuyerReceiptConfirmed(),
                order.getTrackingNumber(), order.getShipmentStatus(), order.getEstimatedDeliveryAt(), order.getShippedAt(),
                order.getReceivedAt(), order.getCreatedAt(), order.getUpdatedAt(), items,
                viewerId != null && reviews.existsByOrderIdAndReviewerId(order.getId(), viewerId),
                viewerId != null && reviews.existsByOrderIdAndReviewerId(order.getId(),
                        order.getBuyerId().equals(viewerId) ? order.getSellerId() : order.getBuyerId()));
    }
}
