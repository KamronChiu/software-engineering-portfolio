package com.kamron.mythread.waitandnotify;

public class Cook extends Thread {
    @Override
    public void run() {
        while (true) {
            //同步代码块
            synchronized (Desk.lock) {
                if (Desk.count == 0) {
                    break;
                } else {
                    //先判断桌子上是否有面条
                    if (Desk.foodFlag == 1) {
                        //如果有就等待
                        try {
                            Desk.lock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        //如果没有就制作食物
                        System.out.println("厨师做了一碗面！");
                        //修改桌子上的食物标志
                        Desk.foodFlag = 1;
                        //叫醒消费者开吃
                        Desk.lock.notifyAll();
                    }
                }
            }
        }
    }
}
