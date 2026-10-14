package com.kamron.software_testing.whiteboxtesting;

import org.junit.jupiter.api.Test;
import static com.kamron.software_testing.whiteboxtesting.HailstoneSequence.hailstoneSequence;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;

class HailstoneTest {
    @Test
    void testNEqualsOne() {
        assertEquals(Arrays.asList(1), hailstoneSequence(1));
    }

    @Test
    void testNEqualsTwo() {
        assertEquals(Arrays.asList(2,1), hailstoneSequence(2));
    }

    @Test
    void testNEqualsThree() {
        List<Integer> expected = Arrays.asList(3,10,5,16,8,4,2,1);
        assertEquals(expected, hailstoneSequence(3));
    }

    @Test
    void testNEqualsSix() {
        List<Integer> expected = Arrays.asList(6,3,10,5,16,8,4,2,1);
        assertEquals(expected, hailstoneSequence(6));
    }

    @Test
    void testZeroThrows() {
        assertThrows(IllegalArgumentException.class, () -> hailstoneSequence(0));
    }

    @Test
    void testNegativeThrows() {
        assertThrows(IllegalArgumentException.class, () -> hailstoneSequence(-5));
    }
}
