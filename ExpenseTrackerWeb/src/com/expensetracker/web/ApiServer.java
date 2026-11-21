package com.expensetracker.web;

import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.util.CsvUtil;
import com.expensetracker.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/** REST API + static file server built on the JDK's own HttpServer (no external libraries). */
public class ApiServer {
    private final ExpenseService service;
    private final Path webRoot;
    private final HttpServer server;

    public ApiServer(ExpenseService service, Path webRoot, int port) throws IOException {
        this.service = service;
        this.webRoot = webRoot.toAbsolutePath().normalize();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public void start() { server.start(); }

    // ---------------- API ----------------

    private void handleApi(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());

            if (path.equals("/api/meta") && method.equals("GET")) {
                sendJson(ex, 200, meta());
            } else if (path.equals("/api/expenses") && method.equals("GET")) {
                sendJson(ex, 200, listJson(filter(q)));
            } else if (path.equals("/api/expenses") && method.equals("POST")) {
                Expense e = parseExpense(0, body(ex));
                service.add(e);
                sendJson(ex, 201, expenseJson(e));
            } else if (path.startsWith("/api/expenses/") && (method.equals("PUT") || method.equals("DELETE"))) {
                int id = Integer.parseInt(path.substring("/api/expenses/".length()));
                if (!service.exists(id)) { error(ex, 404, "Expense not found."); return; }
                if (method.equals("DELETE")) {
                    service.delete(id);
                    sendJson(ex, 200, "{\"deleted\":" + id + "}");
                } else {
                    Expense e = parseExpense(id, body(ex));
                    service.update(e);
                    sendJson(ex, 200, expenseJson(e));
                }
            } else if (path.equals("/api/budget") && method.equals("PUT")) {
                Map<String, String> b = Json.parseObject(body(ex));
                service.setMonthlyBudget(Double.parseDouble(required(b, "amount")));
                sendJson(ex, 200, "{\"budget\":" + service.getMonthlyBudget() + "}");
            } else if (path.equals("/api/export") && method.equals("GET")) {
                exportCsv(ex, filter(q));
            } else {
                error(ex, 404, "Unknown endpoint.");
            }
        } catch (IllegalArgumentException | DateTimeException e) {
            error(ex, 400, e.getMessage() == null ? "Invalid request." : e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            error(ex, 500, "Server error.");
        } finally {
            ex.close();
        }
    }

    private List<Expense> filter(Map<String, String> q) {
        String cat = q.get("category");
        String month = q.get("month");
        Category category = (cat == null || cat.isEmpty()) ? null : Category.valueOf(cat);
        YearMonth ym = (month == null || month.isEmpty()) ? null : YearMonth.parse(month);
        return service.search(q.get("q"), category, ym);
    }

    private Expense parseExpense(int id, String json) {
        Map<String, String> m = Json.parseObject(json);
        String title = required(m, "title").trim();
        double amount = Double.parseDouble(required(m, "amount"));
        Category category = Category.valueOf(required(m, "category"));
        LocalDate date = LocalDate.parse(required(m, "date"));
        String note = m.get("note");
        return new Expense(id, title, amount, category, date, note == null ? "" : note.trim());
    }

    private static String required(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(cap(key) + " is required.");
        return v;
    }

    private static String cap(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    // ---------------- JSON building ----------------

    private String expenseJson(Expense e) {
        return "{\"id\":" + e.getId()
                + ",\"title\":" + Json.quote(e.getTitle())
                + ",\"amount\":" + e.getAmount()
                + ",\"category\":" + Json.quote(e.getCategory().name())
                + ",\"date\":" + Json.quote(e.getDate().toString())
                + ",\"note\":" + Json.quote(e.getNote()) + "}";
    }

    private String listJson(List<Expense> list) {
        StringBuilder sb = new StringBuilder("{\"expenses\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(expenseJson(list.get(i)));
        }
        sb.append("],\"count\":").append(list.size());
        sb.append(",\"total\":").append(service.total(list));
        sb.append(",\"byCategory\":{");
        boolean first = true;
        for (Map.Entry<Category, Double> en : service.totalsByCategory(list).entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(Json.quote(en.getKey().name())).append(':').append(en.getValue());
        }
        return sb.append("}}").toString();
    }

    private String meta() {
        StringBuilder sb = new StringBuilder("{\"categories\":[");
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"key\":").append(Json.quote(cats[i].name()))
              .append(",\"label\":").append(Json.quote(cats[i].getLabel()))
              .append(",\"color\":").append(Json.quote(cats[i].getColor())).append('}');
        }
        sb.append("],\"months\":[");
        List<YearMonth> months = service.months();
        for (int i = 0; i < months.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(Json.quote(months.get(i).toString()));
        }
        YearMonth now = YearMonth.now();
        sb.append("],\"currentMonth\":").append(Json.quote(now.toString()));
        sb.append(",\"spentThisMonth\":").append(service.spentIn(now));
        sb.append(",\"budget\":").append(service.getMonthlyBudget()).append('}');
        return sb.toString();
    }

    private void exportCsv(HttpExchange ex, List<Expense> list) throws IOException {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(CsvUtil.join("ID", "Title", "Amount", "Category", "Date", "Note")).append("\r\n");
        for (Expense e : list) {
            sb.append(CsvUtil.join(String.valueOf(e.getId()), e.getTitle(), String.format("%.2f", e.getAmount()),
                    e.getCategory().getLabel(), e.getDate().toString(), e.getNote())).append("\r\n");
        }
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"expenses.csv\"");
        send(ex, 200, "text/csv; charset=utf-8", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ---------------- static files ----------------

    private void handleStatic(HttpExchange ex) throws IOException {
        try {
            if (!ex.getRequestMethod().equals("GET")) { send(ex, 405, "text/plain", "Method not allowed".getBytes()); return; }
            String p = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
            if (p.equals("/")) p = "/index.html";
            Path file = webRoot.resolve(p.substring(1)).normalize();
            if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
                send(ex, 404, "text/plain; charset=utf-8", "404 Not Found".getBytes());
                return;
            }
            send(ex, 200, contentType(file.getFileName().toString()), Files.readAllBytes(file));
        } finally {
            ex.close();
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    // ---------------- helpers ----------------

    private static String body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null) return map;
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int i = pair.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            map.put(k, v);
        }
        return map;
    }

    private void error(HttpExchange ex, int code, String msg) throws IOException {
        sendJson(ex, code, "{\"error\":" + Json.quote(msg) + "}");
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        send(ex, code, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange ex, int code, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }
}
