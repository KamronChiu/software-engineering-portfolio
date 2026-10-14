package com.kamron.software_testing.whiteboxtesting;
import java.math.BigInteger;

public class BigMath {
    /*
     * Returns the exact product of a and b.
     * Supports values outside long range (e.g. Long.MIN_VALUE−1, Long.MAX_VALUE+1).
     */
    public BigInteger multiply(BigInteger a, BigInteger b) {
        return a.multiply(b);
    }
}
