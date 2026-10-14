package demo2;

import java.util.Scanner;

public class NestedIf {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("enter a number");
        int score = sc.nextInt();
        if(score>=90){
            System.out.println("grade A");
        }
        else{
            if (score >= 80) {
                System.out.println("grade B");
            }
            else{
                if (score >= 70) {
                    System.out.println("grade C");
                }
                else{
                    if (score >= 60) {
                        System.out.println("grade D");
                    }
                    else{
                        System.out.println("grade E");
                    }
                }
            }
        }

    }
}
