package com.campus.trade.repository.location;

import com.campus.trade.entity.location.CampusPoi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampusPoiRepository extends JpaRepository<CampusPoi, Long> {
    List<CampusPoi> findByEnabledTrueOrderBySortAsc();
}
