package final_assignment001;

public class TestPolymorphism {
    public static void main(String[] args) {
        Car car = new Car("D234567891", "General Motors", 20, 20000.0);
        Car electricCar = new ElectricCar("E876543210", "Tesla", 8, 48000.0, 100);
        Car petrolCar = new PetrolCar("F345678912", "Ford", 12, 30000.0, 60);

        // Polymorphism: Using Car reference to call overridden methods
        System.out.println(car.getDescription());
        System.out.println(electricCar.getDescription());
        System.out.println(petrolCar.getDescription());

        // Dynamic Binding: Invoking subclass-specific methods via type casting
        if (electricCar instanceof ElectricCar) {
            ElectricCar ec = (ElectricCar) electricCar;
            ec.setBatterySize(105);
            System.out.println("Updated Battery Size (Dynamic Binding): " + ec.getBatterySize());
        }

        if (petrolCar instanceof PetrolCar) {
            PetrolCar pc = (PetrolCar) petrolCar;
            pc.setTankSize(65);
            System.out.println("Updated Tank Size (Dynamic Binding): " + pc.getTankSize());
        }

        // Explore the static method in polymorphism (Note: Static methods are not polymorphic)
        System.out.println("ElectricCar Count: " + ElectricCar.getElectricCarCount());
        System.out.println("PetrolCar Count: " + PetrolCar.getPetrolCarCount());
    }
}
