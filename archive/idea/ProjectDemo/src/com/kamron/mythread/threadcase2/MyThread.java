package com.kamron.mythread.threadcase2;

public class MyThread extends Thread {
    //static是所有创建的对象共享的，如果没有static，那么每一次创建一个新的对象的时候都会创建一个新的值
    static int ticket = 0;   //0-99
    //锁对象一定要是唯一的
//    static Object lock = new Object();


    @Override
    public void run() {
        while (true) {
            //上锁
            //同步代码块
            synchronized (MyThread.class) {
                if (ticket < 100) {
                    try {
                        sleep(50);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    ticket++;
                    System.out.println(getName() + "正在卖第" + ticket + "张票！！");
                } else {
                    break;
                }
            }
        }
    }
}
