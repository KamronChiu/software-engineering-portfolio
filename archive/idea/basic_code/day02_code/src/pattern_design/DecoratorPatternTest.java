package pattern_design;

// test class demonstrating the Decorator pattern
public class DecoratorPatternTest {
    public static void main(String[] args) {
        Coffee coffee = new BasicCoffee();

        coffee = new MilkDecorator(coffee);
        coffee = new SugarDecorator(coffee);

        System.out.println(coffee.getDescription());
        System.out.println("Total cost: £" + coffee.cost());
    }
}

