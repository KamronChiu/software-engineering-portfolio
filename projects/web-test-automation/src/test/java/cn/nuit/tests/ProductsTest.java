package cn.nuit.tests;

import cn.nuit.pages.LoginPage;
import cn.nuit.pages.ProductsPage;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.extension.ExtendWith;
import cn.nuit.tests.TestListener;

/**
 * Tests for product sorting, selection, and cart badge count.
 */
@ExtendWith(TestListener.class)
@Feature("Products Module")
@DisplayName("Product Sorting & Add-to-Cart Test")
public class ProductsTest extends TestBase {

    @Test
    @Severity(SeverityLevel.NORMAL)
    @Description("Verify sorting by name and price, then add an item to the cart and check the badge count.")
    void testAllSortAndAdd() {
        // Login
        Allure.step("Log in as standard_user", () -> {
            LoginPage lp = new LoginPage(driver);
            lp.open();
            lp.login("standard_user", "secret_sauce");
        });

        // Prepare page object
        ProductsPage pp = new ProductsPage(driver);

        // Verify all sort options
        Map<String, Runnable> sorts = Map.of(
                "Name (A to Z)", () -> {
                    List<String> actual = pp.getAllNames();
                    List<String> expected = actual.stream().sorted().collect(Collectors.toList());
                    Assertions.assertEquals(expected, actual, "Names should be sorted A to Z");
                },
                "Name (Z to A)", () -> {
                    List<String> actual = pp.getAllNames();
                    List<String> expected = actual.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
                    Assertions.assertEquals(expected, actual, "Names should be sorted Z to A");
                },
                "Price (low to high)", () -> {
                    List<Double> actual = pp.getAllPrices();
                    List<Double> expected = actual.stream().sorted().collect(Collectors.toList());
                    Assertions.assertEquals(expected, actual, "Prices should be sorted low to high");
                },
                "Price (high to low)", () -> {
                    List<Double> actual = pp.getAllPrices();
                    List<Double> expected = actual.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
                    Assertions.assertEquals(expected, actual, "Prices should be sorted high to low");
                }
        );
        for (String option : sorts.keySet()) {
            Allure.step("Sorting and verifying: " + option, () -> {
                pp.sortBy(option);
                sorts.get(option).run();
            });
        }

        //  Add the first item to cart
        Allure.step("Add first product to cart", () -> {
            pp.addToCart(0);
            Assertions.assertEquals(1, pp.getCartCount(), "Cart badge should show 1 item");
        });

        // Navigate to cart and verify item count on cart page
        Allure.step("Navigate to cart and verify item count", () -> {
            pp.goToCart();
            int pageCount = new cn.nuit.pages.CartPage(driver).getItemCount();
            Assertions.assertEquals(1, pageCount, "Cart page should show 1 item");
        });
    }
}