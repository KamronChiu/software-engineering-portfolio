package com.kamron.software_testing.whiteboxtesting;

import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * This program reads a positive integer n from standard input,
 * generates the Hailstone (Collatz) sequence, and prints it
 * as a comma‑separated list.
 * Example:
 * Input:  17
 * Output: 17,52,26,13,40,20,10,5,16,8,4,2,1
 */
public class HailstoneSequence {
    public static void main(String[] args) {
        var scanner = new Scanner(System.in);
        System.out.print("Enter a positive integer n: ");
        int n = scanner.nextInt();
        scanner.close();
        try {
            List<Integer> sequence = hailstoneSequence(n);
            System.out.println(sequence);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * Computes the Hailstone (Collatz) sequence starting from n.
     * Input:  an integer n > 0
     * Return: a List<Integer> containing the sequence from n down to 1.
     *
     * @param n the starting positive integer (must be > 0)
     * @return the Collatz sequence as a List<Integer>
     * @throws IllegalArgumentException if n <= 0
     */
    public static List<Integer> hailstoneSequence(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be > 0");
        }

        var seq = new ArrayList<Integer>();
        while (true) {
            seq.add(n);
            if (n == 1) {
                // Terminate when we reach 1
                break;
            }
            // Apply Collatz rule
            n = (n % 2 == 0) ? n / 2 : 3 * n + 1;
        }
        return seq;
    }
}