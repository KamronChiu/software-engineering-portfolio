package com.kamron.software_testing.whiteboxtesting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    private final AuthService auth = new AuthService();

    @Test
    void testLoginCorrect() {
        assertTrue(auth.login("admin", "123456"));
    }

    @Test
    void testLoginWrongUsername() {
        assertFalse(auth.login("user", "123456"));
    }

    @Test
    void testLoginWrongPassword() {
        assertFalse(auth.login("admin", "abcdef"));
    }

    @Test
    void testLoginBothWrong() {
        assertFalse(auth.login("user", "pwd"));
    }

    @Test
    void testLoginEmptyStrings() {
        assertFalse(auth.login("", ""));
    }

    @Test
    void testLoginNullUsername() {
        assertFalse(auth.login(null, "123456"));
    }

    @Test
    void testLoginNullPassword() {
        assertFalse(auth.login("admin", null));
    }
}