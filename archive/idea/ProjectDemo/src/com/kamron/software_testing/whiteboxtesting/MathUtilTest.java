package com.kamron.software_testing.whiteboxtesting;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MathUtilTest {
    private final MathUtil mu = new MathUtil();

    @Test
    void testNegative() {
        assertEquals(10, mu.absolute(-10));
    }

    @Test
    void testZero() {
        assertEquals(0, mu.absolute(0));
    }

    @Test
    void testPositive() {
        assertEquals(25, mu.absolute(25));
    }

    @Test
    void testMaxValue() {
        assertEquals(Integer.MAX_VALUE, mu.absolute(Integer.MAX_VALUE));
    }

    @Test
    void testMinValue_overflow() {
        // -MIN_VALUE overflows and remains MIN_VALUE
        assertEquals(Integer.MIN_VALUE, mu.absolute(Integer.MIN_VALUE));
    }
}
