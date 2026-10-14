package com.kamron.software_testing.whiteboxtesting;

//This program computes and prints the Hailstone (Collatz) sequence
    public class Hailstone {
    public static void main(String[] args) {
        //  Set the initial value.
        int n = 3;

        // Generate the sequence as a comma‑separated string.
        String sequence = generateHailstoneSequence(n);

        //  Print the result.
        System.out.println(sequence);
    }
    /**
     * Generates the Hailstone sequence starting at n and returns it
     * as a comma‑separated string, ending with 1
     * @param n the starting integer (must be > 0)
     * @return the sequence, e.g. "3,10,5,16,8,4,2,1"
     * @throws IllegalArgumentException if n <= 0
     */
    public static String generateHailstoneSequence(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Starting value must be positive");
        }

        StringBuilder sb = new StringBuilder();

        // Continue until we reach 1.
        while (true) {
            sb.append(n);
            if (n == 1) {
                // Once we've appended 1, the sequence ends.
                break;
            }
            sb.append(',');

            // Compute the next term.
            if (n % 2 == 0) {
                // Even
                n = n / 2;
            } else {
                // Odd
                n = 3 * n + 1;
            }
        }
        return sb.toString();
    }
}

