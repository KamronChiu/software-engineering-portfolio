package com.kamron.software_testing.BasicSyntax;

public class throwsAndThrows {
    /*    throws：写在方法定义处，表示声明一个异常。告诉调用者，使用本方法可能会有哪些异常。
        throw：写在方法内，结束方法，手动抛出异常对象，交给调用者，方法下面的代码就不会再执行了*/
    public static void main(String[] args) {
        //需求：定义一个方法求数组的最大值
        int[] arr = null;
        int max = 0;
        try {
            max = getMax(arr);
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        } catch (ArithmeticException e) {
            System.out.println(e.getMessage());
        }

        System.out.println(max);

    }

    public static int getMax(int[] arr) throws NullPointerException, ArithmeticException {
        if (arr == null) {
            //手动创建一个异常对象，并把这个这个异常交给方法的调用者处理
            //此时方法就会结束，下面的代码就不会再执行
            throw new NullPointerException("arr is null");
        }

        System.out.println("can u see me?");
        int max = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (max < arr[i]) {
                max = arr[i];
            }
        }
        return max;
    }
}
