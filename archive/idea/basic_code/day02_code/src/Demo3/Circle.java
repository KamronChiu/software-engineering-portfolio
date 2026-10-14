package Demo3;

public class Circle {

    public double radius = 1;//instance variable 实例变量，类变量

    public Circle() {  //constructor 构造方法
    }

    public Circle(double radius) {  //constructor 构造方法
        this.radius = radius;
    }

    double getArea() {  //instance method 实例方法
        return radius * radius * Math.PI;
    }

    double getPerimeter() {  //instance method 实例方法
        return 2 * radius * Math.PI;
    }

    void setRadius(double newRadius) {  //instance method 实例方法
        radius = newRadius;
    }

}
