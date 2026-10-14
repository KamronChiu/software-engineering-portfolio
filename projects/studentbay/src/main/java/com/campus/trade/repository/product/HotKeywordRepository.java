package com.campus.trade.repository.product;

import com.campus.trade.entity.product.HotKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HotKeywordRepository extends JpaRepository<HotKeyword, Long> {

    Optional<HotKeyword> findByKeyword(String keyword);

    List<HotKeyword> findTop10ByOrderByCountDesc();
}
