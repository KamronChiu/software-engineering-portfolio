package cn.nuit.pages;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

// login page.
public class LoginPage {
    private final WebDriver driver;
    private final WebDriverWait wait;
    private static final By USERNAME_INPUT = By.id("user-name");
    private static final By PASSWORD_INPUT = By.id("password");
    private static final By LOGIN_BUTTON = By.id("login-button");
    private static final By ERROR_MESSAGE = By.cssSelector("[data-test='error']");

    public LoginPage(WebDriver driver) { // other page also need login
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @Step("Open the login page")
    public LoginPage open() {
        driver.get("https://www.saucedemo.com/");
        wait.until(ExpectedConditions.visibilityOfElementLocated(USERNAME_INPUT));
        return this;
    }

    @Step("Enter username: {username}")
    public LoginPage enterUsername(String username) {
        WebElement input = wait.until(ExpectedConditions.elementToBeClickable(USERNAME_INPUT));
        input.clear();
        input.sendKeys(username);
        return this;
    }

    @Step("Enter password: {password}")
    public LoginPage enterPassword(String password) {
        WebElement input = wait.until(ExpectedConditions.elementToBeClickable(PASSWORD_INPUT));
        input.clear();
        input.sendKeys(password);
        return this;
    }

    @Step("Click the login button")
    public LoginPage clickLogin() {
        wait.until(ExpectedConditions.elementToBeClickable(LOGIN_BUTTON)).click();
        return this;
    }

    @Step("Login with username: {username} and password: {password}")
    public LoginPage login(String username, String password) {
        return this
                .enterUsername(username)
                .enterPassword(password)
                .clickLogin();
    }

    @Step("Get error message after login failure")
    public String getErrorMessage() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(ERROR_MESSAGE)).getText();
    }
}