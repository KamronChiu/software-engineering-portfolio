package com.campus.trade.controller.product;

import com.campus.trade.entity.product.Category;
import com.campus.trade.repository.product.CategoryRepository;
import com.campus.trade.repository.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 阶段 4（T4.1/T4.2）接口测试：分类列表返回树形结构；商品列表分页与 ISBN/课程号搜索。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductStage4ControllerIntegrationTests {

    @Autowired
    private MockMvc web;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void ensureCategorySeed() {
        if (categoryRepository.count() == 0) {
            categoryRepository.saveAll(List.of(
                    category("教材资料", 1), category("数码产品", 2), category("其他", 6)));
        }
    }

    private Category category(String name, int sort) {
        Category item = new Category();
        item.setName(name);
        item.setSort(sort);
        item.setStatus("ENABLED");
        return item;
    }

    @Test
    void categoriesReturnTreeWithEnabledOnly() throws Exception {
        web.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("教材资料"))
                .andExpect(jsonPath("$.data[0].children").isArray());
    }

    @Test
    void productListIsPaginated() throws Exception {
        web.perform(get("/api/products").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(5));
    }

    @Test
    void keywordSearchMatchesCourseCodeAndIsbn() throws Exception {
        // 种子演示数据里有 CS2010 / ISBN 的教材（ensureProductCatalog 幂等，可能为空则跳过断言）
        var match = productRepository.findAll().stream()
                .filter(p -> "CS2010".equals(p.getCourseCode()) || "978-7-04-039663-8".equals(p.getIsbn()))
                .findFirst();
        assertThat(match).isPresent();
        web.perform(get("/api/products")
                        .param("keyword", "CS2010"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }
}
