package com.kamron.software_testing.whiteboxtesting;

public class WhiteBox {
    public static void main(String[] args) {

    }

    public int case1(int a, int b, int x) {
        if ((a > 1) && (b == 0)) {
            x = x / a;
        }
        if ((a == 2) || (x > 1)) {
            x = x + 1;
        }
        return x;
    }

    public int doWork(int x, int y, int z) {
        int k = 0, j = 0;
        if ((x > 3) && (z < 10)) {
            k = x * y - 1;
            j = 2 * k;
        }//block1
        if ((x == 4) || (y > 5)) {
            j = x * y + 10;
        }//block2
        j = j % 3;//block3
        return k;
    }
}
