package com.kamron.software_testing.whiteboxtesting;

public class OrderCalculator {
    /**
     * Calculate the final payment price.
     *
     * @param amount          Original order amount, must be ≥ 0
     * @param membershipLevel 0 = regular, 1 = silver, 2 = gold, 3 = diamond;
     *                        any other value → IllegalArgumentException
     * @param coupon          Coupon code, may be null or empty
     * @return                Discounted price, never below 0
     */
    public static double calculateFinalPrice(
            double amount, int membershipLevel, String coupon) {
        if (amount < 0) {
            throw new IllegalArgumentException("Invalid amount");
        }
        double price = amount;
        switch (membershipLevel) {
            case 1: price *= 0.95; break;
            case 2: price *= 0.90; break;
            case 3: price *= 0.85; break;
            case 0: /* no discount */ break;
            default:
                throw new IllegalArgumentException("Invalid membershipLevel");
        }
        // Coupon logic
        if (coupon != null && !coupon.isEmpty()) {
            if (coupon.startsWith("SAVE10")) {
                price -= 10;
            } else if (coupon.startsWith("SAVE20") && price > 100) {
                price -= 20;
            } else {
                price -= 5;
            }
        }
        return Math.max(price, 0);
    }
}
