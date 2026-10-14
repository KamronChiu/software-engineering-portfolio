package com.campus.trade.repository.chat;

import com.campus.trade.entity.chat.AutoReplySetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AutoReplySettingRepository extends JpaRepository<AutoReplySetting, Long> {
    Optional<AutoReplySetting> findByUserId(Long userId);
}
