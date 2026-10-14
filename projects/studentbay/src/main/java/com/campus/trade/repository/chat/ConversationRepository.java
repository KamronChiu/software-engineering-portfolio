package com.campus.trade.repository.chat;

import com.campus.trade.entity.chat.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByProductIdAndBuyerId(Long productId, Long buyerId);
    List<Conversation> findByBuyerIdOrderByUpdatedAtDesc(Long buyerId);
    List<Conversation> findBySellerIdOrderByUpdatedAtDesc(Long sellerId);
}
