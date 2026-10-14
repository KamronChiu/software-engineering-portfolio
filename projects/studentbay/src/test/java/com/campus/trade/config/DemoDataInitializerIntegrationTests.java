package com.campus.trade.config;

import com.campus.trade.entity.product.Product;
import com.campus.trade.repository.product.ProductRepository;
import com.campus.trade.repository.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DemoDataInitializerIntegrationTests {

    private static final Set<String> CATEGORIES = Set.of(
            "教材资料", "数码产品", "骑行装备", "宿舍生活", "服饰美妆", "其他"
    );

    @Autowired
    private DemoDataInitializer initializer;

    @Autowired
    private ProductRepository products;

    @Autowired
    private UserRepository users;

    @Test
    void catalogueShouldContainFiveDistinctProductsInEveryCategory() {
        Map<String, List<Product>> byCategory = products.findAll().stream()
                .collect(Collectors.groupingBy(Product::getCategory));

        assertThat(byCategory.keySet()).containsAll(CATEGORIES);
        CATEGORIES.forEach(category -> {
            List<Product> samples = byCategory.get(category);
            assertThat(samples)
                    .as("%s 应至少有 5 个演示商品", category)
                    .hasSizeGreaterThanOrEqualTo(5);
            assertThat(samples.stream().map(Product::getTitle))
                    .as("%s 的商品名称应互不重复", category)
                    .doesNotHaveDuplicates();
        });
    }

    @Test
    void catalogueShouldContainFiveDistinctChildFriendlyToyProducts() {
        List<String> toyTitles = products.findAll().stream()
                .filter(product -> "其他".equals(product.getCategory()))
                .filter(product -> {
                    String text = product.getTitle() + " " + product.getDescription();
                    return List.of("玩具", "积木", "拼图", "玩偶", "遥控车", "磁力片")
                            .stream().anyMatch(text::contains);
                })
                .map(Product::getTitle)
                .toList();

        assertThat(toyTitles).hasSizeGreaterThanOrEqualTo(5).doesNotHaveDuplicates();
    }

    @Test
    void everyPresentationProductShouldHaveItsOwnNonBlankPhoto() {
        Set<Long> demoSellerIds = Set.of(
                users.findByEmailIgnoreCase("chen@campus.edu").orElseThrow().getId(),
                users.findByEmailIgnoreCase("wang@campus.edu").orElseThrow().getId());
        List<Product> demoProducts = products.findAll().stream()
                .filter(product -> demoSellerIds.contains(product.getSellerId()))
                .toList();

        assertThat(demoProducts).hasSizeGreaterThanOrEqualTo(40);
        assertThat(demoProducts).extracting(Product::getImageUrl)
                .allSatisfy(url -> assertThat(url).isNotBlank())
                .doesNotHaveDuplicates();
    }

    @Test
    void rerunningInitializerShouldBeIdempotent() throws Exception {
        long before = products.count();

        initializer.run(new DefaultApplicationArguments(new String[0]));

        assertThat(products.count()).isEqualTo(before);
    }
}
