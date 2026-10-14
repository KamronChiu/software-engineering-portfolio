package com.kamron.software_testing.assignment1;

public class Circle extends Shape {
    private double radius = 0;

    public double getRadius() {
        return radius;
    }

    public Circle(double r) {
        super.shapeName = "Circle";
        if (r <= 0) {
            throw new IllegalArgumentException("Radius must be positive.");
        }
        this.radius = r;
    }

    @Override
    public double getArea() {
        return Math.PI * radius * radius;
    }

    @Override
    public double getPerimeter() {
        return 2 * Math.PI * radius;
    }
}
