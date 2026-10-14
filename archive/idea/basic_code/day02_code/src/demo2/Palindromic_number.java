package demo2;

import java.util.Scanner;

public class Palindromic_number {
    public static void main(String[] args) {
        // 核心思路：把数字倒过来跟原来的数字进行比较
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter a hundred digit: ");
        int x = sc.nextInt();
        // 获取每一位
        int ge = x%10;
        int shi = x/10%10;
        int bai = x/100%10;
        // 倒过来然后拼接
        int result = ge*100 + shi*10 + bai;
        System.out.println(result);
        if(x == result){
            System.out.println("Yes, its palindrome number");
        }else{
            System.out.println("No, its no a palindrome number");
        }
    }
}
