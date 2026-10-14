package demo2;

import java.util.Scanner;

public class IfDemo1 {
    public static void main(String[ ] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("请输入酒量");  // println和print的区别是换行
        int wine = sc.nextInt();
        if (wine > 2){
            System.out.println("小伙子，不错哦！！");

        }

    }
}
