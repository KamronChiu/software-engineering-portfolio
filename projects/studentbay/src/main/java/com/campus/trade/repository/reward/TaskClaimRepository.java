package com.campus.trade.repository.reward;

import com.campus.trade.entity.reward.TaskClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface TaskClaimRepository extends JpaRepository<TaskClaim, Long> {
    boolean existsByUserIdAndTaskCode(Long userId, String taskCode);
    boolean existsByUserIdAndTaskCodeAndClaimDate(Long userId, String taskCode, LocalDate claimDate);
}
