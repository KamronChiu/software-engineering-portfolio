package com.kamron.software_testing.whiteboxtesting;

import com.kamron.software_testing.whiteboxtesting.WhiteBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class WhiteBoxTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() throws Exception {
    }

    @ParameterizedTest
    //group1
    //@CsvSource({"2,0,2", "1,1,1"})
    @CsvSource({
            "2,0,2,2",
            "1,1,2,3",
            "1,0,1,1",
            "2,1,1,2"})
    void testCase(int a, int b, int x, int expectedResult) {
        WhiteBox wb = new WhiteBox();
        int actualValue = wb.case1(a, b, x);
        assertEquals(expectedResult, actualValue);
    }

    @ParameterizedTest
    @CsvSource({})
    void testDoWork(int x, int y, int z, int result) {
        WhiteBox wb = new WhiteBox();
        int actualResult = wb.doWork(x, y, z);
        assertEquals(result, actualResult);
    }

    @Test
    void main() {
    }

    @Test
        //statement coverage
/*    void case1() {
        int a = 2, b= 1,x = 4;
        int expectedResult = 5;
        WhiteBox wb = new WhiteBox();
        int actualValue = wb.case1(a,b,x);
        assertEquals(expectedResult, actualValue);
    }*/
        //decision coverage
    void case1() {
        int a = 3, b = 0, x = -3;
        int expectedResult = -1;
        WhiteBox wb = new WhiteBox();
        int actualValue = wb.case1(a, b, x);
        assertEquals(expectedResult, actualValue);
    }
}