package api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import db.Db;
import db.Db.Account;
import db.Db.AttendanceRecord;
import db.Db.AttendanceSummary;
import db.Db.Book;
import db.Db.Contact;
import db.Db.CourseOption;
import db.Db.CourseRow;
import db.Db.GradeRow;
import db.Db.Loan;
import db.Db.MarkableSession;
import db.Db.MessageRow;
import db.Db.SessionRow;
import db.Db.StudentMark;
import db.Db.StudentProfile;
import db.Db.TaskRow;
import db.Db.TeacherCourse;
import db.Db.TranscriptEntry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Small JSON API on the built-in JDK HTTP server.
 *
 * POST /api/auth/login      {"username", "password"} -> {token, user}
 * POST /api/auth/logout     Authorization: Bearer <token>
 * GET  /api/me              current user
 * GET  /api/schedule        {user, courses, sessions}
 * GET  /api/registration    {credits, limit, courses}
 * POST /api/registration    {"code"} -> refreshed registration
 * GET  /api/grades          student rows or teacher courses
 * POST /api/grades          {"code", "studentId", "att1", "att2", "finalScore"} (teacher)
 * GET  /api/transcript      {entries, credits, gpa}
 * GET  /api/attendance      {summary | sessions, records}
 * POST /api/attendance      {"sessionId", "studentId", "date", "status"} (teacher)
 * GET  /api/library         {books, loans}
 * POST /api/library/borrow  {"bookId"}
 * POST /api/library/return  {"loanId"}
 * GET  /api/messages        {inbox, sent, contacts}
 * POST /api/messages        {"recipient", "body"}
 * GET  /api/tasks           {tasks} — university to-do list
 * POST /api/tasks           {"title", "dueDate", "priority"}
 * POST /api/tasks/done      {"id", "done"}
 */
public final class ApiServer {

    private final Db db;
    /** Session token -> user id. Sessions live in memory and end when the server restarts. */
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    private ApiServer(Db db) {
        this.db = db;
    }

    public static void main(String[] args) throws Exception {
        int port = parsePort(args.length > 0 ? args[0] : env("PORT", "8080"));
        String dbPath = env("DB_PATH", "data/university.db");

        ApiServer api = new ApiServer(Db.open(dbPath));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", api::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("API listening on http://localhost:" + server.getAddress().getPort());
        System.out.println("Database: " + dbPath);
        System.out.println("Demo accounts: student1/stud123, student2/stud123, teacher1/teach123,"
                + " teacher2/teach123, manager/manager123, admin/admin123");
    }

    /**
     * Accepts a port between 1 and 65535. Anything else (nothing given, a shell
     * comment such as "#", a typo) falls back to 8080 instead of failing.
     */
    private static int parsePort(String value) {
        if (value != null && value.trim().matches("\\d{1,5}")) {
            int port = Integer.parseInt(value.trim());
            if (port >= 1 && port <= 65535) {
                return port;
            }
        }
        if (value != null && !value.isBlank()) {
            System.err.println("Ignoring unusable port \"" + value + "\", using 8080 instead.");
        }
        return 8080;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        try {
            if ("OPTIONS".equals(method)) {
                send(exchange, 204, null);
                return;
            }
            switch (method + " " + path) {
                case "POST /api/auth/login" -> login(exchange);
                case "POST /api/auth/logout" -> logout(exchange);
                case "GET /api/me" -> me(exchange);
                case "GET /api/schedule" -> schedule(exchange);
                case "GET /api/registration" -> registration(exchange);
                case "POST /api/registration" -> enroll(exchange);
                case "GET /api/grades" -> grades(exchange);
                case "POST /api/grades" -> saveMark(exchange);
                case "GET /api/transcript" -> transcript(exchange);
                case "GET /api/attendance" -> attendance(exchange);
                case "POST /api/attendance" -> markAttendance(exchange);
                case "GET /api/library" -> library(exchange);
                case "POST /api/library/borrow" -> borrow(exchange);
                case "POST /api/library/return" -> returnLoan(exchange);
                case "GET /api/messages" -> messages(exchange);
                case "GET /api/tasks" -> tasks(exchange);
                case "POST /api/tasks" -> addTask(exchange);
                case "POST /api/tasks/done" -> setTaskDone(exchange);
                case "POST /api/messages" -> sendMessage(exchange);
                default -> error(exchange, 404, "Not found");
            }
        } catch (UnauthorizedException unauthorized) {
            error(exchange, 401, "Please sign in");
        } catch (SecurityException forbidden) {
            error(exchange, 403, forbidden.getMessage() == null ? "Not allowed" : forbidden.getMessage());
        } catch (IllegalArgumentException badRequest) {
            error(exchange, 400, badRequest.getMessage() == null ? "Bad request" : badRequest.getMessage());
        } catch (Exception failure) {
            System.err.println("Request failed: " + method + " " + path + " -> " + failure);
            error(exchange, 500, "Server error");
        } finally {
            exchange.close();
        }
    }

    // ─── Authentication ──────────────────────────────────────

    private void login(HttpExchange exchange) throws Exception {
        Map<?, ?> body = readObject(exchange);
        String username = text(body.get("username"));
        String password = text(body.get("password"));
        if (username.isEmpty() || password.isEmpty()) {
            error(exchange, 400, "Username and password are required");
            return;
        }

        Optional<Account> account = db.login(username, password);
        if (account.isEmpty()) {
            error(exchange, 401, "Invalid username or password");
            return;
        }

        String token = UUID.randomUUID().toString();
        sessions.put(token, account.get().id());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("user", userJson(account.get()));
        send(exchange, 200, payload);
    }

    private void logout(HttpExchange exchange) throws IOException {
        String token = bearerToken(exchange);
        if (token != null) {
            sessions.remove(token);
        }
        send(exchange, 204, null);
    }

    private void me(HttpExchange exchange) throws Exception {
        send(exchange, 200, userJson(require(exchange)));
    }

    // ─── Schedule ────────────────────────────────────────────

    private void schedule(HttpExchange exchange) throws Exception {
        Account user = require(exchange);

        List<Map<String, Object>> courses = new ArrayList<>();
        for (CourseRow course : db.courses(user)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", course.code());
            item.put("title", course.title());
            item.put("credits", course.credits());
            item.put("instructor", course.instructor());
            item.put("score", course.score());
            item.put("grade", course.grade());
            courses.add(item);
        }

        List<Map<String, Object>> sessions = new ArrayList<>();
        for (SessionRow session : db.sessions(user)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", session.code());
            item.put("title", session.title());
            item.put("kind", session.kind());
            item.put("day", session.day());
            item.put("start", session.start());
            item.put("end", session.end());
            item.put("room", session.room());
            item.put("instructor", session.instructor());
            sessions.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user", userJson(user));
        payload.put("courses", courses);
        payload.put("sessions", sessions);
        send(exchange, 200, payload);
    }

    // ─── Registration ────────────────────────────────────────

    private void registration(HttpExchange exchange) throws Exception {
        Account user = require(exchange);
        requireRole(user, "STUDENT");
        send(exchange, 200, registrationPayload(user));
    }

    private void enroll(HttpExchange exchange) throws Exception {
        Account user = require(exchange);
        requireRole(user, "STUDENT");

        String code = text(readObject(exchange).get("code"));
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Course code is required");
        }
        String failure = db.enroll(user, code);
        if (failure != null) {
            error(exchange, 409, failure);
            return;
        }
        send(exchange, 200, registrationPayload(user));
    }

    private Map<String, Object> registrationPayload(Account user) throws Exception {
        StudentProfile profile = db.profile(user.id());
        List<Map<String, Object>> courses = new ArrayList<>();
        for (CourseOption option : db.options(user)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", option.code());
            item.put("title", option.title());
            item.put("credits", option.credits());
            item.put("instructor", option.instructor());
            item.put("targetMajor", option.targetMajor());
            item.put("targetYear", option.targetYear());
            item.put("enrolled", option.enrolled());
            item.put("eligible", option.eligible());
            item.put("reason", option.reason());
            courses.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("credits", profile.credits());
        payload.put("limit", Db.CREDIT_LIMIT);
        payload.put("courses", courses);
        return payload;
    }

    // ─── Grades and transcript ───────────────────────────────

    private void grades(HttpExchange exchange) throws Exception {
        Account user = require(exchange);
        Map<String, Object> payload = new LinkedHashMap<>();

        if ("TEACHER".equals(user.role())) {
            List<Map<String, Object>> courses = new ArrayList<>();
            for (TeacherCourse course : db.teacherGrades(user)) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("code", course.code());
                entry.put("title", course.title());

                List<Map<String, Object>> students = new ArrayList<>();
                for (StudentMark mark : course.students()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", mark.id());
                    item.put("fullName", mark.fullName());
                    item.put("att1", mark.att1());
                    item.put("att2", mark.att2());
                    item.put("finalScore", mark.finalScore());
                    item.put("total", mark.total());
                    item.put("letter", mark.letter());
                    students.add(item);
                }
                entry.put("students", students);
                courses.add(entry);
            }
            payload.put("role", "TEACHER");
            payload.put("courses", courses);
        } else if ("STUDENT".equals(user.role())) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (GradeRow row : db.studentGrades(user)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", row.code());
                item.put("title", row.title());
                item.put("credits", row.credits());
                item.put("att1", row.att1());
                item.put("att2", row.att2());
                item.put("finalScore", row.finalScore());
                item.put("total", row.total());
                item.put("letter", row.letter());
                rows.add(item);
            }
            payload.put("role", "STUDENT");
            payload.put("rows", rows);
        } else {
            throw new SecurityException("Grades are not available for your role");
        }

        send(exchange, 200, payload);
    }

    private void saveMark(HttpExchange exchange) throws Exception {
        Account user = require(exchange);
        requireRole(user, "TEACHER");

        Map<?, ?> body = readObject(exchange);
        String code = text(body.get("code"));
        String studentId = text(body.get("studentId"));
        if (code.isEmpty() || studentId.isEmpty()) {
            throw new IllegalArgumentException("Course and student are required");
        }
        db.setMark(user, code, studentId,
                number(body.get("att1")), number(body.get("att2")), number(body.get("finalScore")));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        send(exchange, 200, payload);
    }

    private void transcript(HttpExchange exchange) throws Exception {
        Account user = require(exchange);
        requireRole(user, "STUDENT");

        List<TranscriptEntry> entries = db.transcript(user);
        List<Map<String, Object>> rows = new ArrayList<>();
        int earned = 0;
        int gradedCredits = 0;
        double pointSum = 0;
        for (TranscriptEntry entry : entries) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", entry.code());
            item.put("title", entry.title());
            item.put("credits", entry.credits());
            item.put("letter", entry.letter());
            item.put("points", entry.points());
            item.put("passed", entry.passed());
            rows.add(item);

            gradedCredits += entry.credits();
            pointSum += entry.points() * entry.credits();
            if (entry.passed()) {
                earned += entry.credits();
            }
        }

        double gpa = gradedCredits == 0 ? 0 : Math.round(pointSum / gradedCredits * 100) / 100.0;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("credits", earned);
        payload.put("gpa", gpa);
        payload.put("entries", rows);
        send(exchange, 200, payload);
    }

    // ─── Attendance ──────────────────────────────────────────

    private void attendance(HttpExchange exchange) throws Exception {
        Account user = require(exchange);
        Map<String, Object> payload = new LinkedHashMap<>();

        if ("STUDENT".equals(user.role())) {
            List<Map<String, Object>> summary = new ArrayList<>();
            for (AttendanceSummary row : db.attendanceSummaries(user)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", row.code());
                item.put("title", row.title());
                item.put("present", row.present());
                item.put("total", row.total());
                summary.add(item);
            }
            payload.put("role", "STUDENT");
            payload.put("summary", summary);
        } else if ("TEACHER".equals(user.role())) {
            List<Map<String, Object>> sessions = new ArrayList<>();
            for (MarkableSession session : db.markableSessions(user)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", session.id());
                item.put("code", session.code());
                item.put("title", session.title());
                item.put("day", session.day());
                item.put("start", session.start());
                item.put("end", session.end());

                List<Map<String, Object>> students = new ArrayList<>();
                for (Contact contact : session.students()) {
                    Map<String, Object> student = new LinkedHashMap<>();
                    student.put("id", contact.id());
                    student.put("username", contact.username());
                    student.put("fullName", contact.fullName());
                    students.add(student);
                }
                item.put("students", students);
                sessions.add(item);
            }
            payload.put("role", "TEACHER");
            payload.put("sessions", sessions);
        } else {
            throw new SecurityException("Attendance is not available for your role");
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (AttendanceRecord record : db.attendanceRecords(user)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", record.code());
            item.put("student", record.student());
            item.put("date", record.date());
            item.put("status", record.status());
            records.add(item);
        }
        payload.put("records", records);
        send(exchange, 200, payload);
    }

    private void markAttendance(HttpExchange exchange) throws Exception {
        Account user = require(exchange);
        requireRole(user, "TEACHER");

        Map<?, ?> body = readObject(exchange);
        Double sessionId = number(body.get("sessionId"));
        String studentId = text(body.get("studentId"));
        String date = text(body.get("date"));
        String status = text(body.get("status")).toUpperCase();
        if (sessionId == null || studentId.isEmpty()) {
            throw new IllegalArgumentException("Session and student are required");
        }
        db.markAttendance(user, sessionId.longValue(), studentId, date, status);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        send(exchange, 200, payload);
    }

    // ─── Library ─────────────────────────────────────────────

    private void library(HttpExchange exchange) throws Exception {
        Account user = require(exchange);

        List<Map<String, Object>> books = new ArrayList<>();
        for (Book book : db.books()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", book.id());
            item.put("title", book.title());
            item.put("author", book.author());
            item.put("isbn", book.isbn());
            item.put("copies", book.copies());
            item.put("available", book.available());
            books.add(item);
        }

        List<Map<String, Object>> loans = new ArrayList<>();
        for (Loan loan : db.loans(user.id())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", loan.id());
            item.put("title", loan.title());
            item.put("borrowedAt", loan.borrowedAt());
            item.put("dueAt", loan.dueAt());
            item.put("returnedAt", loan.returnedAt());
            loans.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("books", books);
        payload.put("loans", loans);
        send(exchange, 200, payload);
    }

    private void borrow(HttpExchange exchange) throws Exception {
        Account user = require(exchange);

        Double bookId = number(readObject(exchange).get("bookId"));
        if (bookId == null) {
            throw new IllegalArgumentException("Book is required");
        }
        String failure = db.borrow(user.id(), bookId.longValue());
        if (failure != null) {
            error(exchange, 409, failure);
            return;
        }
        send(exchange, 200, libraryPayload(user));
    }

    private void returnLoan(HttpExchange exchange) throws Exception {
        Account user = require(exchange);

        Double loanId = number(readObject(exchange).get("loanId"));
        if (loanId == null) {
            throw new IllegalArgumentException("Loan is required");
        }
        if (!db.returnLoan(user.id(), loanId.longValue())) {
            error(exchange, 404, "Loan not found");
            return;
        }
        send(exchange, 200, libraryPayload(user));
    }

    private Map<String, Object> libraryPayload(Account user) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();

        List<Map<String, Object>> books = new ArrayList<>();
        for (Book book : db.books()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", book.id());
            item.put("title", book.title());
            item.put("author", book.author());
            item.put("isbn", book.isbn());
            item.put("copies", book.copies());
            item.put("available", book.available());
            books.add(item);
        }

        List<Map<String, Object>> loans = new ArrayList<>();
        for (Loan loan : db.loans(user.id())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", loan.id());
            item.put("title", loan.title());
            item.put("borrowedAt", loan.borrowedAt());
            item.put("dueAt", loan.dueAt());
            item.put("returnedAt", loan.returnedAt());
            loans.add(item);
        }

        payload.put("books", books);
        payload.put("loans", loans);
        return payload;
    }

    // ─── Messages ────────────────────────────────────────────

    private void messages(HttpExchange exchange) throws Exception {
        Account user = require(exchange);

        List<Map<String, Object>> inbox = new ArrayList<>();
        for (MessageRow row : db.inbox(user.id())) {
            inbox.add(messageJson(row));
        }

        List<Map<String, Object>> sent = new ArrayList<>();
        for (MessageRow row : db.sent(user.id())) {
            sent.add(messageJson(row));
        }

        List<Map<String, Object>> contacts = new ArrayList<>();
        for (Contact contact : db.contacts(user.id())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", contact.id());
            item.put("username", contact.username());
            item.put("fullName", contact.fullName());
            contacts.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inbox", inbox);
        payload.put("sent", sent);
        payload.put("contacts", contacts);
        send(exchange, 200, payload);
    }

    private void sendMessage(HttpExchange exchange) throws Exception {
        Account user = require(exchange);

        Map<?, ?> body = readObject(exchange);
        String recipient = text(body.get("recipient"));
        String messageBody = body.get("body") instanceof String string ? string : "";
        db.send(user.id(), recipient, messageBody);
        send(exchange, 200, messagePayload(user));
    }

    private Map<String, Object> messagePayload(Account user) throws Exception {
        List<Map<String, Object>> inbox = new ArrayList<>();
        for (MessageRow row : db.inbox(user.id())) {
            inbox.add(messageJson(row));
        }
        List<Map<String, Object>> sent = new ArrayList<>();
        for (MessageRow row : db.sent(user.id())) {
            sent.add(messageJson(row));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inbox", inbox);
        payload.put("sent", sent);
        return payload;
    }

    private static Map<String, Object> messageJson(MessageRow row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sender", row.sender());
        item.put("recipient", row.recipient());
        item.put("body", row.body());
        item.put("sentAt", row.sentAt());
        return item;
    }

    // ─── To-do list ──────────────────────────────────────────

    private void tasks(HttpExchange exchange) throws Exception {
        require(exchange);
        send(exchange, 200, tasksPayload());
    }

    private void addTask(HttpExchange exchange) throws Exception {
        require(exchange);

        Map<?, ?> body = readObject(exchange);
        db.addTask(text(body.get("title")), text(body.get("dueDate")),
                text(body.get("priority")).toUpperCase());
        send(exchange, 201, tasksPayload());
    }

    private void setTaskDone(HttpExchange exchange) throws Exception {
        require(exchange);

        Map<?, ?> body = readObject(exchange);
        Double id = number(body.get("id"));
        if (id == null || !(body.get("done") instanceof Boolean done)) {
            throw new IllegalArgumentException("Task and state are required");
        }
        if (!db.setTaskDone(id.longValue(), done)) {
            error(exchange, 404, "Task not found");
            return;
        }
        send(exchange, 200, tasksPayload());
    }

    private Map<String, Object> tasksPayload() throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TaskRow task : db.tasks()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", task.id());
            item.put("title", task.title());
            item.put("dueDate", task.dueDate());
            item.put("priority", task.priority());
            item.put("done", task.done());
            rows.add(item);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tasks", rows);
        return payload;
    }

    // ─── Helpers ─────────────────────────────────────────────

    private static Map<String, Object> userJson(Account account) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", account.id());
        user.put("username", account.username());
        user.put("fullName", account.fullName());
        user.put("role", account.role());
        user.put("program", account.program());
        user.put("year", account.year());
        user.put("gpa", account.gpa());
        user.put("department", account.department());
        user.put("title", account.title());
        return user;
    }

    private Account require(HttpExchange exchange) throws Exception {
        String token = bearerToken(exchange);
        if (token == null) {
            throw new UnauthorizedException();
        }
        String userId = sessions.get(token);
        if (userId == null) {
            throw new UnauthorizedException();
        }
        return db.findById(userId).orElseThrow(UnauthorizedException::new);
    }

    private static void requireRole(Account account, String role) {
        if (!role.equals(account.role())) {
            throw new SecurityException("This page is not available for your role");
        }
    }

    private static String bearerToken(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static Map<?, ?> readObject(HttpExchange exchange) throws IOException {
        Object parsed;
        try (InputStream in = exchange.getRequestBody()) {
            parsed = Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        if (parsed instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalArgumentException("Expected a JSON object");
    }

    private static String text(Object value) {
        return value instanceof String string ? string.trim() : "";
    }

    private static Double number(Object value) {
        return value instanceof Double doubleValue ? doubleValue : null;
    }

    private static void error(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", message);
        send(exchange, status, payload);
    }

    private static void send(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] body = payload == null ? new byte[0] : Json.write(payload).getBytes(StandardCharsets.UTF_8);

        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");

        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /** Missing or expired session. */
    private static final class UnauthorizedException extends RuntimeException {
        UnauthorizedException() {
            super("Please sign in");
        }
    }
}
