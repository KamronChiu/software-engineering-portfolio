package com.kamron.software_testing.assignment1;

public class Rectangle extends Shape {
    private double width = 0;
    private double height = 0;

    public Rectangle(double height, double width) {
        super.shapeName = "Rectangle";
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive.");
        }
        this.height = height;
        this.width = width;
    }

    @Override
    public double getArea() {
        return width * height;
    }

    @Override
    public double getPerimeter() {
        return 2 * (width + height);
    }
}
