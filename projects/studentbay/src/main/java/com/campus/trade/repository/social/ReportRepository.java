package com.campus.trade.repository.social;

import com.campus.trade.entity.social.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findAllByOrderByCreatedAtDesc();

    long countByProductIdIn(Collection<Long> productIds);
}
