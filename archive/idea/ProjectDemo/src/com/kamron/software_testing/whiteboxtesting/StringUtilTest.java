package com.kamron.software_testing.whiteboxtesting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StringUtilTest {
    private final StringUtil util = new StringUtil();

    @Test
    void testReverseNull() {
        assertNull(util.reverse(null));
    }

    @Test
    void testReverseEmpty() {
        assertEquals("", util.reverse(""));
    }

    @Test
    void testReverseSingleChar() {
        assertEquals("A", util.reverse("A"));
    }

    @Test
    void testReverseNormal() {
        assertEquals("normaK", util.reverse("Kamron"));
    }

    @Test
    void testReverseWithSpaces() {
        assertEquals("c b a", util.reverse("a b c"));
    }

    @Test
    void testReverseSpecialChars() {
        String input = "Hi! 我是Kamron😄";
        String expected = "😄normaK是我 !iH";
        assertEquals(expected, util.reverse(input));
    }
}