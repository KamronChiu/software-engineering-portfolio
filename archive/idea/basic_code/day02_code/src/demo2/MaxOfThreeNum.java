package demo2;

import java.util.Scanner;

public class MaxOfThreeNum {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("enter fist num ");
        int a = sc.nextInt();
        System.out.print("enter second num ");
        int b = sc.nextInt();
        System.out.print("enter third num ");
        int c = sc.nextInt();
        if(a>b&&b>c){
            System.out.println(a);
        }
        else if(b>c){
            System.out.println(b);

        }
        else{
            System.out.println(c);
        }

        }

    }

