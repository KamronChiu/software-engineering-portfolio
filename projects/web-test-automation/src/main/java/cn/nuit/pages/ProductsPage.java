package cn.nuit.pages;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * sorting, adding to cart, and navigation actions.
 */
public class ProductsPage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    private static final By SORT_DROPDOWN = By.cssSelector(".product_sort_container");
    private static final By PRICE_LABELS = By.cssSelector(".inventory_item_price");
    private static final By NAME_LABELS = By.cssSelector(".inventory_item_name");
    private static final By ADD_BUTTONS = By.cssSelector(".inventory_item button");
    private static final By CART_BADGE = By.className("shopping_cart_badge");
    private static final By CART_LINK = By.className("shopping_cart_link");

    public ProductsPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @Step("Get all product prices")
    public List<Double> getAllPrices() {
        return wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(PRICE_LABELS)).stream().map(e -> e.getText().replace("$", "")).map(Double::parseDouble).collect(Collectors.toList());
    }

    @Step("Get all product names")
    public List<String> getAllNames() {
        return wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(NAME_LABELS)).stream().map(WebElement::getText).collect(Collectors.toList());
    }

    @Step("Sort products by \"{option}\"")
    public void sortBy(String option) {
        WebElement dropdown = wait.until(ExpectedConditions.elementToBeClickable(SORT_DROPDOWN));
        new Select(dropdown).selectByVisibleText(option);
    }

    @Step("Add product at index {index} to cart")
    public void addToCart(int index) {
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(ADD_BUTTONS, index - 1));
        List<WebElement> buttons = driver.findElements(ADD_BUTTONS);
        WebElement button = buttons.get(index);
        wait.until(ExpectedConditions.elementToBeClickable(button)).click();
    }

    @Step("Get cart item count (badge)")
    public int getCartCount() {
        String text = wait.until(ExpectedConditions.visibilityOfElementLocated(CART_BADGE)).getText();
        return Integer.parseInt(text);
    }

    @Step("Navigate to cart page")
    public void goToCart() {
        wait.until(ExpectedConditions.elementToBeClickable(CART_LINK)).click();
    }
}