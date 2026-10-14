package com.kamron.software_testing.whiteboxtesting;
import java.util.ArrayList;
import java.util.List;

public class Calculator {
    /**
     * Returns the sum of a and b.
     */
/*    public int add(int a, int b) {
        return a + b;
    }*/
/*    public static int sumAbsolute(List<Integer> list) {
        // let's reuse sum(), because DRY, so first we take absolute values
        for (int i = 0; i < list.size(); ++i) {
            list.set(i, Math.abs(list.get(i)));
        }
        return sum(list);
    }
    *//**
     * @return the sum of the numbers in the list
     *//*
    public static int sum(List<Integer> list) {
        int sum = 0;
        for (int x : list) {
            sum += x;
        }
        return sum;
    }*/

    public static int sumAbsolute(List<Integer> list) {
        if (list == null) {
            throw new NullPointerException("List cannot be null");
        }
        // Copy so we don't mutate the original
        List<Integer> copy = new ArrayList<>(list.size());
        for (Integer x : list) {
            copy.add(Math.abs(x));
        }
        return sum(copy);
    }

    public static int sum(List<Integer> list) {
        if (list == null) {
            throw new NullPointerException("List cannot be null");
        }
        int total = 0;
        for (int x : list) {
            total += x;
        }
        return total;
    }

    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>();
        list.add(-1);
        list.add(-2);
        list.add(-3);
        int result_Absolute = sumAbsolute(list);
        int result = sum(list);
        System.out.println("the result of absolute value is:\n" +result_Absolute+"\n"+"the result is:\n"+result);

    }
}


