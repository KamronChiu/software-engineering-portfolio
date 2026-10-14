package com.campus.trade.repository.reward;

import com.campus.trade.entity.reward.DailyGameProgress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyGameProgressRepository extends JpaRepository<DailyGameProgress, Long> {
    Optional<DailyGameProgress> findByUserIdAndGameDate(Long userId, LocalDate gameDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select game from DailyGameProgress game where game.userId = :userId and game.gameDate = :gameDate")
    Optional<DailyGameProgress> findForUpdate(@Param("userId") Long userId, @Param("gameDate") LocalDate gameDate);
}
