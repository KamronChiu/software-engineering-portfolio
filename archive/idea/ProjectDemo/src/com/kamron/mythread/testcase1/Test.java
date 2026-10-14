package com.kamron.mythread.testcase1;

public class Test {
    public static void main(String[] args) {
        /*微信中的抢红包也用了多线程
                假设：100块钱，分成三个包，现在有五个人去抢
                其中：红包是共享的数据*/
        MyThread t1= new MyThread();
        MyThread t2= new MyThread();
        MyThread t3= new MyThread();
        MyThread t4= new MyThread();
        MyThread t5= new MyThread();

        t1.setName("t1");
        t2.setName("t2");
        t3.setName("t3");
        t4.setName("t4");
        t5.setName("t5");

        t1.start();
        t2.start();
        t3.start();
        t4.start();
        t5.start();

    }
}
