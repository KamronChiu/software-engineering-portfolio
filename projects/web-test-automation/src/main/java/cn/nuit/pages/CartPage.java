package cn.nuit.pages;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.util.List;

/**
 * Provides methods to inspect and manipulate cart items.
 */
public class CartPage {
    private final WebDriver driver;
    private final WebDriverWait wait;
    private static final By CART_ITEMS = By.cssSelector(".cart_item");
    private static final By REMOVE_BUTTONS = By.cssSelector("button[data-test^='remove-']");
    private static final By CHECKOUT_BUTTON = By.cssSelector("[data-test='checkout']");

    public CartPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    /**
     * Return the number of items currently listed in the cart.
     */
    @Step("Get cart item count")
    public int getItemCount() {
        List<WebElement> items = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(CART_ITEMS));
        return items.size();
    }

    /**
     * Remove the item at the given zero-based index, waiting for the DOM to update.
     * @param index position of the item to remove
     */
    @Step("Remove item at index {0}")
    public void removeItem(int index) {
        List<WebElement> buttons = wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(REMOVE_BUTTONS, index - 1));
        List<WebElement> itemsBefore = driver.findElements(CART_ITEMS);
        int originalCount = itemsBefore.size();
        if (buttons.size() <= index) {
            throw new RuntimeException("No remove button at index " + index + " (only " + buttons.size() + " present)");
        }

        WebElement button = buttons.get(index);
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", button);
        wait.until(ExpectedConditions.elementToBeClickable(button)).click();
        wait.until(driver -> driver.findElements(CART_ITEMS).size() == originalCount - 1);
    }
    /**
     * Click the Checkout button to proceed to the checkout page.
     */
    @Step("Click Checkout button")
    public void clickCheckout() {
        WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(CHECKOUT_BUTTON));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", btn);
        btn.click();
    }
}