package com.kamron.software_testing.whiteboxtesting;/*
package com.kamron.software_testing.whiteboxtesting;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SumTest {

    @Test
    void testE1() {
        List<Integer> list = new ArrayList<>();
        assertEquals(0, sum(list));
        assertEquals(0, sumAbsolute(new ArrayList<>(list)));
    }

    @Test
    void testE2() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        assertEquals(6, sum(list));
        assertEquals(6, sumAbsolute(new ArrayList<>(list)));
    }

    @Test
    //wrong
    void testE3() {
        List<Integer> list = Arrays.asList(-1, -2, -3);
        assertEquals(-6, sum(list));
        assertEquals(6, sumAbsolute(new ArrayList<>(list)));
    }

    @Test
    void testE4() {
        List<Integer> list = Arrays.asList(-1, 0, 2);
        assertEquals(1, sum(list));
        assertEquals(3, sumAbsolute(new ArrayList<>(list)));
    }

    @Test
    void testE5() {
        List<Integer> list = Arrays.asList(0, 1, -1);
        assertEquals(0, sum(list));
        assertEquals(2, sumAbsolute(new ArrayList<>(list)));
    }

    @Test
    void testE6() {
        assertThrows(NullPointerException.class, () -> sum(null));
        assertThrows(NullPointerException.class, () -> sumAbsolute(null));
    }

    // Boundary: list length = 1
    @Test
    void testBoundary_listLengthOne() {
        List<Integer> list = Collections.singletonList(-100);
        assertEquals(-100, sum(list));
        assertEquals(100, sumAbsolute(new ArrayList<>(list)));
    }

    // Boundary: large numbers
    @Test
    void testBoundary_largeValues() {
        List<Integer> list = Arrays.asList(Integer.MAX_VALUE, 1);
        assertEquals((long)Integer.MAX_VALUE + 1, sumLong(list));
    }

    // Boundary: Integer.MIN_VALUE
    @Test
    void testBoundary_minValue() {
        List<Integer> list = Arrays.asList(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, sum(list));
        // abs(Integer.MIN_VALUE) == Integer.MIN_VALUE (due to overflow!)
        assertEquals(Integer.MIN_VALUE, sumAbsolute(new ArrayList<>(list)));
    }

    // White-box: Original list should not be mutated
    @Test
    void testWhiteBox_listMutationBug() {
        List<Integer> list = new ArrayList<>(Arrays.asList(-1, -2, -3));
        List<Integer> original = new ArrayList<>(list); // deep copy
        sumAbsolute(list);  // mutates the list!
        assertNotEquals(original, list, "sumAbsolute() should not mutate the original list");
    }

    // White-box: Basic correctness of sum
    @Test
    void testWhiteBox_sumCorrectness() {
        List<Integer> list = Arrays.asList(10, 20, -30);
        assertEquals(0, sum(list));
    }

    // Helper for handling large value overflows
    long sumLong(List<Integer> list) {
        long sum = 0;
        for (int x : list) sum += x;
        return sum;
    }

    // Original functions
    public static int sumAbsolute(List<Integer> list) {
        for (int i = 0; i < list.size(); ++i) {
            list.set(i, Math.abs(list.get(i)));  // MUTATES list
        }
        return sum(list);
    }

    public static int sum(List<Integer> list) {
        int sum = 0;
        for (int x : list) {
            sum += x;
        }
        return sum;
    }
}*/


import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SumTest {
//The main method will not be detected
    @Test
    public void testSumPositiveNumbers() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        int expected = 6;
        int result = Calculator.sum(list);
        assertEquals(expected, result);
    }

    @Test
    public void testSumNegativeNumbers() {
        List<Integer> list = Arrays.asList(-1, -2, -3);
        int expected = -6;
        int result = Calculator.sum(list);
        assertEquals(expected, result);
    }

    @Test
    public void testSumEmptyList() {
        List<Integer> list = Arrays.asList();
        int expected = 0;
        int result = Calculator.sum(list);
        assertEquals(expected, result);
    }

    @Test
    public void testSumAbsolutePositiveNumbers() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        int expected = 6;
        int result = Calculator.sumAbsolute(list);
        assertEquals(expected, result);
    }

    @Test
    public void testSumAbsoluteNegativeNumbers() {
        List<Integer> list = Arrays.asList(-1, -2, -3);
        int expected = 6;
        int result = Calculator.sumAbsolute(list);
        assertEquals(expected, result);
    }

    @Test
    public void testSumAbsoluteMixedNumbers() {
        List<Integer> list = Arrays.asList(-1, 2, -3);
        int expected = 6;
        int result = Calculator.sumAbsolute(list);
        assertEquals(expected, result);
    }
}
