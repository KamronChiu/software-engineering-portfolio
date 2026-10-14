package com.campus.trade.controller.product;

import com.campus.trade.common.ApiResponse;
import com.campus.trade.entity.product.Category;
import com.campus.trade.repository.product.CategoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品分类（T4.1）：GET /api/categories 返回启用的分类列表（含 children 树形结构），
 * 首页分类 Tab 与发布页分类下拉都从这里读取，不再写死。
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Category> all = categories.findByStatusOrderBySortAsc("ENABLED");
        Map<Long, List<Map<String, Object>>> byParent = new LinkedHashMap<>();
        all.stream().filter(c -> c.getParentId() != null).forEach(c ->
                byParent.computeIfAbsent(c.getParentId(), key -> new java.util.ArrayList<>()).add(categoryView(c)));
        List<Map<String, Object>> roots = all.stream()
                .filter(c -> c.getParentId() == null)
                .map(c -> {
                    Map<String, Object> node = categoryView(c);
                    node.put("children", byParent.getOrDefault(c.getId(), List.of()));
                    return node;
                })
                .toList();
        return ApiResponse.success(roots);
    }

    private Map<String, Object> categoryView(Category category) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", category.getId());
        row.put("name", category.getName());
        row.put("icon", category.getIcon());
        row.put("sort", category.getSort());
        return row;
    }
}
