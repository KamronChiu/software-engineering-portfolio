import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

import static org.junit.jupiter.api.Assertions.*;

class DemoTest {
    WebDriver driver;
    @BeforeAll
    static void setUpBeforeClass() throws Exception {

    }
    @AfterAll
    static void tearDownAfterClass() throws Exception {

    }
    @BeforeEach
    void setUp() throws Exception {
        driver = new ChromeDriver();
    }
    @AfterEach
    void tearDown() throws Exception {
        driver.quit();
    }
    @Test
    void test() throws InterruptedException {
        driver.get("https://www.selenium.dev/selenium/web/web-form.html");
        driver.getTitle();
        WebElement textBox =
                driver.findElement(By.name("my-text"));
        WebElement submitButton =
                driver.findElement(By.cssSelector("button"));
        textBox.sendKeys("Selenium");
        submitButton.click();
        WebElement message =
                driver.findElement(By.id("message"));
        message.getText();
    }
}