package cn.nuit.tests;

import cn.nuit.pages.CartPage;
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
import org.junit.jupiter.api.extension.ExtendWith;
import cn.nuit.tests.TestListener;

/**
 * Tests for the cart functionality:
 * 1. Adding items to cart and verifying the badge and cart page count.
 * 2. Test the function of removing. Removing an item from cart and verifying the updated count.
 */
@ExtendWith(TestListener.class)
@Feature("Cart Module")
@DisplayName("Cart Add & Remove Functionality")
public class CartTest extends TestBase {

    @Test
    @Severity(SeverityLevel.NORMAL)
    @Description("Add two items to the cart, verify badge and page count; then remove one and verify again.")
    void testCartAddAndRemove() {
        // Log in as a standard user
        Allure.step("Log in as standard_user", () -> {
            new LoginPage(driver).open().login("standard_user", "secret_sauce");
        });

        // Add two items to the cart
        Allure.step("Add product indices 0 and 1 to cart", () -> {
            ProductsPage pp = new ProductsPage(driver);
            pp.addToCart(0);
            pp.addToCart(1);
            int badgeCount = pp.getCartCount();
            Assertions.assertEquals(2, badgeCount, "Cart badge should show 2 items");
        });

        // Navigate to cart page and verify item count there
        Allure.step("Navigate to cart page and verify 2 items listed", () -> {
            ProductsPage pp = new ProductsPage(driver);
            pp.goToCart();
            CartPage cp = new CartPage(driver);
            int countInCart = cp.getItemCount();
            Assertions.assertEquals(2, countInCart, "Cart page should list 2 items");
        });

        //  Remove the first item and verify the remaining count
        Allure.step("Remove the first item and verify only 1 item remains", () -> {
            CartPage cp = new CartPage(driver);
            cp.removeItem(0);
            int remaining = cp.getItemCount();
            Assertions.assertEquals(1, remaining, "Cart page should list 1 item after removal");
        });
    }
}