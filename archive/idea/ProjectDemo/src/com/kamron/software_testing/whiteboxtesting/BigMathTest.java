package com.kamron.software_testing.whiteboxtesting;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;

class BigMathTest {
    private final BigMath bm = new BigMath();

    // Boundary constants
    private static final BigInteger MIN_MINUS_ONE =
            BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE);
    private static final BigInteger MIN =
            BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger MIN_PLUS_ONE =
            BigInteger.valueOf(Long.MIN_VALUE).add(BigInteger.ONE);
    private static final BigInteger MAX_MINUS_ONE =
            BigInteger.valueOf(Long.MAX_VALUE).subtract(BigInteger.ONE);
    private static final BigInteger MAX =
            BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger MAX_PLUS_ONE =
            BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

    @Test
    void testZeroTimesAnything() {
        assertEquals(BigInteger.ZERO, bm.multiply(BigInteger.ZERO, MAX));
        assertEquals(BigInteger.ZERO, bm.multiply(BigInteger.ZERO, MIN));
    }

    @Test
    void testPositiveTimesPositive() {
        assertEquals(BigInteger.valueOf(6),
                bm.multiply(BigInteger.valueOf(2), BigInteger.valueOf(3)));
    }

    @Test
    void testNegativeTimesPositive() {
        assertEquals(BigInteger.valueOf(-6),
                bm.multiply(BigInteger.valueOf(-2), BigInteger.valueOf(3)));
    }

    @Test
    void testNegativeTimesNegative() {
        assertEquals(BigInteger.valueOf(6),
                bm.multiply(BigInteger.valueOf(-2), BigInteger.valueOf(-3)));
    }

    @Test
    void testBoundary_MIN_MINUS_ONE() {
        // (MIN−1) * 1 == MIN−1
        assertEquals(MIN_MINUS_ONE, bm.multiply(MIN_MINUS_ONE, BigInteger.ONE));
    }

    @Test
    void testBoundary_MIN_PLUS_ONE_times_MAX_PLUS_ONE() {
        BigInteger expected = MIN_PLUS_ONE.multiply(MAX_PLUS_ONE);
        assertEquals(expected, bm.multiply(MIN_PLUS_ONE, MAX_PLUS_ONE));
    }

    @Test
    void testBoundary_MAX_times_MAX() {
        BigInteger expected = MAX.multiply(MAX);
        assertEquals(expected, bm.multiply(MAX, MAX));
    }
}
