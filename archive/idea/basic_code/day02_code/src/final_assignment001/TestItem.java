package final_assignment001;

public class TestItem {
    public static void main(String[] args) {
        Car c = new Car("1389846370","Porsche",5,190000.0);
        ElectricCar ec = new ElectricCar("4826202719","BYD",50,3000.0,40);
        ElectricCar ec2 = new ElectricCar("1846205717","HUAWEI",21,3800.9,50);
        //这多一个ElectricCar对象, 证明count方法被调用
        PetrolCar pc = new PetrolCar("9856262769","Tesla",30,7000.67,300);
        System.out.println(c.getDescription());
        c.addStock(5);
        c.sellStock(10);
        c.setSupplier("Updated Supplier");
        c.setPrice(16000.0);
        System.out.println("Updated Car: " + c.getDescription());

        // ElectricCar class methods
        System.out.println(ec.getDescription());
        ec.setBatterySize(90);
        System.out.println("Updated Battery Size: " + ec.getBatterySize());
        System.out.println("Total Electric Cars: " + ElectricCar.getElectricCarCount());

        // PetrolCar class methods
        System.out.println(pc.getDescription());
        pc.setTankSize(55);
        System.out.println("Updated Tank Size: " + pc.getTankSize());
        System.out.println("Total Petrol Cars: " + PetrolCar.getPetrolCarCount());
    }
}

