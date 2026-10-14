package com.campus.trade.controller.admin;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.common.BusinessException;
import com.campus.trade.entity.config.AppConfig;
import com.campus.trade.entity.product.Product;
import com.campus.trade.entity.product.ProductStatus;
import com.campus.trade.entity.order.OrderStatus;
import com.campus.trade.entity.order.PaymentStatus;
import com.campus.trade.entity.order.TradeOrder;
import com.campus.trade.entity.order.AfterSaleStatus;
import com.campus.trade.entity.order.AfterSaleTicket;
import com.campus.trade.entity.social.Report;
import com.campus.trade.entity.social.ReportStatus;
import com.campus.trade.entity.user.AppUser;
import com.campus.trade.entity.user.UserStatus;
import com.campus.trade.repository.config.AppConfigRepository;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.order.OrderRepository;
import com.campus.trade.repository.order.AfterSaleTicketRepository;
import com.campus.trade.repository.social.ReportRepository;
import com.campus.trade.repository.user.UserRepository;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.order.AfterSaleService;
import com.campus.trade.dto.order.AfterSaleReviewRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.EnumMap;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserRepository users;
    private final ProductRepository products;
    private final ReportRepository reports;
    private final OrderRepository orders;
    private final AfterSaleTicketRepository afterSaleTickets;
    private final AfterSaleService afterSales;
    private final AppConfigRepository configs;
    private final CurrentUserService currentUser;

    public AdminController(UserRepository users, ProductRepository products, ReportRepository reports, OrderRepository orders,
                           AfterSaleTicketRepository afterSaleTickets, AfterSaleService afterSales,
                           AppConfigRepository configs, CurrentUserService currentUser) {
        this.users = users;
        this.products = products;
        this.reports = reports;
        this.orders = orders;
        this.afterSaleTickets = afterSaleTickets;
        this.afterSales = afterSales;
        this.configs = configs;
        this.currentUser = currentUser;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            HttpSession session) {
        currentUser.requireAdmin(session);
        LocalDate rangeTo = to == null ? LocalDate.now() : to;
        LocalDate rangeFrom = from == null ? rangeTo.minusDays(6) : from;
        if (rangeFrom.isAfter(rangeTo)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(rangeFrom, rangeTo) > 365) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "日期范围最多为 366 天");
        }
        LocalDateTime rangeStart = rangeFrom.atStartOfDay();
        LocalDateTime rangeEnd = rangeTo.plusDays(1).atStartOfDay();
        List<TradeOrder> allOrders = orders.findAllWithItems().stream()
                .filter(order -> order.getCreatedAt() != null
                        && !order.getCreatedAt().isBefore(rangeStart)
                        && order.getCreatedAt().isBefore(rangeEnd))
                .toList();
        List<AppUser> allUsers = users.findAll();
        List<Report> allReports = reports.findAll();

        Map<OrderStatus, Long> orderStatuses = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            orderStatuses.put(status, allOrders.stream().filter(order -> order.getStatus() == status).count());
        }
        long activeUsers = allUsers.stream().filter(user -> user.getStatus() == UserStatus.ACTIVE).count();
        long disabledUsers = allUsers.size() - activeUsers;
        long pendingReports = allReports.stream().filter(report -> report.getStatus() == ReportStatus.PENDING).count();
        long refundedOrders = allOrders.stream().filter(order -> order.getPaymentStatus() == PaymentStatus.REFUNDED).count();
        long interventionOrders = afterSaleTickets.countByStatusIn(List.of(AfterSaleStatus.INTERVENTION_REQUESTED));
        BigDecimal grossMerchandiseValue = allOrders.stream()
                .filter(order -> order.getPaymentStatus() == PaymentStatus.PAID)
                .map(TradeOrder::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> dailyTrend = new ArrayList<>();
        for (LocalDate date = rangeFrom; !date.isAfter(rangeTo); date = date.plusDays(1)) {
            LocalDate trendDate = date;
            List<TradeOrder> dayOrders = allOrders.stream()
                    .filter(order -> order.getCreatedAt() != null && order.getCreatedAt().toLocalDate().equals(trendDate)).toList();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date);
            point.put("label", date.getMonthValue() + "/" + date.getDayOfMonth());
            point.put("orders", dayOrders.size());
            point.put("amount", dayOrders.stream().filter(order -> order.getPaymentStatus() == PaymentStatus.PAID)
                    .map(TradeOrder::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            dailyTrend.add(point);
        }

        Map<String, CategoryAccumulator> categoryAccumulators = new LinkedHashMap<>();
        allOrders.stream().filter(order -> order.getPaymentStatus() == PaymentStatus.PAID).forEach(order ->
                order.getItems().forEach(item -> {
                    String category = products.findById(item.getProductId()).map(Product::getCategory).orElse("其他");
                    CategoryAccumulator accumulator = categoryAccumulators.computeIfAbsent(category,
                            ignored -> new CategoryAccumulator());
                    accumulator.amount = accumulator.amount.add(item.getSubtotal());
                    accumulator.quantity += item.getQuantity();
                    accumulator.orderIds.add(order.getId());
                }));
        List<Map<String, Object>> categoryAnalysis = categoryAccumulators.entrySet().stream()
                .sorted((a, b) -> b.getValue().amount.compareTo(a.getValue().amount))
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category", entry.getKey());
                    row.put("amount", entry.getValue().amount);
                    row.put("quantity", entry.getValue().quantity);
                    row.put("orders", entry.getValue().orderIds.size());
                    return row;
                }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("range", Map.of("from", rangeFrom, "to", rangeTo));
        result.put("totalOrders", allOrders.size());
        result.put("grossMerchandiseValue", grossMerchandiseValue);
        result.put("orderStatuses", orderStatuses);
        result.put("totalUsers", allUsers.size());
        result.put("activeUsers", activeUsers);
        result.put("disabledUsers", disabledUsers);
        result.put("verifiedUsers", allUsers.stream().filter(AppUser::isStudentVerified).count());
        result.put("pendingReports", pendingReports);
        result.put("refundedOrders", refundedOrders);
        result.put("interventionOrders", interventionOrders);
        result.put("pendingAfterSales", afterSaleTickets.countByStatusIn(List.of(
                AfterSaleStatus.REQUESTED, AfterSaleStatus.SELLER_APPROVED,
                AfterSaleStatus.INTERVENTION_REQUESTED, AfterSaleStatus.REFUND_APPROVED)));
        result.put("dailyTrend", dailyTrend);
        result.put("categoryAnalysis", categoryAnalysis);
        return ApiResponse.success(result);
    }

    @GetMapping("/after-sales")
    public ApiResponse<List<AfterSaleTicket>> afterSales(HttpSession session) {
        currentUser.requireAdmin(session);
        return ApiResponse.success(afterSales.listForAdmin());
    }

    @PostMapping("/after-sales/{id}/review")
    public ApiResponse<AfterSaleTicket> reviewAfterSale(@PathVariable Long id,
                                                        @Valid @RequestBody AfterSaleReviewRequest request,
                                                        HttpSession session) {
        currentUser.requireAdmin(session);
        return ApiResponse.success(afterSales.adminReview(id, request));
    }

    @PostMapping("/after-sales/{id}/refund")
    public ApiResponse<AfterSaleTicket> refundAfterSale(@PathVariable Long id, HttpSession session) {
        currentUser.requireAdmin(session);
        return ApiResponse.success(afterSales.executeRefund(id));
    }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(HttpSession session) {
        currentUser.requireAdmin(session);
        return ApiResponse.success(users.findAll().stream().map(this::userView).toList());
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<Map<String, Object>> userStatus(@PathVariable Long id, @RequestBody StatusRequest request, HttpSession session) {
        currentUser.requireAdmin(session);
        AppUser user = users.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
        user.setStatus(UserStatus.valueOf(request.status()));
        return ApiResponse.success(userView(users.save(user)));
    }

    @GetMapping("/products")
    public ApiResponse<List<Product>> products(HttpSession session) {
        currentUser.requireAdmin(session);
        return ApiResponse.success(products.findAll());
    }

    @PatchMapping("/products/{id}/status")
    public ApiResponse<Product> productStatus(@PathVariable Long id, @RequestBody StatusRequest request, HttpSession session) {
        currentUser.requireAdmin(session);
        Product product = products.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "商品不存在"));
        product.setStatus(ProductStatus.valueOf(request.status()));
        return ApiResponse.success(products.save(product));
    }

    @GetMapping("/reports")
    public ApiResponse<List<Report>> reports(HttpSession session) {
        currentUser.requireAdmin(session);
        return ApiResponse.success(reports.findAllByOrderByCreatedAtDesc());
    }

    @PostMapping("/reports/{id}/process")
    public ApiResponse<Report> processReport(@PathVariable Long id, @RequestBody ReportProcessRequest request, HttpSession session) {
        currentUser.requireAdmin(session);
        Report report = reports.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "举报不存在"));
        report.setStatus(ReportStatus.valueOf(request.status()));
        report.setAdminNote(request.adminNote());
        if (request.takeDownProduct()) {
            products.findById(report.getProductId()).ifPresent(product -> {
                product.setStatus(ProductStatus.OFF_SHELF);
                products.save(product);
            });
        }
        return ApiResponse.success(reports.save(report));
    }

    @GetMapping("/map-config")
    public ApiResponse<Map<String, String>> mapConfig(HttpSession session) {
        currentUser.requireAdmin(session);
        Map<String, String> result = new LinkedHashMap<>();
        configs.findAll().forEach(config -> result.put(config.getKey().replace("map.", ""), config.getValue()));
        return ApiResponse.success(result);
    }

    @PutMapping("/map-config")
    public ApiResponse<Map<String, String>> updateMapConfig(@RequestBody Map<String, String> request, HttpSession session) {
        currentUser.requireAdmin(session);
        for (String key : List.of("centerLat", "centerLng", "zoom", "tileUrl", "attribution")) {
            String value = request.get(key);
            if (value != null && !value.isBlank()) {
                AppConfig config = configs.findById("map." + key).orElseGet(AppConfig::new);
                config.setKey("map." + key);
                config.setValue(value.trim());
                configs.save(config);
            }
        }
        return mapConfig(session);
    }

    private Map<String, Object> userView(AppUser user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("displayName", user.getDisplayName());
        result.put("email", user.getEmail());
        result.put("university", user.getUniversity());
        result.put("studentVerified", user.isStudentVerified());
        result.put("role", user.getRole());
        result.put("status", user.getStatus());
        result.put("createdAt", user.getCreatedAt());
        return result;
    }

    public record StatusRequest(String status) {}
    public record ReportProcessRequest(String status, String adminNote, boolean takeDownProduct) {}

    private static class CategoryAccumulator {
        private BigDecimal amount = BigDecimal.ZERO;
        private int quantity;
        private final Set<Long> orderIds = new HashSet<>();
    }
}
