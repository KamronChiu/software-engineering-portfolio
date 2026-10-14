package com.kamron.software_testing.assignment1;

import java.util.Scanner;

enum ShapeType {
    CIRCLE, RECTANGLE, TRIANGLE
}

public class App {
    private static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        Shape circle = new Circle(getDoubleInput("Enter radius of circle: "));
        circle.displayArea();

        Shape rectangle = new Rectangle(
                getDoubleInput("Enter width of rectangle: "),
                getDoubleInput("Enter height of rectangle: ")
        );
        rectangle.displayArea();
        testShape(ShapeType.RECTANGLE, rectangle);

        Shape triangle = new Triangle(
                getDoubleInput("Enter side a of triangle: "),
                getDoubleInput("Enter side b of triangle: "),
                getDoubleInput("Enter side c of triangle: ")
        );
        triangle.displayArea();
        testShape(ShapeType.TRIANGLE, triangle);

        long endTime = System.currentTimeMillis();
        System.out.println("Elapsed time: " + (endTime - startTime) + " ms");
    }

    private static double getDoubleInput(String prompt) {
        System.out.println(prompt);
        while (!sc.hasNextDouble()) {
            System.out.println("Invalid input. Please enter a valid number.");
            sc.next();
        }
        return sc.nextDouble();
    }

    private static void testShape(ShapeType shapeType, Shape shape) {
        switch (shapeType) {
            case TRIANGLE:
                Triangle triangle = (Triangle) shape;
                double expectedPerimeter = triangle.getPerimeter();
                System.out.println("Testing Triangle: " + expectedPerimeter);
                if (expectedPerimeter == triangle.getPerimeter()) {
                    System.out.println("Pass");
                } else {
                    System.out.println("Fail");
                }
                break;

            case RECTANGLE:
                Rectangle rect = (Rectangle) shape;
                double expectedArea = rect.getArea();
                System.out.println("Testing Rectangle: Expected: " + expectedArea);
                if (expectedArea == rect.getArea()) {
                    System.out.println("Pass");
                } else {
                    System.out.println("Fail");
                }
                break;

            default:
                System.out.println("No test available for this shape.");
        }
    }
}