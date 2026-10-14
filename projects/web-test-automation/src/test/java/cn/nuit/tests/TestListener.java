package cn.nuit.tests;

import io.qameta.allure.Attachment;
import org.junit.jupiter.api.extension.*;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

public class TestListener implements TestWatcher, BeforeEachCallback, AfterEachCallback {
    private WebDriver driver;

    @Override
    public void beforeEach(ExtensionContext context) {
        this.driver = TestBase.driver;
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        if (driver != null && isSessionActive(driver)) {
            saveScreenshot(driver);
        }
    }

    @Attachment(value = "Failure Screenshot", type = "image/png")
    public byte[] saveScreenshot(WebDriver driver) {
        try {
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        } catch (Exception e) {
            System.out.println("Screenshot failed: " + e.getMessage());
            return new byte[0];
        }
    }

    private boolean isSessionActive(WebDriver driver) {
        try {
            return ((RemoteWebDriver) driver).getSessionId() != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
    }
}