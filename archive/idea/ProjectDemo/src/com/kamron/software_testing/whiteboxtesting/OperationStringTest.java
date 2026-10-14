package com.kamron.software_testing.whiteboxtesting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OperationStringTest {

    // --------- Tests for operationString(String) ---------
    @Test
    @DisplayName("operationString(null) throws NPE")
    void operationString_null_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
                OperationString.operationString(null));
    }

    @Test
    @DisplayName("operationString(\"\") returns \"c\"")
    void operationString_empty_returnsC() {
        assertEquals("c", OperationString.operationString(""));
    }

    @Test
    @DisplayName("operationString(\"x\") returns \"xxc\"")
    void operationString_singleChar() {
        assertEquals("xxc", OperationString.operationString("x"));
    }

    @Test
    @DisplayName("operationString(\"ab\") returns \"ababc\"")
    void operationString_multiChar() {
        assertEquals("ababc", OperationString.operationString("ab"));
    }

    @Test
    @DisplayName("operationString(\"你好\") returns \"你好你好c\"")
    void operationString_unicode() {
        assertEquals("你好你好c", OperationString.operationString("你好"));
    }

    // --- Tests for operationStringBuilder(StringBuilder) ---

    @Test
    @DisplayName("operationStringBuilder(null) throws NPE")
    void operationStringBuilder_null_throwsNPE() {
        assertThrows(NullPointerException.class, () ->
                OperationString.operationStringBuilder(null));
    }

    @Test
    @DisplayName("operationStringBuilder(\"\") returns \"c\"")
    void operationStringBuilder_empty_returnsC() {
        StringBuilder input = new StringBuilder("");
        StringBuilder result = OperationString.operationStringBuilder(input);
        assertEquals("c", result.toString());
    }

    @Test
    @DisplayName("operationStringBuilder(\"x\") returns \"xxc\"")
    void operationStringBuilder_singleChar() {
        StringBuilder input = new StringBuilder("x");
        StringBuilder result = OperationString.operationStringBuilder(input);
        assertEquals("xxc", result.toString());
    }

    @Test
    @DisplayName("operationStringBuilder(\"ab\") returns \"ababc\"")
    void operationStringBuilder_multiChar() {
        StringBuilder input = new StringBuilder("ab");
        StringBuilder result = OperationString.operationStringBuilder(input);
        assertEquals("ababc", result.toString());
    }

    @Test
    @DisplayName("operationStringBuilder(\"你好\") returns \"你好你好c\"")
    void operationStringBuilder_unicode() {
        StringBuilder input = new StringBuilder("你好");
        StringBuilder result = OperationString.operationStringBuilder(input);
        assertEquals("你好你好c", result.toString());
    }
}