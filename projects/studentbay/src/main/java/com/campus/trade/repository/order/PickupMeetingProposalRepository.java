package com.campus.trade.repository.order;

import com.campus.trade.entity.order.PickupMeetingProposal;
import com.campus.trade.entity.order.PickupProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PickupMeetingProposalRepository extends JpaRepository<PickupMeetingProposal, Long> {
    List<PickupMeetingProposal> findByOrderIdOrderByCreatedAtDesc(Long orderId);
    List<PickupMeetingProposal> findByOrderIdAndStatus(Long orderId, PickupProposalStatus status);
}
