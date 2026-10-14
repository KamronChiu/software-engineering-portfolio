package Demo3;

public class Animal {
    String name;
    double weight;
    private static int noOfObject;
    Circle c;

    public Animal() {
        noOfObject++;
    }

    public Animal(String name, double weight) {
        this.name = name;
        this.weight = weight;
        noOfObject++;
        c = new Circle();
    }
    static int getNoOfObject() {
        return noOfObject;
    }
}
