package com.campus.trade.controller.order;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.dto.order.*;
import com.campus.trade.service.auth.CurrentUserService;
import com.campus.trade.service.order.OrderService;
import com.campus.trade.service.order.AfterSaleService;
import com.campus.trade.service.order.PickupMeetingService;
import com.campus.trade.entity.order.AfterSaleTicket;
import com.campus.trade.entity.order.PickupMeetingProposal;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order")
public class OrderController {
    private final OrderService orders;
    private final AfterSaleService afterSales;
    private final PickupMeetingService pickupMeetings;
    private final CurrentUserService currentUser;

    public OrderController(OrderService orders, AfterSaleService afterSales,
                           PickupMeetingService pickupMeetings, CurrentUserService currentUser) {
        this.orders = orders;
        this.afterSales = afterSales;
        this.pickupMeetings = pickupMeetings;
        this.currentUser = currentUser;
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(201, "order created", orders.createOrder(request)));
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> checkout(@Valid @RequestBody CheckoutRequest request, HttpSession session) {
        Long buyerId = currentUser.require(session).getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(201, "订单已提交", orders.checkout(buyerId, request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long id, HttpSession session) {
        return ApiResponse.success(orders.getOrderForUser(id, currentUser.require(session).getId()));
    }

    @GetMapping("/mine/buyer")
    public ApiResponse<List<OrderResponse>> buyerOrders(HttpSession session) {
        return ApiResponse.success(orders.getBuyerOrders(currentUser.require(session).getId()));
    }

    @GetMapping("/mine/seller")
    public ApiResponse<List<OrderResponse>> sellerOrders(HttpSession session) {
        return ApiResponse.success(orders.getSellerOrders(currentUser.require(session).getId()));
    }

    @PostMapping("/{id}/payment")
    public ApiResponse<OrderResponse> payment(@PathVariable Long id, @Valid @RequestBody PaymentRequest request, HttpSession session) {
        return ApiResponse.success(orders.pay(id, currentUser.require(session).getId(), request.paymentMethod(), request.idempotencyKey()));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id, HttpSession session) {
        return ApiResponse.success(orders.cancelUnpaid(id, currentUser.require(session).getId()));
    }

    @PatchMapping("/{id}/price-adjustment")
    public ApiResponse<OrderResponse> adjustPrice(@PathVariable Long id,
                                                   @Valid @RequestBody AdjustOrderPriceRequest request,
                                                   HttpSession session) {
        return ApiResponse.success(orders.adjustPendingPrice(id, currentUser.require(session).getId(), request));
    }

    @PostMapping("/{id}/return-request")
    public ResponseEntity<ApiResponse<AfterSaleTicket>> requestReturn(@PathVariable Long id,
                                                                       @Valid @RequestBody ReturnRequest request,
                                                                       HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(201, "退货申请已提交",
                afterSales.requestReturn(id, currentUser.require(session).getId(), request)));
    }

    @GetMapping("/{id}/after-sale")
    public ApiResponse<AfterSaleTicket> afterSale(@PathVariable Long id, HttpSession session) {
        return ApiResponse.success(afterSales.getForParticipant(id, currentUser.require(session).getId()));
    }

    @PostMapping("/{id}/after-sale/seller-review")
    public ApiResponse<AfterSaleTicket> sellerReview(@PathVariable Long id,
                                                      @Valid @RequestBody AfterSaleReviewRequest request,
                                                      HttpSession session) {
        return ApiResponse.success(afterSales.sellerReview(id, currentUser.require(session).getId(), request));
    }

    @PostMapping("/{id}/after-sale/intervention")
    public ApiResponse<AfterSaleTicket> intervention(@PathVariable Long id,
                                                     @Valid @RequestBody InterventionRequest request,
                                                     HttpSession session) {
        return ApiResponse.success(afterSales.requestIntervention(id, currentUser.require(session).getId(), request));
    }

    @GetMapping("/{id}/pickup-proposals")
    public ApiResponse<List<PickupMeetingProposal>> pickupProposals(@PathVariable Long id, HttpSession session) {
        return ApiResponse.success(pickupMeetings.list(id, currentUser.require(session).getId()));
    }

    @PostMapping("/{id}/pickup-proposals")
    public ResponseEntity<ApiResponse<PickupMeetingProposal>> proposePickup(@PathVariable Long id,
                                                                             @Valid @RequestBody PickupMeetingProposalRequest request,
                                                                             HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(201, "面交修改已发送给对方",
                pickupMeetings.propose(id, currentUser.require(session).getId(), request)));
    }

    @PostMapping("/{id}/pickup-proposals/{proposalId}/respond")
    public ApiResponse<PickupMeetingProposal> respondPickup(@PathVariable Long id, @PathVariable Long proposalId,
                                                             @Valid @RequestBody PickupProposalDecisionRequest request,
                                                             HttpSession session) {
        return ApiResponse.success(pickupMeetings.respond(id, proposalId,
                currentUser.require(session).getId(), request));
    }

    @PostMapping("/{id}/courier-booking")
    public ApiResponse<OrderResponse> bookCourier(@PathVariable Long id, @Valid @RequestBody CourierBookingRequest request,
                                                   HttpSession session) {
        return ApiResponse.success(orders.bookCourier(id, currentUser.require(session).getId(), request));
    }

    @PostMapping("/{id}/confirm-shipment")
    public ApiResponse<OrderResponse> confirmShipment(@PathVariable Long id, @Valid @RequestBody ConfirmShipmentRequest request,
                                                       HttpSession session) {
        return ApiResponse.success(orders.confirmShipment(id, currentUser.require(session).getId(), request));
    }

    @PostMapping("/{id}/confirm-pickup-handover")
    public ApiResponse<OrderResponse> confirmPickupHandover(@PathVariable Long id,
                                                             @RequestBody(required = false) PickupCodeRequest request,
                                                             HttpSession session) {
        return ApiResponse.success(orders.confirmPickupHandover(id, currentUser.require(session).getId(),
                request == null ? null : request.code()));
    }

    @PostMapping("/{id}/confirm-receipt")
    public ApiResponse<OrderResponse> confirmReceipt(@PathVariable Long id,
                                                      @RequestBody(required = false) PickupCodeRequest request,
                                                      HttpSession session) {
        return ApiResponse.success(orders.receive(id, currentUser.require(session).getId(),
                request == null ? null : request.code()));
    }

    @GetMapping("/{id}/logistics")
    public ApiResponse<LogisticsResponse> logistics(@PathVariable Long id, HttpSession session) {
        return ApiResponse.success(orders.getLogistics(id, currentUser.require(session).getId()));
    }

    @PostMapping("/{id}/logistics/refresh")
    public ApiResponse<LogisticsResponse> refreshLogistics(@PathVariable Long id, HttpSession session) {
        return ApiResponse.success(orders.refreshLogistics(id, currentUser.require(session).getId()));
    }

    @GetMapping("/buyer/{buyerId}")
    public ApiResponse<List<OrderResponse>> getBuyerOrders(@PathVariable Long buyerId) { return ApiResponse.success(orders.getBuyerOrders(buyerId)); }
    @GetMapping("/seller/{sellerId}")
    public ApiResponse<List<OrderResponse>> getSellerOrders(@PathVariable Long sellerId) { return ApiResponse.success(orders.getSellerOrders(sellerId)); }
    @PatchMapping("/{id}/pay")
    public ApiResponse<OrderResponse> pay(@PathVariable Long id) { return ApiResponse.success(orders.pay(id)); }
    @PatchMapping("/{id}/ship")
    public ApiResponse<OrderResponse> ship(@PathVariable Long id) { return ApiResponse.success(orders.ship(id)); }
    @PatchMapping("/{id}/receive")
    public ApiResponse<OrderResponse> receive(@PathVariable Long id) { return ApiResponse.success(orders.receive(id)); }

    public record PaymentRequest(@jakarta.validation.constraints.NotBlank String paymentMethod,
                                 @jakarta.validation.constraints.NotBlank String idempotencyKey) {}
    public record PickupCodeRequest(String code) {}
}
