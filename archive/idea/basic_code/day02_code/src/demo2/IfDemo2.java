package demo2;

import java.util.Scanner;

public class IfDemo2 {
    public static void main(String[] args) {
        System.out.print("enter a number ");
        Scanner sc = new Scanner(System.in);
        int num = sc.nextInt();
        if (num % 2 == 0) {
            System.out.println("HiEven");
        }

        if (num % 5 == 0) {
            System.out.println("HiFive");
        }
    }
}
