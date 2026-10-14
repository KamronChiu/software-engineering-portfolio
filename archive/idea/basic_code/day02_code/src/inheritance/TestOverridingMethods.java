package inheritance;

public class TestOverridingMethods {
    public static void main(String[] args) {
        SimpleAnimal sa = new SimpleAnimal();
        System.out.println(sa);
        sa.eat("poop");

        Cat c = new Cat("Mia",4.2,true,'F');
        System.out.println(c);
        c.eat("mouse");
        c.eat();
    }
}
