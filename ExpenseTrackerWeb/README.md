# Expense Tracker (Web)

Java backend + HTML/CSS/JS frontend. Desktop Swing app ko web app me convert kiya gaya hai.

## Run
Requires JDK 11+ (koi external library / Maven nahi chahiye).
```
./run.sh            # Windows: run.bat
```
Phir browser me kholo: http://localhost:8080  (custom port: `./run.sh 9090` ya env `PORT`)

## Structure
```
src/com/expensetracker
 ├── model     Expense, Category
 ├── dao       ExpenseDao (interface), FileExpenseDao (CSV, data/expenses.csv)
 ├── service   ExpenseService – validation, filter, totals, budget (data/budget.txt)
 ├── web       ApiServer – REST API + static files (JDK HttpServer)
 └── util      CsvUtil, Json
web/            index.html, style.css, app.js
```

## REST API
| Method | Path | Kaam |
|---|---|---|
| GET | /api/meta | categories, months, budget, this-month total |
| GET | /api/expenses?q=&category=&month=YYYY-MM | filtered list + total + category totals |
| POST | /api/expenses | naya expense |
| PUT | /api/expenses/{id} | edit |
| DELETE | /api/expenses/{id} | delete |
| PUT | /api/budget | `{"amount": 15000}` |
| GET | /api/export?... | CSV download |

Database (MySQL) chahiye to sirf `FileExpenseDao` ki jagah JDBC DAO likho — baaki layers same rahenge.
