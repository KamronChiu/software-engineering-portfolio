package com.kamron.mythread.testcase1;

import java.util.Random;

public class MyThread extends Thread {
    //共享数据
    static double money = 100;
    static int count = 3;
    //最小的中奖金额
    static final double MIN = 0.01;

    public void run() {
        for (int i = 0; i < count; i++) {
            synchronized (MyThread.class) {
                if (count == 0) {
                    System.out.println(getName() + "没有抢到红包");
                } else {
                    //定义一个变量，显示中奖金额
                    double price = 0;
                    if (count == 1) {
                        //  无需随机，剩余的钱就是中奖金额
                        price = money;
                    } else {
                        //表示第一二次随机
                        Random r = new Random();
                        //第一个红包最大值的范围
                        double bound = money - (count - 1) * MIN;
                        price = r.nextDouble(bound);
                        if (price < MIN) {
                            price = MIN;
                        }
                    }
                    money -= price;
                    count--;
                    System.out.println(getName() + "抢到了" + price + "元");
                }
            }
        }
    }
}
