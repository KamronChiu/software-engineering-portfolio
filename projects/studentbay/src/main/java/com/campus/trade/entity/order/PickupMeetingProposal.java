package com.campus.trade.entity.order;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pickup_meeting_proposal", indexes = {
        @Index(name = "idx_pickup_proposal_order", columnList = "order_id"),
        @Index(name = "idx_pickup_proposal_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class PickupMeetingProposal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "proposer_id", nullable = false)
    private Long proposerId;

    @Column(name = "responder_id", nullable = false)
    private Long responderId;

    @Column(name = "meeting_start_at", nullable = false)
    private LocalDateTime meetingStartAt;

    @Column(name = "meeting_end_at", nullable = false)
    private LocalDateTime meetingEndAt;

    @Column(nullable = false, length = 160)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PickupProposalStatus status;

    @Column(name = "conflict_warning", length = 500)
    private String conflictWarning;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
