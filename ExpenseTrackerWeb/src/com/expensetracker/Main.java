package com.expensetracker;

import com.expensetracker.dao.FileExpenseDao;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.web.ApiServer;

import java.nio.file.Paths;

/** Entry point. Wires DAO -> Service -> HTTP server. Usage: java -cp out com.expensetracker.Main [port] */
public class Main {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        else if (System.getenv("PORT") != null) port = Integer.parseInt(System.getenv("PORT"));

        ExpenseService service = new ExpenseService(
                new FileExpenseDao(Paths.get("data", "expenses.csv")), Paths.get("data", "budget.txt"));
        if (service.isEmpty()) service.seedDemoData();   // demo data on first run only

        new ApiServer(service, Paths.get("web"), port).start();
        System.out.println("Expense Tracker running at http://localhost:" + port);
    }
}
