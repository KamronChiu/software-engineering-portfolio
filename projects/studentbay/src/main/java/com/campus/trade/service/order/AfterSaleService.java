package com.campus.trade.service.order;

import com.campus.trade.common.BusinessException;
import com.campus.trade.dto.order.AfterSaleReviewRequest;
import com.campus.trade.dto.order.InterventionRequest;
import com.campus.trade.dto.order.ReturnRequest;
import com.campus.trade.entity.chat.ChatMessage;
import com.campus.trade.entity.chat.Conversation;
import com.campus.trade.entity.order.*;
import com.campus.trade.entity.product.ProductStatus;
import com.campus.trade.repository.chat.ChatMessageRepository;
import com.campus.trade.repository.chat.ConversationRepository;
import com.campus.trade.repository.order.AfterSaleTicketRepository;
import com.campus.trade.repository.order.OrderRepository;
import com.campus.trade.repository.product.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AfterSaleService {
    private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AfterSaleTicketRepository tickets;
    private final OrderRepository orders;
    private final ProductRepository products;
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;

    public AfterSaleService(AfterSaleTicketRepository tickets, OrderRepository orders, ProductRepository products,
                            ConversationRepository conversations, ChatMessageRepository messages) {
        this.tickets = tickets;
        this.orders = orders;
        this.products = products;
        this.conversations = conversations;
        this.messages = messages;
    }

    @Transactional
    public AfterSaleTicket requestReturn(Long orderId, Long buyerId, ReturnRequest request) {
        TradeOrder order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getBuyerId().equals(buyerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有买家可以申请退货");
        }
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BusinessException(HttpStatus.CONFLICT, "未付款订单请直接取消，无需申请退款");
        }
        if (order.getStatus() != OrderStatus.PAID
                && order.getStatus() != OrderStatus.COURIER_BOOKED
                && order.getStatus() != OrderStatus.SHIPPED
                && order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前订单状态无法申请退款");
        }
        if (order.getStatus() == OrderStatus.COMPLETED) {
            LocalDateTime receivedAt = order.getReceivedAt() == null ? order.getUpdatedAt() : order.getReceivedAt();
            if (receivedAt != null && LocalDateTime.now().isAfter(receivedAt.plusDays(7))) {
                throw new BusinessException(HttpStatus.CONFLICT, "确认收货已超过 7 天，售后申请入口已关闭");
            }
        }
        if (tickets.findByOrderId(orderId).isPresent()) {
            throw new BusinessException(HttpStatus.CONFLICT, "该订单已有售后工单");
        }
        AfterSaleTicket ticket = new AfterSaleTicket();
        ticket.setWorkOrderNo("AS" + LocalDateTime.now().format(NUMBER_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase(Locale.ROOT));
        ticket.setOrderId(orderId);
        ticket.setBuyerId(order.getBuyerId());
        ticket.setSellerId(order.getSellerId());
        ticket.setStatus(AfterSaleStatus.REQUESTED);
        ticket.setPreviousOrderStatus(order.getStatus());
        ticket.setReason(request.reason().trim());
        ticket.setDescription(trim(request.description()));
        ticket.setRefundAmount(order.getTotalAmount());
        order.setStatus(OrderStatus.RETURN_REQUESTED);
        orders.save(order);
        AfterSaleTicket saved = tickets.save(ticket);
        notifyCounterparty(order, buyerId, "AFTER_SALE_REQUESTED",
                "买家已提交售后申请", request.reason().trim());
        return saved;
    }

    @Transactional(readOnly = true)
    public AfterSaleTicket getForParticipant(Long orderId, Long userId) {
        TradeOrder order = requireParticipant(orderId, userId);
        return tickets.findByOrderId(order.getId()).orElse(null);
    }

    @Transactional
    public AfterSaleTicket sellerReview(Long orderId, Long sellerId, AfterSaleReviewRequest request) {
        TradeOrder order = requireOrder(orderId);
        if (!order.getSellerId().equals(sellerId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有该订单卖家可以审核退货申请");
        }
        AfterSaleTicket ticket = requireTicket(orderId);
        if (ticket.getStatus() != AfterSaleStatus.REQUESTED) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前工单不在卖家审核阶段");
        }
        boolean approved = decision(request.decision());
        ticket.setSellerNote(trim(request.note()));
        ticket.setSellerReviewedAt(LocalDateTime.now());
        ticket.setStatus(approved ? AfterSaleStatus.SELLER_APPROVED : AfterSaleStatus.SELLER_REJECTED);
        order.setStatus(approved ? OrderStatus.RETURN_APPROVED : ticket.getPreviousOrderStatus());
        orders.save(order);
        AfterSaleTicket saved = tickets.save(ticket);
        notifyCounterparty(order, sellerId, approved ? "AFTER_SALE_APPROVED" : "AFTER_SALE_REJECTED",
                approved ? "卖家已同意售后申请" : "卖家已拒绝售后申请",
                trim(request.note()));
        return saved;
    }

    @Transactional
    public AfterSaleTicket requestIntervention(Long orderId, Long userId, InterventionRequest request) {
        TradeOrder order = requireParticipant(orderId, userId);
        AfterSaleTicket ticket = requireTicket(orderId);
        if (ticket.getStatus() != AfterSaleStatus.SELLER_REJECTED
                && ticket.getStatus() != AfterSaleStatus.SELLER_APPROVED
                && ticket.getStatus() != AfterSaleStatus.REQUESTED) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前售后状态无法申请平台介入");
        }
        ticket.setStatus(AfterSaleStatus.INTERVENTION_REQUESTED);
        ticket.setInterventionReason(request.reason().trim());
        ticket.setInterventionAt(LocalDateTime.now());
        order.setStatus(OrderStatus.INTERVENTION);
        orders.save(order);
        AfterSaleTicket saved = tickets.save(ticket);
        notifyCounterparty(order, userId, "INTERVENTION_REQUESTED",
                "已申请平台介入", request.reason().trim());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AfterSaleTicket> listForAdmin() {
        return tickets.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public AfterSaleTicket adminReview(Long ticketId, AfterSaleReviewRequest request) {
        AfterSaleTicket ticket = requireTicketById(ticketId);
        if (ticket.getStatus() != AfterSaleStatus.SELLER_APPROVED
                && ticket.getStatus() != AfterSaleStatus.INTERVENTION_REQUESTED) {
            throw new BusinessException(HttpStatus.CONFLICT, "工单尚未进入平台退款审核阶段");
        }
        TradeOrder order = requireOrder(ticket.getOrderId());
        boolean approved = decision(request.decision());
        ticket.setAdminNote(trim(request.note()));
        ticket.setAdminReviewedAt(LocalDateTime.now());
        ticket.setStatus(approved ? AfterSaleStatus.REFUND_APPROVED : AfterSaleStatus.REFUND_REJECTED);
        order.setStatus(approved ? OrderStatus.REFUND_APPROVED : ticket.getPreviousOrderStatus());
        orders.save(order);
        return tickets.save(ticket);
    }

    @Transactional
    public AfterSaleTicket executeRefund(Long ticketId) {
        AfterSaleTicket ticket = requireTicketById(ticketId);
        if (ticket.getStatus() != AfterSaleStatus.REFUND_APPROVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "退款审核通过后才可执行退款");
        }
        TradeOrder order = orders.findByIdForUpdate(ticket.getOrderId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
        order.setStatus(OrderStatus.REFUNDED);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        order.getItems().forEach(item -> products.findById(item.getProductId()).ifPresent(product -> {
            product.setStatus(ProductStatus.AVAILABLE);
            products.save(product);
        }));
        ticket.setStatus(AfterSaleStatus.REFUNDED);
        ticket.setRefundedAt(LocalDateTime.now());
        orders.save(order);
        return tickets.save(ticket);
    }

    private TradeOrder requireParticipant(Long orderId, Long userId) {
        TradeOrder order = requireOrder(orderId);
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权查看该售后工单");
        }
        return order;
    }

    private TradeOrder requireOrder(Long id) {
        return orders.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
    }

    private AfterSaleTicket requireTicket(Long orderId) {
        return tickets.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "售后工单不存在"));
    }

    private AfterSaleTicket requireTicketById(Long id) {
        return tickets.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "售后工单不存在"));
    }

    private boolean decision(String raw) {
        if ("APPROVE".equalsIgnoreCase(raw)) return true;
        if ("REJECT".equalsIgnoreCase(raw)) return false;
        throw new BusinessException(HttpStatus.BAD_REQUEST, "审核决定必须为 APPROVE 或 REJECT");
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 将售后状态写入订单关联会话。消息仍由实际操作人发出，因此现有未读统计
     * 可以直接把它计入对方账号，无需再维护一套容易不同步的通知表。
     */
    private void notifyCounterparty(TradeOrder order, Long senderId, String event,
                                    String title, String detail) {
        if (order.getItems().isEmpty()) return;
        Long productId = order.getItems().get(0).getProductId();
        Conversation conversation = conversations.findByProductIdAndBuyerId(productId, order.getBuyerId())
                .orElseGet(() -> {
                    Conversation created = new Conversation();
                    created.setProductId(productId);
                    created.setBuyerId(order.getBuyerId());
                    created.setSellerId(order.getSellerId());
                    return conversations.save(created);
                });
        String safeDetail = detail == null || detail.isBlank() ? "请进入订单详情查看处理进度" : detail;
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setContent("[[ORDER_EVENT:" + event + ":" + order.getId() + "]]"
                + title + "\n" + safeDetail);
        message.setAutoReply(false);
        messages.save(message);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversations.save(conversation);
    }
}
