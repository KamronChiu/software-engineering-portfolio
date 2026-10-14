import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class WebTest {

    private WebDriver driver;
    private WebDriverWait wait;
    private Actions actions;

    @BeforeAll
    void setupAll() {
    }

    @BeforeEach
    void setupEach() {
        driver = new ChromeDriver();
        driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(5));
        wait = new WebDriverWait(driver, Duration.ofSeconds(12));
        actions = new Actions(driver);
    }

    @AfterEach
    void teardownEach() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    @DisplayName("1. Text Box")
    void testInputValidation() {
        // open the page
        driver.get("http://127.0.0.1:3000/");
        WebElement nameField = driver.findElement(By.id("userName"));
        // click Name，validate this element
        nameField.click();
        WebElement active = driver.switchTo().activeElement();
        assertEquals(nameField, active, "After clicking, it should focus on the Name text box");
        String validName = "Kamron";
        nameField.clear();
        nameField.sendKeys(validName);
        String nameValue = nameField.getAttribute("value");
        assertEquals(validName, nameValue, "The Name text box should accept legal letters and Spaces");

        // Email  box
        WebElement emailField = driver.findElement(By.id("userEmail"));
        emailField.click();
        active = driver.switchTo().activeElement();
        assertEquals(emailField, active, "After clicking, it should focus on the Email text box");
        String validEmail = "zzt@abc.com";
        emailField.clear();
        emailField.sendKeys(validEmail);
        String emailValue = emailField.getAttribute("value");
        assertEquals(validEmail, emailValue, "The Email text box should accept email boxes in legal formats");

//        //  Illegal input test
//        nameField.click();
//        String badName = "Kamron@123";
//        nameField.clear();
//        nameField.sendKeys(badName);
//        nameValue = nameField.getAttribute("value");
//        // Only letters and Spaces are allowed
//        assertTrue(nameValue.matches("^[A-Za-z ]+$"),
//                String.format("The "Name" field only allows letters and Spaces, but the actual value is：'%s'", nameValue));
    }

    @Test
    @DisplayName("2. Check Box")
    void testCheckBoxSelection() throws InterruptedException {
        driver.get("http://127.0.0.1:3000/");
        WebElement musicCheckbox = driver.findElement(By.id("hobby1"));
        boolean flag = musicCheckbox.isSelected();
        if (flag) {
            musicCheckbox.click();
        }
        WebElement sportCheckbox = driver.findElement(By.id("hobby2"));
        boolean flag2 = sportCheckbox.isSelected();
        if (!flag2) {
            sportCheckbox.click();
        }
        WebElement readingCheckbox = driver.findElement(By.id("hobby3"));
        boolean flag3 = readingCheckbox.isSelected();
        if (!flag3) {
            readingCheckbox.click();
        }
        WebElement travelCheckbox = driver.findElement(By.id("hobby4"));
        boolean flag4 = travelCheckbox.isSelected();
        if (flag4) {
            travelCheckbox.click();
        }
        assertFalse(musicCheckbox.isSelected());
        assertTrue(sportCheckbox.isSelected());
        assertTrue(readingCheckbox.isSelected());
        assertFalse(travelCheckbox.isSelected());
    }

    @Test
    @DisplayName("3. Radio Button")
    void testRadioButtonSelection() throws InterruptedException {
        driver.get("http://127.0.0.1:3000/");
        WebElement maleRadio = driver.findElement(By.id("gender1"));   // value="male"
        WebElement femaleRadio = driver.findElement(By.id("gender2")); // value="female"
        // Verify the initial state: None are selected
        assertFalse(maleRadio.isSelected());
        assertFalse(femaleRadio.isSelected());
        maleRadio.click();
        assertTrue(maleRadio.isSelected());
        assertFalse(femaleRadio.isSelected());
        femaleRadio.click();
        assertTrue(femaleRadio.isSelected());
        assertFalse(maleRadio.isSelected());
    }

    @Test
    @DisplayName("4. Web Tables")
    void testWebTableRecordAddition() throws InterruptedException {
        driver.get("http://127.0.0.1:3000/");
        String testName = "Ziting Zhao";
        String testEmail = "zzt@qq.com";
        WebElement nameField = driver.findElement(By.id("userName"));
        WebElement emailField = driver.findElement(By.id("userEmail"));
        nameField.clear();
        nameField.sendKeys(testName);
        emailField.clear();
        emailField.sendKeys(testEmail);
        // choose Gender = Female
        Thread.sleep(500);
        WebElement femaleRadio = driver.findElement(By.id("gender2"));
        if (!femaleRadio.isSelected()) {
            femaleRadio.click();
        }
        Thread.sleep(500);
        //choose the hobby
        List<WebElement> hobbyCheckboxes = driver.findElements(By.name("hobby"));
        for (WebElement cb : hobbyCheckboxes) {
            if (cb.isSelected()) {
                cb.click();
            }
        }
        WebElement sportCheckbox = driver.findElement(By.id("hobby2"));
        WebElement travelCheckbox = driver.findElement(By.id("hobby4"));
        if (!sportCheckbox.isSelected()) {
            sportCheckbox.click();
        }
        if (!travelCheckbox.isSelected()) {
            travelCheckbox.click();
        }
        assertFalse(driver.findElement(By.id("hobby1")).isSelected());
        assertTrue(sportCheckbox.isSelected());
        assertFalse(driver.findElement(By.id("hobby3")).isSelected());
        assertTrue(travelCheckbox.isSelected());
//        //  Choose the band that 80s likes
        Select bandSelect = new Select(driver.findElement(By.id("EightiesFave")));
        List<WebElement> options = bandSelect.getOptions();
        List<String> expectedList = new ArrayList<String>();
        expectedList.add("The Cure");
        expectedList.add("Cocteau Twins");
        expectedList.add("Everything But the Girl");
        expectedList.add("Depeche Mode");
        int i = 0;
        for (WebElement option : options) {
            System.out.println(option.getText());
            assertEquals(expectedList.get(i), option.getText());
            i++;
        }
        bandSelect.selectByIndex(3);
        String val = bandSelect.getFirstSelectedOption().getText();
        assertEquals(expectedList.get(3), val);
        //  fill Address
        WebElement addr = driver.findElement(By.id("permanentAddress"));
        addr.clear();
        addr.sendKeys("neusoft, Foshan City");
        // Submit
        WebElement submitBtn = driver.findElement(By.cssSelector("button[name='submit']"));
        submitBtn.click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//table[@id='data-table']//td[text()='" + testName + "']")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//table[@id='data-table']//td[text()='" + testEmail + "']")));
        WebElement nameCell = driver.findElement(By.xpath("//table[@id='data-table']//td[text()='" + testName + "']"));
        WebElement emailCell = driver.findElement(By.xpath("//table[@id='data-table']//td[text()='" + testEmail + "']"));
        assertEquals(testName, nameCell.getText(), "The table should contain what has just been added Name");
        assertEquals(testEmail, emailCell.getText(), "The table should contain what has just been added Email");
    }

    @Test
    @DisplayName("5. Buttons Interactions")
    void testCombinedButtonsInteractions() throws InterruptedException {
        driver.get("http://127.0.0.1:3000/ctxmenu.html");
        Actions actions = new Actions(driver);
        WebElement box = driver.findElement(By.id("myDiv"));
        actions.contextClick(box).perform();
        assertTrue(driver.findElement(By.id("customMenu")).isDisplayed());
        driver.findElement(By.cssSelector("#customMenu > ul > li:nth-child(3)")).click();
        assertEquals("Reading", driver.findElement(By.id("result")).getText());

        actions.doubleClick(box).perform();
        assertTrue(driver.findElement(By.id("dbclick")).isDisplayed());
        driver.findElement(By.id("gender2")).click();
        assertEquals("female", driver.findElement(By.id("result")).getText());
    }

    @DisplayName("6. Navigation Links")
    @Test
    void testNavigationLinks() {
        String baseUrl = "http://127.0.0.1:3000/";
        driver.get(baseUrl);
        // Home
        WebElement homeLink = driver.findElement(By.linkText("Home"));
        homeLink.click();
        wait.until(ExpectedConditions.urlToBe(baseUrl));
        assertEquals(baseUrl, driver.getCurrentUrl());
        //  Drag and Drop
        WebElement ddLink = driver.findElement(By.linkText("Drag and Drop"));
        ddLink.click();
        wait.until(ExpectedConditions.urlContains("dd.html"));
        assertTrue(driver.getCurrentUrl().endsWith("dd.html"));
        // Context Menu
        WebElement ctxLink = driver.findElement(By.linkText("Context Menu"));
        ctxLink.click();
        wait.until(ExpectedConditions.urlContains("ctxmenu.html"));
        assertTrue(driver.getCurrentUrl().endsWith("ctxmenu.html"));
        //  Modal Window
        WebElement modalLink = driver.findElement(By.linkText("Modal Window"));
        modalLink.click();
        wait.until(ExpectedConditions.urlContains("modal.html"));
        assertTrue(driver.getCurrentUrl().endsWith("modal.html"));
        // Tooltip
        WebElement tooltipLink = driver.findElement(By.linkText("Tooltip"));
        tooltipLink.click();
        wait.until(ExpectedConditions.urlContains("tooltip.html"));
        assertTrue(driver.getCurrentUrl().endsWith("tooltip.html"));
        // Dynamic
        WebElement dynamicLink = driver.findElement(By.linkText("Dynamic"));
        dynamicLink.click();
        wait.until(ExpectedConditions.urlContains("dynamic.html"));
        assertTrue(driver.getCurrentUrl().endsWith("dynamic.html"));
    }

    @Test
    @DisplayName("7. Dynamic Properties")
    void testDynamicProperties() {
        driver.get("http://127.0.0.1:3000/dynamic.html");
        WebElement timeoutInput = driver.findElement(By.id("timeout"));
        timeoutInput.clear();
        timeoutInput.sendKeys("12000");
        WebElement adderBtn = driver.findElement(By.id("adder"));
        adderBtn.click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("box0")));
        WebElement box0 = driver.findElement(By.id("box0"));
        assertTrue(box0.isDisplayed());
        WebElement revealBtn = driver.findElement(By.id("reveal"));
        revealBtn.click();
        WebElement revealedInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("revealed")));
        assertTrue(revealedInput.isDisplayed());
    }

    @Test
    @DisplayName("8. Tooltips Verification: CSS Tooltip + Title Tooltip")
    void testTooltipsVerification() {
        driver.get("http://127.0.0.1:3000/tooltip.html");
        WebElement cssTooltipContainer = driver.findElement(By.cssSelector(".tooltip"));
        actions.moveToElement(cssTooltipContainer).perform();
        WebElement tooltipText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".tooltip .tooltiptext")));
        String actualCssTooltip = tooltipText.getText();
        assertEquals("This is tooltip information", actualCssTooltip);
        WebElement titleTooltipElement = driver.findElement(By.xpath("//div[@title='This is title tooltip']"));
        actions.moveToElement(titleTooltipElement).perform();
        String actualTitle = titleTooltipElement.getAttribute("title");
        assertEquals("This is title tooltip", actualTitle);
    }

    @DisplayName("9. Drag and Drop Action")
    @Test
    void testDragAndDropMultipleImages() throws InterruptedException {
        driver.get("http://127.0.0.1:3000/dd.html");
        WebElement drag1 = driver.findElement(By.id("drag1"));
        WebElement drag2 = driver.findElement(By.id("drag2"));
        WebElement drag3 = driver.findElement(By.id("drag3"));
        WebElement droppable = driver.findElement(By.id("div1"));
        actions.clickAndHold(drag1)
                .pause(Duration.ofMillis(500))
                .moveToElement(droppable)
                .pause(Duration.ofMillis(500))
                .release()
                .perform();
        wait.until(ExpectedConditions.textToBe(By.id("result"), "dropped;"));
        actions.clickAndHold(drag2)
                .pause(Duration.ofMillis(500))
                .moveToElement(droppable)
                .pause(Duration.ofMillis(500))
                .release()
                .perform();
        wait.until(ExpectedConditions.textToBe(By.id("result"), "dropped;dropped;"));
        actions.clickAndHold(drag3)
                .pause(Duration.ofMillis(500))
                .moveToElement(droppable)
                .pause(Duration.ofMillis(500))
                .release()
                .perform();
        wait.until(ExpectedConditions.textToBe(By.id("result"), "dropped;dropped;dropped;"));
        String resultText = driver.findElement(By.id("result")).getText();
        assertEquals("dropped;dropped;dropped;", resultText, "after dropping three image，Result should be “dropped;dropped;dropped;”");
    }

    @DisplayName("10. Alert Dialog Handling")
    @Test
    void testAlertConfirmHandling() throws InterruptedException {
        driver.get("http://127.0.0.1:3000/modal.html");
        driver.findElement(By.id("alertDiag")).click();
        Alert alertBox = driver.switchTo().alert();
        alertBox.accept();
        driver.findElement(By.id("confirmDiag")).click();
        Alert confirmBox1 = driver.switchTo().alert();
        confirmBox1.accept();  //  OK
        WebElement result = driver.findElement(By.id("confirmResult"));
        wait.until(ExpectedConditions.textToBePresentInElement(result, "You pressed OK!"));
        assertEquals("You pressed OK!", result.getText());
    }
}