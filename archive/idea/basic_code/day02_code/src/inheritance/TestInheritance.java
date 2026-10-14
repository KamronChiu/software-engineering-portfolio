package inheritance;

public class TestInheritance {
    public static void main(String[] args) {
        Bird b = new Bird();
        System.out.println(b.name);
        System.out.println(b.weight);
        System.out.println(b.adopted);
        System.out.println(b.getGender());
        System.out.println(b.getNoOfObjects());
        Cat c = new Cat("Mia", 4.2, true, 'F');
        System.out.println(c.name);
        System.out.println(c.weight);
        System.out.println(c.adopted);
        System.out.println(c.getGender());
        System.out.println(c.getNoOfObjects());
        System.out.println(SimpleAnimal.getNoOfObjects());

    }
}
