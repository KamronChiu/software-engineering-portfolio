package final_assignment001;

public class Car {
    private String stockCode;
    private String supplier;
    private int quantityInStock;
    private double price;
    //有参数构造
    public Car(String stockCode, String supplier, int quantityInStock, double price) {
        if (stockCode.length() != 10) {
            throw new IllegalArgumentException("stockCode must be 10 characters");
        }
        this.stockCode = stockCode;
        this.supplier = supplier;
        this.quantityInStock = quantityInStock;
        this.price = price;
    }
    //无参构造
    public Car() {}
//各个data fields的getter，setter方法
    public String getStockCode() {
        return stockCode;
    }
    public String getSupplier() {
        return supplier;
    }
    public int getQuantityInStock() {
        return quantityInStock;
    }
    public double getPrice() {
        return price;
    }
    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }
    public void setPrice(double price) {
        this.price = price;
    }

    public void addStock(int quantity) {
        if (quantity < 1) {
            System.out.println("should be greater than 0");
            return;
        }
        if (quantityInStock + quantity > 30) {
            System.out.println("The maximum quantity is 30");
            return;
        }
        quantityInStock += quantity;
        System.out.println("Stock successfully added. Current quantity: " + quantityInStock);
    }

    public boolean sellStock(int amount) {
        if (amount < 1) {
            System.out.println("Invalid amount. Amount must be greater than 0.");
            return false;
        }
        if (amount <= quantityInStock) {
            quantityInStock -= amount;
            System.out.println("Stock successfully sold. Current stock: " + quantityInStock);
            return true;
        } else {
            System.out.println("Not enough stock available. Sale not completed.");
            return false;
        }
    }
    public String getDescription() {
        return "Car Item Details: Stock Code = " + stockCode + ", Supplier = " + supplier +
                ", Quantity in Stock = " + quantityInStock + ", Price = $" + price;
    }
}
