package com.kamron.software_testing.whiteboxtesting;

/*public class StringUtil {
    *//**
     * Returns the reversed string of the input string s.
     *//*
    public String reverse(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }
}*/
public class StringUtil {
    /**
     * correctly handling surrogate pairs (e.g. emojis).
     * Unicode code points in reverse order
     */
    public String reverse(String s) {
        if (s == null) {
            return null;
        }
        // Extract all code points from s into an int[]
        int[] codePoints = s.codePoints().toArray();
        // Build a new String from them in reverse order
        StringBuilder sb = new StringBuilder(codePoints.length);
        for (int i = codePoints.length - 1; i >= 0; i--) {
            sb.appendCodePoint(codePoints[i]);
        }
        return sb.toString();
    }
}
