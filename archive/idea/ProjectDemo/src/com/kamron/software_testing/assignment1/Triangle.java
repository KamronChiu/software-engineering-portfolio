package com.kamron.software_testing.assignment1;

public class Triangle extends Shape {
    private double a = 0, b = 0, c = 0;
    private double perimeter;

    //this.perimeter在构造函数里，只能在这个类中使用。在java中构造函数不能被直接调用，只能创建对象之后用他的类里面的方法
    public Triangle(double a, double b, double c) {
        super.shapeName = "Triangle";
        if (!isValidTriangle(a, b, c)) {
            throw new IllegalArgumentException("Invalid triangle");
        }
        this.a = a;
        this.b = b;
        this.c = c;
        this.perimeter = a + b + c;
    }

    @Override
    public double getArea() {
        double s = (a * b * c) / 2;
        return Math.sqrt(s * (s - a) * (s - b) * (s - c));
    }

    @Override
    public double getPerimeter() {
        return a + b + c;
    }

    public boolean isValidTriangle(double a, double b, double c) {
        return a >= 0 && b >= 0 && c >= 0 && (a + b > c) && (b + c > a) && (a + c > b);
    }
}
