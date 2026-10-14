package com.kamron.software_testing.whiteboxtesting;

public class Grading {

    /**
     * Returns a grade based on the score:
     * - If the score is less than 0 or greater than 100, throws
     * IllegalArgumentException.
     * - For scores in [90, 100], returns "A".
     * - For scores in [80, 89], returns "B".
     * - For scores in [70, 79], returns "C".
     * - For scores in [60, 69], returns "D".
     * - For scores in [0, 59], returns "F".
     */
    public String grade(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Score must be between 0 and 100");
        }
        if (score >= 90) {
            return "A";
        } else if (score >= 80) {
            return "B";
        } else if (score >= 70) {
            return "C";
        } else if (score >= 60) {
            return "D";
        } else {
            return "F";
        }
    }
}
