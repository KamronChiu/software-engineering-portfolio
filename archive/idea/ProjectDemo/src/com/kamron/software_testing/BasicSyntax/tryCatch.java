package com.kamron.software_testing.BasicSyntax;

public class tryCatch {
    public static void main(String[] args) {
        //快捷键： command + option  +t

        /*
         * public String getMessage()                 返回此throwable的详细信息字符串
         * public String toString()                         返回此可抛出的简短描述
         * public void printStackTrace()             把异常的错误信息输入到控制台
         */

        //1.如果try没有遇到问题，怎么执行？
        //会把try里面的所有代码都执行，不会执行catch的代码
/*        int[] arr = {1,2,3,4,5,6};
        try{
            System.out.println(arr[0]);
        }catch (ArrayIndexOutOfBoundsException e){
            System.out.println("no such element");
        }
        System.out.println("can u see me?");*/

        //2.如果try中获得多个问题，怎么执行？(在try里可能有多个语句会出现异常，写多个catch只是为了能捕获出他的其中一个异常，但多个语句异常也只能捕获到第一个异常
        // 会多写几个catch与其对应
        // 细节：如果我们要捕获多个异常且存在父子关系的话，父类一定要写在最下面
        int[] arr = {1, 2, 3, 4, 5, 6};
        try {
            System.out.println(arr[10]);
            System.out.println(2 / 0);
            String a = null;
            System.out.println(a.equals("abc"));
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("no such element");
        } catch (ArithmeticException e) {
            System.out.println("divide by zero is not allowed.");
        } catch (NullPointerException e) {
            System.out.println("pointer is null");
        } catch (Exception e) {
            System.out.println("Exception caught in tryCatch");
        }

        System.out.println("can u see me?");
    }
}
