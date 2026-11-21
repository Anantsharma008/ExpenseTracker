package com.expensetracker.dao;

import com.expensetracker.model.Expense;
import java.util.List;

/** Persistence contract. Swap the implementation (file, JDBC, ...) without touching the rest of the app. */
public interface ExpenseDao {
    List<Expense> findAll();
    void save(Expense expense);   // insert or update (matched by id)
    void delete(int id);
    int nextId();
}
