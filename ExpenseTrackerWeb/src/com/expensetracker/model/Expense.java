package com.expensetracker.model;

import java.time.LocalDate;

/** A single expense entry. */
public class Expense {
    private int id;
    private final String title;
    private final double amount;
    private final Category category;
    private final LocalDate date;
    private final String note;

    public Expense(int id, String title, double amount, Category category, LocalDate date, String note) {
        this.id = id;
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.date = date;
        this.note = note == null ? "" : note;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public double getAmount() { return amount; }
    public Category getCategory() { return category; }
    public LocalDate getDate() { return date; }
    public String getNote() { return note; }
}
