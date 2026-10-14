package com.kamron.mythread.threadcase2;

public class ThreadDemo {
    public static void main(String[] args) {
        //要求：某电影院正在上映国产大片，共100张票，而他有三个窗口卖票，请设计一个程序模拟该电影院卖票
        MyThread mt1 = new MyThread();
        MyThread mt2 = new MyThread();
        MyThread mt3 = new MyThread();
        //起名字
        mt1.setName("window 1");
        mt2.setName("window 2");
        mt3.setName("window 3");

        mt1.start();
        mt2.start();
        mt3.start();
    }
}
