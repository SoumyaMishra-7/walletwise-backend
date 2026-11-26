package com.soumya.walletwise;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Lightweight JSON API intended as a drop-in local backend for the React client.
 * Data lives in memory so a database is not required during frontend development.
 */
@RestController
@RequestMapping({"/api", "/api/v1"})
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "https://wallet-wise-lyart.vercel.app"}, allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class ApiController {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<Long, Transaction> transactions = new ConcurrentHashMap<>();
    private final Map<String, Budget> budgets = new ConcurrentHashMap<>();
    private final MongoTemplate mongo;

    public ApiController(MongoTemplate mongo) { this.mongo = mongo; }

    /** Reload persisted records on restart so the API and MongoDB stay in sync. */
    @PostConstruct
    void loadPersistedState() {
        mongo.findAll(User.class, "users").forEach(user -> { if (user.email != null) users.put(user.email, user); });
        mongo.findAll(Transaction.class, "transactions").forEach(transaction -> { if (transaction.id > 0) transactions.put(transaction.id, transaction); });
        mongo.findAll(Budget.class, "budgets").forEach(budget -> { if (budget.id > 0 && budget.month != null && budget.category != null) budgets.put(budget.month + ":" + budget.category.toLowerCase(), budget); });
        long highestId = Math.max(
                transactions.keySet().stream().mapToLong(Long::longValue).max().orElse(0L),
                budgets.values().stream().mapToLong(budget -> budget.id).max().orElse(0L));
        ids.set(Math.max(ids.get(), highestId + 1));
    }

    @GetMapping({"", "/health"})
    public Map<String, Object> health() { return Map.of("success", true, "status", "ok", "message", "WalletWise Java API is running"); }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody AuthRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.containsKey(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        User user = new User(ids.getAndIncrement(), request.nameOrUsername(), email, request.password());
        users.put(email, user);
        mongo.save(user, "users");
        return authResponse(user);
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@Valid @RequestBody AuthRequest request) {
        User user = users.get(request.email().trim().toLowerCase());
        if (user == null || !Objects.equals(user.password, request.password())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        return authResponse(user);
    }

    @GetMapping({"/auth/me", "/users/me", "/profile"})
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String ignored) {
        return Map.of("success", true, "user", currentUser());
    }

    @PutMapping({"/auth/profile", "/users/me", "/profile"})
    public Map<String, Object> updateProfile(@RequestBody ProfileRequest request) {
        User user = currentUser();
        if (request.name() != null && !request.name().isBlank()) user.name = request.name().trim();
        if (request.currency() != null && !request.currency().isBlank()) user.currency = request.currency().trim().toUpperCase();
        if (request.theme() != null && !request.theme().isBlank()) user.theme = request.theme().trim();
        mongo.save(user, "users");
        return Map.of("success", true, "user", user);
    }

    @GetMapping({"/transactions", "/transaction"})
    public Map<String, Object> listTransactions(@RequestParam(required = false) String type, @RequestParam(required = false) String category,
                                                 @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int limit,
                                                 @RequestParam(required = false) String search, @RequestParam(required = false) String startDate,
                                                 @RequestParam(required = false) String endDate, @RequestParam(defaultValue = "newest") String sort) {
        List<Transaction> result = transactions.values().stream()
                .filter(t -> type == null || t.type.equalsIgnoreCase(type))
                .filter(t -> category == null || t.category.equalsIgnoreCase(category))
                .filter(t -> search == null || search.isBlank() || t.description.toLowerCase().contains(search.toLowerCase()) || t.category.toLowerCase().contains(search.toLowerCase()))
                .filter(t -> startDate == null || startDate.isBlank() || !t.date.isBefore(LocalDate.parse(startDate.substring(0, 10))))
                .filter(t -> endDate == null || endDate.isBlank() || !t.date.isAfter(LocalDate.parse(endDate.substring(0, 10))))
                .sorted(transactionComparator(sort)).toList();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safePage = Math.max(1, page);
        int from = Math.min((safePage - 1) * safeLimit, result.size());
        int to = Math.min(from + safeLimit, result.size());
        return Map.of("success", true, "transactions", result.subList(from, to), "data", result.subList(from, to), "count", result.size(),
                "pagination", Map.of("page", safePage, "limit", safeLimit, "total", result.size(), "pages", Math.max(1, (int) Math.ceil((double) result.size() / safeLimit))));
    }

    @PostMapping({"/transactions", "/transaction"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTransaction(@Valid @RequestBody TransactionRequest request) {
        String type = request.type().toLowerCase();
        if (!Set.of("income", "expense").contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be income or expense");
        Transaction transaction = new Transaction(ids.getAndIncrement(), request.titleOrDescription(), request.amount(), type, request.categoryOrDefault(), request.date() == null ? LocalDate.now() : request.date());
        transactions.put(transaction.id, transaction);
        mongo.save(transaction, "transactions");
        return Map.of("success", true, "message", "Transaction created", "transaction", transaction, "data", transaction);
    }

    @PutMapping("/transactions/{id}")
    public Map<String, Object> updateTransaction(@PathVariable long id, @RequestBody TransactionRequest request) {
        Transaction transaction = getTransaction(id);
        if (request.titleOrDescription() != null) transaction.description = request.titleOrDescription();
        if (request.amount() != null) transaction.amount = request.amount();
        if (request.type() != null) transaction.type = request.type().toLowerCase();
        if (request.category() != null && !request.category().isBlank()) transaction.category = request.category();
        if (request.date() != null) transaction.date = request.date();
        mongo.save(transaction, "transactions");
        return Map.of("success", true, "transaction", transaction, "data", transaction);
    }

    @DeleteMapping("/transactions/{id}")
    public Map<String, Object> deleteTransaction(@PathVariable long id) { getTransaction(id); transactions.remove(id); mongo.remove(Query.query(Criteria.where("id").is(id)), "transactions"); return Map.of("success", true, "message", "Transaction deleted"); }

    @GetMapping("/transactions/{id}")
    public Map<String, Object> getTransactionById(@PathVariable long id) { Transaction transaction = getTransaction(id); return Map.of("success", true, "transaction", transaction, "data", transaction); }

    @PatchMapping("/transactions/{id}")
    public Map<String, Object> patchTransaction(@PathVariable long id, @RequestBody TransactionRequest request) { return updateTransaction(id, request); }

    @PostMapping("/transactions/{id}/undo")
    public Map<String, Object> undoTransaction(@PathVariable long id) { return deleteTransaction(id); }

    @PostMapping({"/transactions/{id}/skip", "/transactions/recurring/{id}/skip"})
    public Map<String, Object> skipTransaction(@PathVariable long id) { getTransaction(id); return Map.of("success", true, "message", "Next recurring occurrence skipped"); }

    @GetMapping({"/budgets", "/budget"})
    public Map<String, Object> listBudgets() { List<Budget> list = new ArrayList<>(budgets.values()); return Map.of("success", true, "budgets", list, "data", list); }

    @PostMapping({"/budgets", "/budget"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createBudget(@Valid @RequestBody BudgetRequest request) {
        Budget budget = new Budget(ids.getAndIncrement(), request.categoryOrDefault(), request.amountOrLimit(), request.monthOrCurrent());
        budgets.put(budget.month + ":" + budget.category.toLowerCase(), budget);
        mongo.save(budget, "budgets");
        return Map.of("success", true, "budget", budget, "data", budget);
    }

    @GetMapping("/budget/current")
    public Map<String, Object> currentBudget() { return budgetForMonth(YearMonth.now().toString()); }

    @GetMapping("/budget/{month}")
    public Map<String, Object> budgetByMonth(@PathVariable String month) { return budgetForMonth(month); }

    @PutMapping("/budget/{id}")
    public Map<String, Object> updateBudget(@PathVariable long id, @RequestBody BudgetRequest request) { Budget budget = budgets.values().stream().filter(b -> b.id == id).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found")); budgets.remove(budget.month + ":" + budget.category.toLowerCase()); Budget replacement = new Budget(id, request.categoryOrDefault(), request.amountOrLimit(), request.monthOrCurrent()); budgets.put(replacement.month + ":" + replacement.category.toLowerCase(), replacement); mongo.save(replacement, "budgets"); return Map.of("success", true, "budget", replacement); }

    @DeleteMapping("/budget/{id}")
    public Map<String, Object> deleteBudget(@PathVariable long id) { budgets.entrySet().removeIf(e -> e.getValue().id == id); mongo.remove(Query.query(Criteria.where("id").is(id)), "budgets"); return Map.of("success", true, "message", "Budget deleted"); }

    @GetMapping("/budget/stats/summary")
    public Map<String, Object> budgetStats() { BigDecimal allocated = budgets.values().stream().map(b -> b.amount).reduce(BigDecimal.ZERO, BigDecimal::add); BigDecimal spent = sum("expense"); return Map.of("success", true, "totalBudget", allocated, "totalSpent", spent, "remaining", allocated.subtract(spent), "percentageUsed", allocated.signum() == 0 ? BigDecimal.ZERO : spent.multiply(BigDecimal.valueOf(100)).divide(allocated, 2, java.math.RoundingMode.HALF_UP)); }

    @PostMapping("/budget/copy-previous")
    public Map<String, Object> copyPreviousBudget() { return Map.of("success", true, "message", "No previous budget exists to copy"); }

    @GetMapping({"/dashboard", "/dashboard/summary", "/analytics/summary"})
    public Map<String, Object> dashboard() {
        BigDecimal income = sum("income"), expenses = sum("expense");
        Map<String, BigDecimal> byCategory = transactions.values().stream().filter(t -> "expense".equals(t.type)).collect(Collectors.groupingBy(t -> t.category, Collectors.reducing(BigDecimal.ZERO, t -> t.amount, BigDecimal::add)));
        return Map.of("success", true, "totalIncome", income, "totalExpenses", expenses, "totalBalance", income.subtract(expenses), "income", income, "expenses", expenses, "balance", income.subtract(expenses), "categoryBreakdown", byCategory, "recentTransactions", transactions.values().stream().sorted(Comparator.comparing((Transaction t) -> t.date).reversed()).limit(5).toList());
    }

    private Comparator<Transaction> transactionComparator(String sort) { return switch (sort) { case "oldest" -> Comparator.comparing(t -> t.date); case "amount-high" -> Comparator.comparing((Transaction t) -> t.amount).reversed(); case "amount-low" -> Comparator.comparing(t -> t.amount); default -> Comparator.comparing((Transaction t) -> t.date).reversed().thenComparingLong(t -> -t.id); }; }
    private Transaction getTransaction(long id) { Transaction t = transactions.get(id); if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"); return t; }
    private BigDecimal sum(String type) { return transactions.values().stream().filter(t -> type.equals(t.type)).map(t -> t.amount).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private Map<String, Object> budgetForMonth(String month) { List<Budget> found = budgets.values().stream().filter(b -> b.month.equals(month)).toList(); if (found.isEmpty()) return Map.of("success", true, "hasBudget", false); return Map.of("success", true, "hasBudget", true, "budget", found.getFirst()); }
    private User currentUser() { return users.values().stream().findFirst().orElseGet(() -> { User demo = new User(0, "WalletWise User", "demo@walletwise.local", ""); users.put(demo.email, demo); mongo.save(demo, "users"); return demo; }); }
    private Map<String, Object> authResponse(User user) { String token = Base64.getUrlEncoder().withoutPadding().encodeToString((user.email + ":" + UUID.randomUUID()).getBytes()); return Map.of("success", true, "message", "Authentication successful", "token", token, "accessToken", token, "user", user, "data", Map.of("token", token, "user", user)); }

    public static class User { @Id public String documentId; public long id; public String name, email; private String password; public String currency = "INR", theme = "light"; public User() {} User(long id, String name, String email, String password) { this.id=id; this.name=name; this.email=email; this.password=password; } public long getId(){return id;} public String getName(){return name;} public String getEmail(){return email;} public String getCurrency(){return currency;} public String getTheme(){return theme;} }
    public static class Transaction { @Id public String documentId; public long id; public String description; public BigDecimal amount; public String type, category; public LocalDate date; public Transaction() {} Transaction(long id,String description,BigDecimal amount,String type,String category,LocalDate date){this.id=id;this.description=description;this.amount=amount;this.type=type;this.category=category;this.date=date;} public long getId(){return id;} public String getDescription(){return description;} public BigDecimal getAmount(){return amount;} public String getType(){return type;} public String getCategory(){return category;} public LocalDate getDate(){return date;} }
    public static class Budget { @Id public String documentId; public long id; public String category, month; public BigDecimal amount; public Budget() {} Budget(long id,String category,BigDecimal amount,String month){this.id=id;this.category=category;this.amount=amount;this.month=month;} public long getId(){return id;} public String getCategory(){return category;} public BigDecimal getAmount(){return amount;} public BigDecimal getLimit(){return amount;} public String getMonth(){return month;} }
    public record AuthRequest(@Email @NotBlank String email, @NotBlank String password, String name, String username) { String nameOrUsername(){ return name != null && !name.isBlank() ? name.trim() : (username != null && !username.isBlank() ? username.trim() : email.substring(0,email.indexOf('@'))); } }
    public record ProfileRequest(String name, String currency, String theme) {}
    public record TransactionRequest(String title, String description, @NotNull @Positive BigDecimal amount, @NotBlank String type, String category, LocalDate date) { String titleOrDescription(){return title != null && !title.isBlank()?title:description;} String categoryOrDefault(){return category == null || category.isBlank()?"Other":category;} }
    public record BudgetRequest(String category, BigDecimal amount, BigDecimal limit, String month) { String categoryOrDefault(){return category == null || category.isBlank()?"General":category;} BigDecimal amountOrLimit(){ BigDecimal value=amount != null?amount:limit; if(value==null || value.signum()<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"amount must be positive"); return value;} String monthOrCurrent(){return month == null || month.isBlank()? YearMonth.now().toString():month;} }
}
