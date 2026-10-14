package com.kamron.software_testing.assignment1;

abstract class Shape {
    /*使用abstract类的目的是为了实现多态和代码复用
    * 1.多态：
    *               允许不同的对象通过相同的接口来执行不同的实现
    *               calculateArea();
calculateCircumference()的方法实现会根据每个形状不同而不同
* 2.代码复用：
*                   Shape类作为一个抽象类提供了一个公共的接口，允许所有的子类继承并使用这些方法
*
* 抽象类的作用：
* 1.提供统一的接口
* 2.避免实例化：不能直接创造Shape类型的对象
* 3.强制子类实现
    * */
    protected String shapeName = "Shape";

    public abstract double getArea();

    public void displayArea() {
        System.out.println("Area of " + shapeName + ": " + getArea());
    }

    public void displayCirc() {
        System.out.println("Circumference of " + shapeName + ": " + getPerimeter());
    }

    public abstract double getPerimeter();
}
