package cn.nuit.pages;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

// checkout pages.
public class CheckoutPage {
    private final WebDriver driver;
    private final WebDriverWait wait;
    private static final By TITLE = By.cssSelector("[data-test='title']");
    private static final By FIRST_NAME = By.cssSelector("[data-test='firstName']");
    private static final By LAST_NAME = By.cssSelector("[data-test='lastName']");
    private static final By POSTAL = By.cssSelector("[data-test='postalCode']");
    private static final By CONTINUE = By.cssSelector("[data-test='continue']");
    private static final By FINISH = By.cssSelector("[data-test='finish']");
    private static final By COMPLETE = By.cssSelector(".complete-header");

    public CheckoutPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    /**
     * Wait for the page title to appear and return its text.
     */
    @Step("Verify checkout page title")
    public String getPageTitle() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(TITLE)).getText();
    }

    /**
     * Fill in first name, last name, postal code and click Continue.
     */
    @Step("Enter user info: {0}, {1}, {2} and continue")
    public CheckoutPage enterUserInfo(String first, String last, String postal) {
        WebElement fn = wait.until(ExpectedConditions.elementToBeClickable(FIRST_NAME));
        fn.clear();
        fn.sendKeys(first);
        WebElement ln = driver.findElement(LAST_NAME);
        ln.clear();
        ln.sendKeys(last);
        WebElement pc = driver.findElement(POSTAL);
        pc.clear();
        pc.sendKeys(postal);
        driver.findElement(CONTINUE).click();
        return this;
    }

    /**
     * Click the Finish button to complete the order.
     */
    @Step("Click Finish to complete checkout")
    public CheckoutPage clickFinish() {
        wait.until(ExpectedConditions.elementToBeClickable(FINISH)).click();
        return this;
    }

    /**
     * Wait for and return the final confirmation message.
     */
    @Step("Get order completion message")
    public String getCompleteText() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(COMPLETE)).getText();
    }
}