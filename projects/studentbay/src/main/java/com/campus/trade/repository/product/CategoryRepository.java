package com.campus.trade.repository.product;

import com.campus.trade.entity.product.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByStatusOrderBySortAsc(String status);

    List<Category> findByParentIdIsNullAndStatusOrderBySortAsc(String status);
}
