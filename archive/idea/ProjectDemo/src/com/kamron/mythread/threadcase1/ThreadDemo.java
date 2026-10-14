package com.kamron.mythread.threadcase1;

public class ThreadDemo {
    public static void main(String[] args) {
        /*多线程的第一种启动方法：
        1.自己定义一个继承Thread
        2.重写run方法
        3.创建子类的对象，并启动线程
        */
        MyThread t1 = new MyThread();
        MyThread t2 = new MyThread();
        t1.setName("thread1");
        t2.setName("thread2");
        //开启线程
        t1.start();
        t2.start();

/*        多线程第二种启动方法：
        1.自己定义一个类实现Runnable接口
            2.重写里面的run方法
            3.创建自己的类对象
            4.创建一个Thread的对象，并开启线程*/
        MyRun mr = new MyRun();
        Thread t3 = new Thread(mr);
        t3.start();
    }
}
