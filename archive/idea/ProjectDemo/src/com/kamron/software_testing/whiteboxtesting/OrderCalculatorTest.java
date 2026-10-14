package com.kamron.software_testing.whiteboxtesting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class OrderCalculatorTest {

    @Test
    void testNegativeAmountThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> OrderCalculator.calculateFinalPrice(-1.0, 0, null));
    }

    @Test
    void testInvalidLevelThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> OrderCalculator.calculateFinalPrice(50.0, 5, ""));
    }

    @ParameterizedTest
    @CsvSource({
            // amount, level, coupon, expected, expectException
            "0.0, 0,    ,      0.0,  false",
            "50.0, 0,    ,     50.0,  false",
            "50.0, 1,    ,     47.5,  false",
            "100.0,1, SAVE10,  85.0,  false",
            "120.0,2, SAVE20,  88.0,  false",  // 120*0.90=108→108-20=88
            "80.0, 3, SAVE20,  63.0,  false",  // 80*0.85=68→<100 so -5, 68-5=63
            "40.0, 0, ABC,    35.0,  false",  // -5 fallback
            "5.0,  3, SAVE10,  0.0,  false"   // 5*0.85=4.25→4.25-10<0→0
    })
    void parameterizedCalculate(double amount,
                                int level,
                                String coupon,
                                double expected,
                                boolean expectException) {
        if (expectException) {
            assertThrows(IllegalArgumentException.class,
                    () -> OrderCalculator.calculateFinalPrice(amount, level, coupon));
        } else {
            double actual = OrderCalculator.calculateFinalPrice(amount, level, coupon);
            assertEquals(expected, actual, 1e-6);
        }
    }
}
