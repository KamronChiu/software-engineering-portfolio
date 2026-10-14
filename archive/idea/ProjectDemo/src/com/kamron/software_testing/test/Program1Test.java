package com.kamron.software_testing.test;

import com.kamron.software_testing.assignment1.Program1;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class Program1Test {
    int a = 10, b = 5, expectedResult = 0;

    public void check(int acturalResult, int expectedResult) {
        System.out.println("Check result");
        if (acturalResult == expectedResult) {
            System.out.println("Pass");
        } else {
            System.out.println("Fail");
            System.out.println("Expected: " + expectedResult);
            System.out.println("Actual: " + acturalResult);
        }
        System.out.println("-----------------------------------------------------------");
    }

    @Test
    void add() {
        System.out.println("Arrange: a = " + a + ", b = " + b + ", expected = " + expectedResult);
        System.out.println("Act: call method program1.add(a,b)");
        Program1 p1 = new Program1();
        int result = p1.add(a, b);
        check(result, expectedResult);
    }

    @Test
    void sub() {
        System.out.println("Arrange: a = " + a + ", b = " + b + ", expected = " + expectedResult);
        System.out.println("Act: call method program1.sub(a,b)");
        Program1 p2 = new Program1();
        int result = p2.sub(a, b);
        check(result, expectedResult);
    }

    @Test
    void mul() {
        System.out.println("Arrange: a = " + a + ", b = " + b + ", expected = " + expectedResult);
        System.out.println("Act: call method program1.mul(a,b)");
        Program1 p3 = new Program1();
        int result = p3.mul(a, b);
        check(result, expectedResult);
    }

    @Test
    void div() {
        int actualResult = 0;
        String expectedMessage = "Division by zero";
        String message = "";
        b = 0;
        Scanner scan = null;
        System.out.println("Case testDivide:  " + a + ", b = " + b + ", expected = " + expectedResult);
        Program1 p4 = new Program1();
        try {
            scan = new Scanner(new File("program1.txt"));
            actualResult = p4.div(a, b);
        } catch (IOException ioExp) {
            System.out.println(ioExp);
        } catch (ArithmeticException Aexp) {
            message = Aexp.getMessage();
            System.out.println(message);
        } catch (Exception exp) {
            message = exp.getMessage();
            System.out.println(message);
        }
        assertEquals(expectedResult, actualResult);
    }

    @Disabled
    @Test
        //非空，>=6 covers
    void testIsValidPassword() {
        String password = "Abclong2";
        Program1 prom = new Program1();
        boolean actualResult = prom.isValidatePassword(password);
        Assertions.assertTrue(actualResult);
    }

    @Test
    void testIsValidPassword2() {
        String password = "Abclong2";
        Program1 prom = new Program1();
        boolean actualResult = prom.isValidatePassword(password);
        assertFalse(actualResult);
    }

    @Test
    void testIsValidPassword3() {
        String password = null;
        Program1 prom = new Program1();
        boolean actualResult = prom.isValidatePassword(password);
        assertFalse(actualResult);
    }

    @Test
    void testIsValidTriangle1() {
        double s1 = 3, s2 = 4, s3 = 5;
        Program1 prom = new Program1();
        boolean actualResult = prom.isValidTriangle(s1, s2, s3);
        Assertions.assertTrue(actualResult);
    }

    @Test
    void testIsValidTriangle2() {
        double s1 = 8, s2 = 2, s3 = 3;
        Program1 prom = new Program1();
        assertThrows(IllegalArgumentException.class, () -> {
            prom.isValidTriangle(s1, s2, s3);
        });
    }

    @Test
    void testIsValidTriangle3() {
        double s1 = 7, s2 = 5, s3 = 1;
        Program1 prom = new Program1();
        Exception exp = assertThrows(IllegalArgumentException.class, () -> {
            prom.isValidTriangle(s1, s2, s3);
        });
        assertEquals("",exp.getMessage());
    }

    @Test
    void testIsValidTriangle4() {
        double s1 = 1, s2 = 1, s3 = 8;
        Program1 prom = new Program1();
        Exception exp = assertThrows(IllegalArgumentException.class, () -> {
            prom.isValidTriangle(s1, s2, s3);
        });
        assertEquals("",exp.getMessage());
    }

    @BeforeAll
    //@BeforeAll：在所有测试方法执行前调用一次，适用于全局数据初始化。
    // @AfterAll：在所有测试方法执行后调用一次，用于资源释放或全局清理
    //不同test相同数据
    static void setUpBeforeClass() throws Exception {
        System.out.println("setUpBeforeClass");
    }

    @AfterAll
    //释放所有资源
    static void tearDownAfterClass() throws Exception {
        System.out.println("tearDownAfterClass");
    }

    @BeforeEach
        //不同的test不同的数据就用这个
    void setUp(TestInfo ti) {
        System.out.println(ti);
        System.out.println("setup");
        if (ti.getDisplayName().contains("Add")) {
            expectedResult = 15;
        } else if (ti.getDisplayName().contains("Subtract")) {
            expectedResult = 5;
        } else if (ti.getDisplayName().contains("Multiply")) {
            expectedResult = 50;
        } else if (ti.getDisplayName().contains("Divide")) {
            expectedResult = 25;
        }
        else {
            expectedResult = 2;
        }
        System.out.println("setUp:  expectedResult = " + expectedResult);
    }

    @AfterEach
        //恢复每一个
    void tearDown() {
        System.out.println("tearDown");
    }
}