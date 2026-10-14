package demo2;

import java.util.Scanner;

public class arithmetic_operator {
    public static void main(String[] args) {
        System.out.println("abc" + 3.7); //"abc3.7"
        System.out.println("abc" + true); //"abctrue"
        System.out.println(1 + 2 + "abc" + 2 +1); //"3abc21"
        //char类型对应ASCLL码，用 '   '
        //只要有字符串参与就是拼接过程，其余有运算

        int x = 10;
        //后++：先用后加
        //先把x变量中的值拿出来用，赋值给y，然后再进行自增。
        //赋值给y的值是自增前的
        int y = x++;  // x= 11    y = 10
        //先++：先加再用
        //先把x进行自增，然后把自增后的结果赋值给左边的变量
        //先把x自增，变成12，然后再把自增之后的12赋值给z
        int z = ++x;
        System.out.println(x);
        System.out.println(y);
        System.out.println(z);

        //判断两个输入的数
        Scanner sc = new Scanner(System.in);
        System.out.println("请输入一个整数");
        int number1 = sc.nextInt();
        System.out.println("请输入第二个整数");
        int number2 = sc.nextInt();
        //可以短路逻辑运算符去链接三个判断
        boolean result = (number1 +number2) % 6 == 0 || number1 == 6 || number2 == 6;
        System.out.println(result);
    }
}
