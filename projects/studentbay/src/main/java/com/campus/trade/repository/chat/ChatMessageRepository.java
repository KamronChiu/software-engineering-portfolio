package com.campus.trade.repository.chat;

import com.campus.trade.entity.chat.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
    long countByConversationIdInAndSenderIdNotAndReadAtIsNull(List<Long> conversationIds, Long senderId);
    long countBySenderIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long senderId, LocalDateTime start, LocalDateTime end);
}
