package com.stockapp.model;

import javafx.beans.property.*;

public class CartItem {
    private final IntegerProperty productId = new SimpleIntegerProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty barcode = new SimpleStringProperty();
    private final DoubleProperty unitPrice = new SimpleDoubleProperty();
    private final IntegerProperty qty = new SimpleIntegerProperty();

    public CartItem(int productId, String name, String barcode, double unitPrice, int qty) {
        this.productId.set(productId);
        this.name.set(name);
        this.barcode.set(barcode);
        this.unitPrice.set(unitPrice);
        this.qty.set(qty);
    }

    public int getProductId() { return productId.get(); }
    public String getName() { return name.get(); }
    public String getBarcode() { return barcode.get(); }
    public double getUnitPrice() { return unitPrice.get(); }
    public int getQty() { return qty.get(); }

    public IntegerProperty qtyProperty() { return qty; }
    public DoubleProperty unitPriceProperty() { return unitPrice; }
    public StringProperty nameProperty() { return name; }
    public StringProperty barcodeProperty() { return barcode; }

    public void incQty(int delta) { qty.set(qty.get() + delta); }

    public double getLineTotal() { return getUnitPrice() * getQty(); }
    public void setQty(int newQty) {
        if (newQty < 0) newQty = 0;
        this.qtyProperty().set(newQty);
    }

}
