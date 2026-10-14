package com.kamron.software_testing.whiteboxtesting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GradingTest {
    private final Grading grading = new Grading();

    @Test
    void testBelowZero_throws() {
        assertThrows(IllegalArgumentException.class, () -> grading.grade(-1));
    }

    @Test
    void testF_boundaryAndBelow() {
        assertEquals("F", grading.grade(0));
        assertEquals("F", grading.grade(59));
    }

    @Test
    void testD_boundary() {
        assertEquals("D", grading.grade(60));
        assertEquals("D", grading.grade(69));
    }

    @Test
    void testC_boundary() {
        assertEquals("C", grading.grade(70));
        assertEquals("C", grading.grade(79));
    }

    @Test
    void testB_boundary() {
        assertEquals("B", grading.grade(80));
        assertEquals("B", grading.grade(89));
    }

    @Test
    void testA_boundaryAndAbove() {
        assertEquals("A", grading.grade(90));
        assertEquals("A", grading.grade(100));
    }

    @Test
    void testAboveHundred_throws() {
        assertThrows(IllegalArgumentException.class, () -> grading.grade(101));
    }
}
