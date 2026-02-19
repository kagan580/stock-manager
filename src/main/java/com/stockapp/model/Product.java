package com.stockapp.model;

public class Product {

    private int id;
    private String name;
    private String barcode;
    private int categoryId;
    private String categoryName;
    private int stock;
    private double price;

    public Product(int id, String name, String barcode, int categoryId, String categoryName, int stock, double price) {
        this.id = id;
        this.name = name;
        this.barcode = barcode;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.stock = stock;
        this.price = price;
    }

    public Product(String name, String barcode, int categoryId, int stock, double price) {
        this(0, name, barcode, categoryId, null, stock, price);
    }


    public int getId() { return id; }
    public String getName() { return name; }
    public String getBarcode() { return barcode; }
    public int getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public int getStock() { return stock; }
    public double getPrice() { return price; }

    public void setId(int id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public void setPrice(double price) {
        this.price = price;
    }
}
