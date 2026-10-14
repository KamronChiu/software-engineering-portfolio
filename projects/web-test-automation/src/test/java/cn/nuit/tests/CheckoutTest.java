package cn.nuit.tests;

import cn.nuit.pages.CartPage;
import cn.nuit.pages.CheckoutPage;
import cn.nuit.pages.LoginPage;
import cn.nuit.pages.ProductsPage;
import io.qameta.allure.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.api.extension.ExtendWith;
import cn.nuit.tests.TestListener;

/**
 * Reads multiple (firstName, lastName, postalCode) combinations from CSV.
 */
@ExtendWith(TestListener.class)
@Epic("SauceDemo Automated Testing")
@Feature("Checkout Module")
@DisplayName("Checkout Process DDT")
public class CheckoutTest extends TestBase {

    @Severity(SeverityLevel.CRITICAL)
    @Description("Complete checkout flow with data-driven user info from CSV")
    @ParameterizedTest(name = "Checkout with {0} {1}, postalCode={2}")
    @CsvFileSource(resources = "/checkout-data.csv", numLinesToSkip = 1)
    void testCompleteCheckoutDDT(String firstName, String lastName, String postalCode) {
        Allure.step("Open login page and log in as standard_user", () -> {
            new LoginPage(driver).open().login("standard_user", "secret_sauce");
        });

        // Add a product and go to cart
        Allure.step("Add product index 0 to cart and navigate to cart page", () -> {
            ProductsPage pp = new ProductsPage(driver);
            pp.addToCart(0);
            pp.goToCart();
        });

        // Click Checkout
        Allure.step("Click the Checkout button", () -> {
            new CartPage(driver).clickCheckout();
        });

        // Verify checkout info page title
        Allure.step("Verify checkout information page title", () -> {
            CheckoutPage co = new CheckoutPage(driver);
            String title = co.getPageTitle();
            Assertions.assertEquals("Checkout: Your Information", title, "Expected to be on the information entry page");
        });

        // Enter user info and continue
        Allure.step(
                String.format("Enter user info: %s %s, %s", firstName, lastName, postalCode),
                () -> {
                    new CheckoutPage(driver).enterUserInfo(firstName, lastName, postalCode);
                }
        );

        // Finish checkout
        Allure.step("Click Finish to complete the order", () -> {
            new CheckoutPage(driver).clickFinish();
        });

        // Verify completion message
        Allure.step("Verify order completion message", () -> {
            String message = new CheckoutPage(driver).getCompleteText();
            Assertions.assertEquals("Thank you for your order!", message, "Expected the order completion message");
        });
    }
}