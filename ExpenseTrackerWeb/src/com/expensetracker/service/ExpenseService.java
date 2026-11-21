package com.expensetracker.service;

import com.expensetracker.dao.ExpenseDao;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/** Business logic: validation, filtering, totals and budget. The UI talks only to this class. */
public class ExpenseService {
    private final ExpenseDao dao;
    private final Path budgetFile;

    public ExpenseService(ExpenseDao dao, Path budgetFile) {
        this.dao = dao;
        this.budgetFile = budgetFile;
    }

    public void validate(Expense e) {
        if (e.getTitle().trim().isEmpty()) throw new IllegalArgumentException("Title is required.");
        if (e.getAmount() <= 0) throw new IllegalArgumentException("Amount must be greater than zero.");
        if (e.getDate().isAfter(LocalDate.now())) throw new IllegalArgumentException("Date cannot be in the future.");
    }

    public synchronized void add(Expense e) {
        validate(e);
        e.setId(dao.nextId());
        dao.save(e);
    }

    public synchronized void update(Expense e) {
        validate(e);
        dao.save(e);
    }

    public boolean exists(int id) {
        return dao.findAll().stream().anyMatch(e -> e.getId() == id);
    }

    public synchronized void delete(int id) {
        dao.delete(id);
    }

    public List<Expense> search(String keyword, Category category, YearMonth month) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        return dao.findAll().stream()
                .filter(e -> category == null || e.getCategory() == category)
                .filter(e -> month == null || YearMonth.from(e.getDate()).equals(month))
                .filter(e -> k.isEmpty() || e.getTitle().toLowerCase().contains(k) || e.getNote().toLowerCase().contains(k))
                .sorted(Comparator.comparing(Expense::getDate).reversed()
                        .thenComparing(Expense::getId, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    public double total(Collection<Expense> list) {
        return list.stream().mapToDouble(Expense::getAmount).sum();
    }

    public Map<Category, Double> totalsByCategory(Collection<Expense> list) {
        Map<Category, Double> map = new EnumMap<>(Category.class);
        for (Expense e : list) map.merge(e.getCategory(), e.getAmount(), Double::sum);
        return map;
    }

    public double spentIn(YearMonth month) {
        return dao.findAll().stream().filter(e -> YearMonth.from(e.getDate()).equals(month))
                .mapToDouble(Expense::getAmount).sum();
    }

    public List<YearMonth> months() {
        return dao.findAll().stream().map(e -> YearMonth.from(e.getDate())).distinct()
                .sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }

    public double getMonthlyBudget() {
        try {
            if (!Files.exists(budgetFile)) return 0;
            return Double.parseDouble(Files.readString(budgetFile).trim());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    public void setMonthlyBudget(double amount) {
        if (amount < 0) throw new IllegalArgumentException("Budget cannot be negative.");
        try {
            Files.createDirectories(budgetFile.toAbsolutePath().getParent());
            Files.writeString(budgetFile, String.valueOf(amount));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean isEmpty() { return dao.findAll().isEmpty(); }

    /** Sample data so the first launch looks good in a demo. */
    public void seedDemoData() {
        Object[][] rows = {
            {"Grocery shopping", 1850, Category.FOOD, 1, "Weekly groceries"},
            {"Metro card recharge", 500, Category.TRAVEL, 2, ""},
            {"Electricity bill", 2300, Category.BILLS, 4, "Monthly bill"},
            {"Movie night", 750, Category.ENTERTAINMENT, 6, "With friends"},
            {"Java textbook", 899, Category.EDUCATION, 8, ""},
            {"Pharmacy", 420, Category.HEALTH, 9, ""},
            {"New headphones", 2999, Category.SHOPPING, 11, "Sale offer"},
            {"Lunch at cafe", 380, Category.FOOD, 13, ""},
            {"Mobile recharge", 599, Category.BILLS, 15, ""},
            {"Cab to airport", 1250, Category.TRAVEL, 18, ""},
            {"Online course", 1499, Category.EDUCATION, 21, "Spring Boot"},
            {"Dinner out", 1100, Category.FOOD, 24, ""},
        };
        for (Object[] r : rows) {
            LocalDate d = LocalDate.now().minusDays((Integer) r[3]);
            add(new Expense(0, (String) r[0], ((Integer) r[1]).doubleValue(), (Category) r[2], d, (String) r[4]));
        }
    }
}
