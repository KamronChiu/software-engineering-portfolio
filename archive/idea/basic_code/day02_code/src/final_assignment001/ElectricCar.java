package final_assignment001;

public class ElectricCar extends Car {
    private static int electricCarCount = 0;
    private int batterySize;

    public ElectricCar(String stockCode, String supplier, int quantityInStock, double price, int batterySize) {
        super(stockCode, supplier, quantityInStock, price); // Call the constructor of Car
        this.batterySize = batterySize; //set the value for battery size.
        electricCarCount++; // 每创造一个ElectricCar对象（多了一台车）时electricCarCount都会加1。
        //The electricCarCount increases by 1 each time an ElectricCar object (one more car) is created
    }

    @Override
    public String getDescription() {
        return "ElectricCar Item Details: Stock Code = " + getStockCode() +
                ", Supplier = " + getSupplier() +
                ", Quantity in Stock = " + getQuantityInStock() +
                ", Price = $" + getPrice() +
                ", Battery Size = " + batterySize + " kWh";
    }

    public static int getElectricCarCount() {
        return electricCarCount;
    }

    public int getBatterySize() {
        return batterySize;
    }

    public void setBatterySize(int batterySize) {
        this.batterySize = batterySize;
    }
}
