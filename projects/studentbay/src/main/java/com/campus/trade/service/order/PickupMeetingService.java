package com.campus.trade.service.order;

import com.campus.trade.common.BusinessException;
import com.campus.trade.dto.order.PickupMeetingProposalRequest;
import com.campus.trade.dto.order.PickupProposalDecisionRequest;
import com.campus.trade.entity.order.*;
import com.campus.trade.repository.order.OrderRepository;
import com.campus.trade.repository.order.PickupMeetingProposalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PickupMeetingService {
    private final OrderRepository orders;
    private final PickupMeetingProposalRepository proposals;

    public PickupMeetingService(OrderRepository orders, PickupMeetingProposalRepository proposals) {
        this.orders = orders;
        this.proposals = proposals;
    }

    @Transactional(readOnly = true)
    public List<PickupMeetingProposal> list(Long orderId, Long userId) {
        requireParticipant(orderId, userId);
        return proposals.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    @Transactional
    public PickupMeetingProposal propose(Long orderId, Long userId, PickupMeetingProposalRequest request) {
        TradeOrder order = requireParticipant(orderId, userId);
        if (!"PICKUP".equals(order.getShippingMethod())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "只有校园面交订单可以协商时间");
        }
        if (!List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID).contains(order.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "订单进入交付或售后阶段后不能修改面交安排");
        }
        if (!request.meetingEndAt().isAfter(request.meetingStartAt())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "结束时间需要晚于开始时间");
        }
        proposals.findByOrderIdAndStatus(orderId, PickupProposalStatus.PENDING).forEach(existing -> {
            existing.setStatus(PickupProposalStatus.WITHDRAWN);
            proposals.save(existing);
        });
        PickupMeetingProposal proposal = new PickupMeetingProposal();
        proposal.setOrderId(orderId);
        proposal.setProposerId(userId);
        proposal.setResponderId(order.getBuyerId().equals(userId) ? order.getSellerId() : order.getBuyerId());
        proposal.setMeetingStartAt(request.meetingStartAt());
        proposal.setMeetingEndAt(request.meetingEndAt());
        proposal.setLocation(request.location().trim());
        proposal.setStatus(PickupProposalStatus.PENDING);
        proposal.setConflictWarning(conflictWarning(order, proposal));
        return proposals.save(proposal);
    }

    @Transactional
    public PickupMeetingProposal respond(Long orderId, Long proposalId, Long userId,
                                          PickupProposalDecisionRequest request) {
        TradeOrder order = requireParticipant(orderId, userId);
        PickupMeetingProposal proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "面交协商记录不存在"));
        if (!proposal.getOrderId().equals(orderId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "面交协商记录与订单不匹配");
        }
        if (!proposal.getResponderId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有对方可以回应本次修改");
        }
        if (proposal.getStatus() != PickupProposalStatus.PENDING) {
            throw new BusinessException(HttpStatus.CONFLICT, "本次面交协商已处理");
        }
        if ("ACCEPT".equalsIgnoreCase(request.decision())) {
            proposal.setStatus(PickupProposalStatus.ACCEPTED);
            order.setPickupMeetingStartAt(proposal.getMeetingStartAt());
            order.setPickupMeetingEndAt(proposal.getMeetingEndAt());
            order.setPickupLocation(proposal.getLocation());
            order.setShippingAddress(proposal.getLocation());
            orders.save(order);
        } else if ("REJECT".equalsIgnoreCase(request.decision())) {
            proposal.setStatus(PickupProposalStatus.REJECTED);
        } else {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "回应必须为 ACCEPT 或 REJECT");
        }
        proposal.setRespondedAt(LocalDateTime.now());
        return proposals.save(proposal);
    }

    private String conflictWarning(TradeOrder current, PickupMeetingProposal proposal) {
        List<TradeOrder> participantOrders = new ArrayList<>();
        participantOrders.addAll(orders.findByBuyerIdOrderByCreatedAtDesc(current.getBuyerId()));
        participantOrders.addAll(orders.findBySellerIdOrderByCreatedAtDesc(current.getBuyerId()));
        participantOrders.addAll(orders.findByBuyerIdOrderByCreatedAtDesc(current.getSellerId()));
        participantOrders.addAll(orders.findBySellerIdOrderByCreatedAtDesc(current.getSellerId()));
        boolean conflict = participantOrders.stream().anyMatch(other -> !other.getId().equals(current.getId())
                && "PICKUP".equals(other.getShippingMethod())
                && List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID).contains(other.getStatus())
                && other.getPickupMeetingStartAt() != null && other.getPickupMeetingEndAt() != null
                && proposal.getMeetingStartAt().isBefore(other.getPickupMeetingEndAt())
                && proposal.getMeetingEndAt().isAfter(other.getPickupMeetingStartAt()));
        return conflict ? "时间冲突提醒：买家或卖家在该时段已有其他面交安排，请确认后再接受" : null;
    }

    private TradeOrder requireParticipant(Long orderId, Long userId) {
        TradeOrder order = orders.findById(orderId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "订单不存在"));
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无权协商该订单面交安排");
        }
        return order;
    }
}
