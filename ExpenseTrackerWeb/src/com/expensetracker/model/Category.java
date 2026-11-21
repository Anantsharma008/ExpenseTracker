package com.expensetracker.model;

/** Expense categories with a display label and a chart colour (hex). */
public enum Category {
    FOOD("Food", "#F97316"),
    TRAVEL("Travel", "#0EA5E9"),
    SHOPPING("Shopping", "#EC4899"),
    BILLS("Bills", "#8B5CF6"),
    ENTERTAINMENT("Entertainment", "#EAB308"),
    HEALTH("Health", "#10B981"),
    EDUCATION("Education", "#6366F1"),
    OTHER("Other", "#64748B");

    private final String label;
    private final String color;

    Category(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() { return label; }
    public String getColor() { return color; }

    @Override
    public String toString() { return label; }
}
