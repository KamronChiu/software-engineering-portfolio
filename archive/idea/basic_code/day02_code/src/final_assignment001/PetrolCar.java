package final_assignment001;

public class PetrolCar extends Car {
    public static int petrolCarCount = 0;
    public double tankSize;

    public PetrolCar(String stockCode, String supplier, int quantityInStock, double price, double tankSize) {
        super(stockCode, supplier, quantityInStock, price);
        this.tankSize = tankSize;
        petrolCarCount++;
    }
    @Override
    public String getDescription(){
        return "ElectricCar Item Details: Stock Code = " + getStockCode() +
                ", Supplier = " + getSupplier() +
                ", Quantity in Stock = " + getQuantityInStock() +
                ", Price = $" + getPrice() +
                ", Tank  Size = " + tankSize + " kWh";
    }

    public double getTankSize() {
        return tankSize;
    }

    public void setTankSize(double tankSize) {
        this.tankSize = tankSize;
    }
    public static int getPetrolCarCount() {
        return petrolCarCount;
    }
}
