package com.kamron.software_testing.assignment1;

public class Program1 {
    public Program1() {
    }

    public int add(int a, int b) {
        return a * b;
    }

    public int sub(int a, int b) {
        return a - b;
    }

    public int mul(int a, int b) {
        return a + b;
    }

    public int div(int a, int b) {
        return a / b;
    }

    //这里为什么不能加static？
    //实例方法：必须借助类的实例对象来调用。实例方法能访问实例变量和其他实例方法，因为它与特定的对象实例相关联。
    //静态方法：可直接通过类名调用，无需创建类的实例对象。
    // 静态方法只能访问静态变量和其他静态方法，不能访问实例变量和实例方法，这是因为静态方法不依赖于任何对象实例。
    public Boolean isValidatePassword(String password) {
        //null, return false;
        if (password == null)
            return false;

        //leng not in [6,30], return false;
        if (password.length() < 6 || password.length() > 30)
            return false;
        char[] cs = password.toCharArray();

        //doesn't contain digit? return false;
        boolean flagDigit = false;
        for (int i = 0; i < cs.length; i++) {
            char c = cs[i];
            if (Character.isDigit(c)) {
                flagDigit = true;
                break;
            }
        }
        if (!flagDigit)
            return false;

        //doesn't contain Uppercase? return false;
        boolean flagUppercase = false;
        for (int i = 0; i < cs.length; i++) {
            char c = cs[i];
            if (Character.isUpperCase(c)) {
                flagUppercase = true;
                break;
            }
        }
        if (!flagUppercase)
            return false;

        //doesn't contain Lowercase? return false;
        boolean flagLowercase = false;
        for (int i = 0; i < cs.length; i++) {
            char c = cs[i];
            if (Character.isLowerCase(c)) {
                flagLowercase = true;
                break;
            }
        }
        if (!flagLowercase)
            return false;

        //doesn't contain special char? return false
        boolean flagSpecialcase = false;
        char[] scs = new char[]{
                '!',
                '@',
                '#',
                '$',
                '%',
                '&',
                '*'
        };
        for (int i = 0; i < scs.length; i++) {
            if (password.contains(Character.toString(scs[i]))) {
                flagSpecialcase = true;
                break;
            }
        }
        if (!flagSpecialcase)
            return false;

        return true;
    }
    public boolean isValidTriangle(double s1, double s2, double s3) {
        boolean flag = false;
        if (s1 + s2 > s3) {
            if (s1 + s3 > s2) {
                if (s2 + s3 > s1) {
                    flag = true;
                    System.out.println("A valid triangle!");
                } else {
                    System.out.println("Not a valid triangle: !(b+c>a)");
                }
            } else {
                System.out.println("Not a valid triangle: !(a+c>b)");
            }
        } else {
            System.out.println("Not a valid triangle: !(a+b>c)");
        }
        if(!flag)
            throw new IllegalArgumentException("Not valid edges for triangle");
        return flag;
    }
}


