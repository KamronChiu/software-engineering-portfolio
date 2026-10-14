package cn.nuit.tests;

import cn.nuit.pages.LoginPage;
import com.opencsv.CSVReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.api.Assertions;
import java.io.FileReader;
import java.util.stream.Stream;

@ExtendWith(TestListener.class)
@DisplayName("Login Functionality - Data Driven Tests")
public class LoginTest extends TestBase {

    static Stream<Arguments> loginData() throws Exception {
        try (CSVReader reader = new CSVReader(new FileReader("src/test/resources/login-data.csv"))) {
            return reader.readAll().stream().skip(1).map(arr -> Arguments.of(arr[0], arr[1], arr[2]));
        }
    }

    @ParameterizedTest(name = "Login as {0}, expect [{2}]")
    @MethodSource("loginData")
    void loginDDT(String username, String password, String expectedOutcome) {
        LoginPage loginPage = new LoginPage(driver);
        loginPage.open().login(username, password);
// successful login
        if ("success".equalsIgnoreCase(expectedOutcome)) {
            Assertions.assertTrue(driver.getCurrentUrl().contains("inventory"), "Expected successful login to redirect to inventory page.");
        } else {
            // locked_out_user cannot log in
            String actualError = loginPage.getErrorMessage().toLowerCase();
            String expectedError = expectedOutcome.toLowerCase().replaceFirst("error:\\s*", "").trim();
            Assertions.assertTrue(actualError.contains(expectedError),
                    () -> String.format("Expected error to contain [%s], but got [%s]", expectedError, actualError));
        }
    }
}