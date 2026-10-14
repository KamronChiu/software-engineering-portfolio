package com.campus.trade.repository.activity;

import com.campus.trade.entity.activity.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {

    List<UserActivity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
