package demo2;

import java.sql.SQLOutput;
import java.util.Scanner;

public class ForDemo {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("请输入第一个数");
        int start = sc.nextInt();
        System.out.println("请输入另一个数字");
        int end = sc.nextInt();
        int count =0;
        for(int i = start ;i <= end; i++){
//            System.out.println("helloworld");
            if (i % 3 ==0 && i % 5 == 0){
                count ++;
            }
        }
        System.out.println(count);
    }
}
