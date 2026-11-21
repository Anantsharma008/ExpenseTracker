package com.expensetracker.dao;

import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.util.CsvUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** CSV-file backed DAO: zero setup, data survives restarts. */
public class FileExpenseDao implements ExpenseDao {
    private final Path file;
    private final List<Expense> items = new ArrayList<>();

    public FileExpenseDao(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty()) continue;
                List<String> f = CsvUtil.split(line);
                items.add(new Expense(Integer.parseInt(f.get(0)), f.get(1), Double.parseDouble(f.get(2)),
                        Category.valueOf(f.get(3)), LocalDate.parse(f.get(4)), f.get(5)));
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not read data file: " + file, e);
        }
    }

    private void flush() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            List<String> lines = new ArrayList<>();
            for (Expense e : items) {
                lines.add(CsvUtil.join(String.valueOf(e.getId()), e.getTitle(), String.valueOf(e.getAmount()),
                        e.getCategory().name(), e.getDate().toString(), e.getNote()));
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override public synchronized List<Expense> findAll() { return new ArrayList<>(items); }

    @Override public synchronized void save(Expense expense) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId() == expense.getId()) { items.set(i, expense); flush(); return; }
        }
        items.add(expense);
        flush();
    }

    @Override public synchronized void delete(int id) {
        items.removeIf(e -> e.getId() == id);
        flush();
    }

    @Override public synchronized int nextId() {
        return items.stream().mapToInt(Expense::getId).max().orElse(0) + 1;
    }
}
