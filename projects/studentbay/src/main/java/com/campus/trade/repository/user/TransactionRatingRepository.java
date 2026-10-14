package com.campus.trade.repository.user;

import com.campus.trade.entity.user.TransactionRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransactionRatingRepository extends JpaRepository<TransactionRating, Long> {
    boolean existsByOrderIdAndRaterId(Long orderId, Long raterId);
    List<TransactionRating> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId);

    @Query("select avg(r.score) from TransactionRating r where r.targetUserId = :userId")
    Double averageScoreFor(@Param("userId") Long userId);

    long countByTargetUserId(Long targetUserId);
}
