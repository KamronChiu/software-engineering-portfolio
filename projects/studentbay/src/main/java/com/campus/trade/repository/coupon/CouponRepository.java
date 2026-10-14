package com.campus.trade.repository.coupon;

import com.campus.trade.entity.coupon.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    List<Coupon> findByEnabledTrueOrderByMinimumAmountAsc();
    List<Coupon> findByEnabledTrueAndOwnerUserIdIsNullOrderByMinimumAmountAsc();
    List<Coupon> findByEnabledTrueAndOwnerUserIdAndUsedFalseOrderByCreatedAtDesc(Long ownerUserId);
    Optional<Coupon> findByCode(String code);
}
