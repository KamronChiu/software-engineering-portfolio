package com.campus.trade.repository.reward;

import com.campus.trade.entity.reward.CoinLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoinLedgerRepository extends JpaRepository<CoinLedger, Long> {
    List<CoinLedger> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
