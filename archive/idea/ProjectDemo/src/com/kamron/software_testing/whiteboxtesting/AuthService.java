package com.kamron.software_testing.whiteboxtesting;

public class AuthService {
    /**
     * Returns true if the username is "admin" and the password is "123456";
     otherwise, returns false.
     */
    public boolean login(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        return "admin".equals(username) && "123456".equals(password);
    }
}
