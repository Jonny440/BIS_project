package db;

import courses.Mark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite persistence for the web API: schema creation, demo seed data and queries.
 * The database file is created (and seeded once) on first start.
 */
public final class Db {

    /** Public view of a signed-in user. */
    public record Account(String id, String username, String role, String fullName,
                          String program, Integer year, Double gpa,
                          String department, String title) {
    }

    /** A course the user is enrolled in or teaches. */
    public record CourseRow(String code, String title, int credits, String instructor,
                            Double score, String grade) {
    }

    /** One class in the weekly timetable. */
    public record SessionRow(String code, String title, String kind, int day,
                             String start, String end, String room, String instructor) {
    }

    /** A course as offered to a student on the registration page. */
    public record CourseOption(String code, String title, int credits, String instructor,
                               String targetMajor, int targetYear, boolean enrolled,
                               boolean eligible, String reason) {
    }

    /** Student study progress used by the registration rules. */
    public record StudentProfile(int credits, int failedCourses, String major, int year) {
    }

    /** One graded course of a student. */
    public record GradeRow(String code, String title, int credits, Double att1, Double att2,
                           Double finalScore, Double total, String letter) {
    }

    /** One student inside a course taught by the signed-in teacher. */
    public record StudentMark(String id, String fullName, Double att1, Double att2,
                              Double finalScore, Double total, String letter) {
    }

    /** A course taught by the signed-in teacher. */
    public record TeacherCourse(String code, String title, List<StudentMark> students) {
    }

    /** A course finished with a grade, as shown on the transcript. */
    public record TranscriptEntry(String code, String title, int credits, String letter,
                                  double points, boolean passed) {
    }

    /** Attendance counters for one course. */
    public record AttendanceSummary(String code, String title, int present, int total) {
    }

    /** A single attendance record. */
    public record AttendanceRecord(String code, String student, String date, String status) {
    }

    /** Timetable session with the students whose attendance can be marked. */
    public record MarkableSession(long id, String code, String title, int day, String start,
                                  String end, List<Contact> students) {
    }

    public record Contact(String id, String username, String fullName) {
    }

    public record Book(long id, String title, String author, String isbn, int copies, int available) {
    }

    public record Loan(long id, String title, String borrowedAt, String dueAt, String returnedAt) {
    }

    public record MessageRow(String sender, String recipient, String body, String sentAt) {
    }

    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS users (
                id         TEXT PRIMARY KEY,
                username   TEXT NOT NULL UNIQUE COLLATE NOCASE,
                password   TEXT NOT NULL,
                first_name TEXT NOT NULL,
                last_name  TEXT NOT NULL,
                gender     TEXT NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
                role       TEXT NOT NULL CHECK (role IN ('STUDENT', 'TEACHER', 'MANAGER', 'ADMIN'))
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS students (
                user_id        TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                major          TEXT NOT NULL,
                year_of_study  INTEGER NOT NULL CHECK (year_of_study BETWEEN 1 AND 6),
                gpa            REAL NOT NULL DEFAULT 0,
                credits        INTEGER NOT NULL DEFAULT 0,
                failed_courses INTEGER NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS employees (
                user_id    TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                salary     REAL NOT NULL DEFAULT 0,
                department TEXT NOT NULL,
                school     TEXT NOT NULL CHECK (school IN ('FIT', 'BS', 'ISE', 'SAM', 'KMA')),
                title      TEXT NOT NULL,
                h_index    INTEGER NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS courses (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                code         TEXT NOT NULL UNIQUE,
                title        TEXT NOT NULL,
                credits      INTEGER NOT NULL CHECK (credits > 0),
                target_major TEXT NOT NULL DEFAULT 'Any',
                target_year  INTEGER NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS course_instructors (
                course_id  INTEGER NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                teacher_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                PRIMARY KEY (course_id, teacher_id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS sessions (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                course_id   INTEGER NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                kind        TEXT NOT NULL CHECK (kind IN ('LECTURE', 'PRACTICE', 'LAB')),
                day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 5),
                start_time  TEXT NOT NULL,
                end_time    TEXT NOT NULL,
                room        TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS enrollments (
                student_id   TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                course_id    INTEGER NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
                attestation1 REAL,
                attestation2 REAL,
                final_score  REAL,
                PRIMARY KEY (student_id, course_id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS attendance (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
                date       TEXT NOT NULL,
                status     TEXT NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'LATE')),
                UNIQUE (student_id, session_id, date)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS library_books (
                id     INTEGER PRIMARY KEY AUTOINCREMENT,
                title  TEXT NOT NULL,
                author TEXT NOT NULL,
                isbn   TEXT NOT NULL UNIQUE,
                copies INTEGER NOT NULL CHECK (copies > 0)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS library_loans (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                book_id     INTEGER NOT NULL REFERENCES library_books(id) ON DELETE CASCADE,
                user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                borrowed_at TEXT NOT NULL,
                due_at      TEXT NOT NULL,
                returned_at TEXT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS messages (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                sender_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                recipient_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                body         TEXT NOT NULL,
                sent_at      TEXT NOT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_sessions_course ON sessions(course_id)",
            "CREATE INDEX IF NOT EXISTS idx_enrollments_student ON enrollments(student_id)",
            "CREATE INDEX IF NOT EXISTS idx_instructors_teacher ON course_instructors(teacher_id)",
            "CREATE INDEX IF NOT EXISTS idx_attendance_student ON attendance(student_id)",
            "CREATE INDEX IF NOT EXISTS idx_loans_user ON library_loans(user_id)",
            "CREATE INDEX IF NOT EXISTS idx_messages_recipient ON messages(recipient_id)",
    };

    /** Credits a student may take in one term. */
    public static final int CREDIT_LIMIT = 21;

    private static final String INSTRUCTORS_SQL = """
            (SELECT group_concat(u2.first_name || ' ' || u2.last_name, ', ')
             FROM course_instructors ci2
             JOIN users u2 ON u2.id = ci2.teacher_id
             WHERE ci2.course_id = c.id) AS instructor
            """;

    private static final String ACCOUNT_SQL = """
            SELECT u.id, u.username, u.password, u.role, u.first_name, u.last_name,
                   s.major, s.year_of_study, s.gpa, e.department, e.title
            FROM users u
            LEFT JOIN students s ON s.user_id = u.id
            LEFT JOIN employees e ON e.user_id = u.id
            """;

    private final String url;

    private Db(String path) {
        this.url = "jdbc:sqlite:" + path;
    }

    public static Db open(String path) {
        try {
            Path file = Path.of(path).toAbsolutePath();
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Class.forName("org.sqlite.JDBC");
            Db db = new Db(file.toString());
            db.migrate();
            db.seedIfEmpty();
            return db;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot open database at " + path, e);
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    // ─── Schema / seed ───────────────────────────────────────

    private void migrate() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String ddl : SCHEMA) {
                statement.execute(ddl);
            }
        }
    }

    private void seedIfEmpty() throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                if (count(connection, "users") == 0) {
                    insertSeed(connection);
                }
                if (count(connection, "attendance") == 0 && count(connection, "enrollments") > 0) {
                    insertAttendanceSeed(connection);
                }
                if (count(connection, "library_books") == 0) {
                    insertLibrarySeed(connection);
                }
                if (count(connection, "messages") == 0) {
                    insertMessageSeed(connection);
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static void insertSeed(Connection connection) throws SQLException {
        // Staff
        insertUser(connection, "A1", "admin", "admin123", "Алина", "Сарсен", "FEMALE", "ADMIN");
        insertEmployee(connection, "A1", 400_000, "Administration", "FIT", "ADMIN", 0);
        insertUser(connection, "M1", "manager", "manager123", "Аружан", "Бектур", "FEMALE", "MANAGER");
        insertEmployee(connection, "M1", 450_000, "Registrar", "FIT", "OR", 0);
        insertUser(connection, "T1", "teacher1", "teach123", "Диас", "Танирберген", "MALE", "TEACHER");
        insertEmployee(connection, "T1", 500_000, "Computer Science", "FIT", "PROFESSOR", 5);
        insertUser(connection, "T2", "teacher2", "teach123", "Мирас", "Оспанов", "MALE", "TEACHER");
        insertEmployee(connection, "T2", 480_000, "Mathematics", "FIT", "SENIOR_LECTURER", 2);

        // Students
        insertUser(connection, "S1", "student1", "stud123", "Нұржан", "Қалмурат", "MALE", "STUDENT");
        insertStudent(connection, "S1", "CS", 2, 3.5, 13, 0);
        insertUser(connection, "S2", "student2", "stud123", "Алия", "Серік", "FEMALE", "STUDENT");
        insertStudent(connection, "S2", "IS", 1, 3.9, 8, 0);

        // Courses
        insertCourse(connection, 1, "CSCI 205", "Object-Oriented Programming", 5, "CS", 2);
        insertCourse(connection, 2, "MATH 201", "Mathematics for Computing", 4, "Any", 0);
        insertCourse(connection, 3, "CSCI 310", "Database Systems", 4, "IS", 1);
        insertCourse(connection, 4, "CSCI 320", "Web Application Development", 4, "CS", 2);
        insertCourse(connection, 5, "CSCI 350", "Computer Networks", 4, "CS", 2);
        insertCourse(connection, 6, "CSCI 360", "Software Engineering", 3, "CS", 2);
        insertCourse(connection, 7, "IS 220", "Information Systems", 3, "IS", 1);

        // Who teaches what
        insertInstructor(connection, 1, "T1");
        insertInstructor(connection, 2, "T2");
        insertInstructor(connection, 3, "T2");
        insertInstructor(connection, 4, "T1");
        insertInstructor(connection, 5, "T1");
        insertInstructor(connection, 6, "T2");
        insertInstructor(connection, 7, "T2");

        // Weekly timetable
        insertSession(connection, 1, "LECTURE", 1, "09:00", "10:30", "B-204");
        insertSession(connection, 1, "LAB", 4, "14:00", "15:30", "L-3");
        insertSession(connection, 2, "LECTURE", 1, "11:00", "12:30", "A-105");
        insertSession(connection, 2, "PRACTICE", 3, "09:00", "10:30", "A-105");
        insertSession(connection, 3, "LECTURE", 2, "10:00", "11:30", "C-110");
        insertSession(connection, 3, "LAB", 4, "09:00", "10:30", "L-2");
        insertSession(connection, 4, "LECTURE", 2, "13:00", "14:30", "B-301");
        insertSession(connection, 4, "LAB", 5, "10:00", "12:00", "L-1");
        insertSession(connection, 5, "LECTURE", 3, "11:00", "12:30", "B-210");
        insertSession(connection, 5, "LAB", 5, "09:00", "10:30", "L-4");
        insertSession(connection, 6, "LECTURE", 2, "15:00", "16:30", "B-105");
        insertSession(connection, 7, "LECTURE", 1, "13:00", "14:30", "C-205");

        // Enrollments and marks
        insertEnrollment(connection, "S1", 1, 25.0, 22.0, 35.0);
        insertEnrollment(connection, "S1", 2, 28.0, 27.0, 32.0);
        insertEnrollment(connection, "S1", 4, null, null, null);
        insertEnrollment(connection, "S2", 2, 30.0, 29.0, 38.0);
        insertEnrollment(connection, "S2", 3, 20.0, 24.0, 31.0);
    }

    private static void insertUser(Connection connection, String id, String username, String password,
                                   String firstName, String lastName, String gender, String role)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (id, username, password, first_name, last_name, gender, role)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, username);
            ps.setString(3, Passwords.hash(password));
            ps.setString(4, firstName);
            ps.setString(5, lastName);
            ps.setString(6, gender);
            ps.setString(7, role);
            ps.executeUpdate();
        }
    }

    private static void insertStudent(Connection connection, String userId, String major, int year,
                                      double gpa, int credits, int failed) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO students (user_id, major, year_of_study, gpa, credits, failed_courses)"
                        + " VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, userId);
            ps.setString(2, major);
            ps.setInt(3, year);
            ps.setDouble(4, gpa);
            ps.setInt(5, credits);
            ps.setInt(6, failed);
            ps.executeUpdate();
        }
    }

    private static void insertEmployee(Connection connection, String userId, double salary, String department,
                                       String school, String title, int hIndex) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO employees (user_id, salary, department, school, title, h_index)"
                        + " VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, userId);
            ps.setDouble(2, salary);
            ps.setString(3, department);
            ps.setString(4, school);
            ps.setString(5, title);
            ps.setInt(6, hIndex);
            ps.executeUpdate();
        }
    }

    private static void insertCourse(Connection connection, int id, String code, String title, int credits,
                                     String targetMajor, int targetYear) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO courses (id, code, title, credits, target_major, target_year)"
                        + " VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, code);
            ps.setString(3, title);
            ps.setInt(4, credits);
            ps.setString(5, targetMajor);
            ps.setInt(6, targetYear);
            ps.executeUpdate();
        }
    }

    private static void insertInstructor(Connection connection, int courseId, String teacherId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO course_instructors (course_id, teacher_id) VALUES (?, ?)")) {
            ps.setInt(1, courseId);
            ps.setString(2, teacherId);
            ps.executeUpdate();
        }
    }

    private static void insertSession(Connection connection, int courseId, String kind, int day,
                                      String start, String end, String room) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sessions (course_id, kind, day_of_week, start_time, end_time, room)"
                        + " VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, courseId);
            ps.setString(2, kind);
            ps.setInt(3, day);
            ps.setString(4, start);
            ps.setString(5, end);
            ps.setString(6, room);
            ps.executeUpdate();
        }
    }

    private static void insertEnrollment(Connection connection, String studentId, int courseId,
                                         Double att1, Double att2, Double finalScore) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO enrollments (student_id, course_id, attestation1, attestation2, final_score)"
                        + " VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, studentId);
            ps.setInt(2, courseId);
            setNullable(ps, 3, att1);
            setNullable(ps, 4, att2);
            setNullable(ps, 5, finalScore);
            ps.executeUpdate();
        }
    }

    private static void setNullable(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.REAL);
        } else {
            ps.setDouble(index, value);
        }
    }

    // ─── Seed for attendance, library and messages ───────────

    private static void insertAttendanceSeed(Connection connection) throws SQLException {
        List<Long> sessionIds = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT s.id FROM sessions s"
                        + " JOIN enrollments e ON e.course_id = s.course_id AND e.student_id = ?"
                        + " ORDER BY s.id")) {
            ps.setString(1, userId(connection, "student1"));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sessionIds.add(rs.getLong(1));
                }
            }
        }

        String studentId = userId(connection, "student1");
        int index = 0;
        for (long sessionId : sessionIds) {
            insertAttendance(connection, studentId, sessionId, "2026-11-02", "PRESENT");
            insertAttendance(connection, studentId, sessionId, "2026-11-09",
                    index % 5 == 0 ? "ABSENT" : "PRESENT");
            index++;
        }
    }

    private static void insertLibrarySeed(Connection connection) throws SQLException {
        insertBook(connection, "Clean Code", "Robert C. Martin", "9780132350884", 4);
        insertBook(connection, "Introduction to Algorithms", "Cormen, Leiserson, Rivest, Stein",
                "9780262046305", 3);
        insertBook(connection, "Database System Concepts", "Silberschatz, Korth, Sudarshan",
                "9780078022159", 2);
        insertBook(connection, "Design Patterns", "Gamma, Helm, Johnson, Vlissides", "9780201633610", 2);
        insertBook(connection, "The Pragmatic Programmer", "Hunt & Thomas", "9780135957059", 3);

        insertLoan(connection, bookId(connection, "9780132350884"), userId(connection, "student1"),
                "2026-11-03", "2026-11-17", null);
        insertLoan(connection, bookId(connection, "9780262046305"), userId(connection, "student1"),
                "2026-09-20", "2026-10-04", "2026-10-02");
    }

    private static void insertMessageSeed(Connection connection) throws SQLException {
        insertMessage(connection, userId(connection, "teacher1"), userId(connection, "student1"),
                "Welcome to the course. The syllabus is on the portal.", "2026-11-05 09:15");
        insertMessage(connection, userId(connection, "student1"), userId(connection, "teacher2"),
                "Good morning, is Thursday's lab still at 09:00?", "2026-11-06 18:40");
        insertMessage(connection, userId(connection, "manager"), userId(connection, "student1"),
                "Registration for the spring term opens on 1 December.", "2026-11-10 12:00");
    }

    private static String userId(Connection connection, String username) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Missing seed user " + username);
                }
                return rs.getString(1);
            }
        }
    }

    private static void insertAttendance(Connection connection, String studentId, long sessionId,
                                        String date, String status) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO attendance (student_id, session_id, date, status)"
                        + " VALUES (?, ?, ?, ?)")) {
            ps.setString(1, studentId);
            ps.setLong(2, sessionId);
            ps.setString(3, date);
            ps.setString(4, status);
            ps.executeUpdate();
        }
    }

    private static void insertBook(Connection connection, String title, String author, String isbn, int copies)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO library_books (title, author, isbn, copies) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, title);
            ps.setString(2, author);
            ps.setString(3, isbn);
            ps.setInt(4, copies);
            ps.executeUpdate();
        }
    }

    private static long bookId(Connection connection, String isbn) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM library_books WHERE isbn = ?")) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static void insertLoan(Connection connection, long bookId, String userId, String borrowedAt,
                                   String dueAt, String returnedAt) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO library_loans (book_id, user_id, borrowed_at, due_at, returned_at)"
                        + " VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, bookId);
            ps.setString(2, userId);
            ps.setString(3, borrowedAt);
            ps.setString(4, dueAt);
            ps.setString(5, returnedAt);
            ps.executeUpdate();
        }
    }

    private static void insertMessage(Connection connection, String senderId, String recipientId,
                                      String body, String sentAt) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO messages (sender_id, recipient_id, body, sent_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, senderId);
            ps.setString(2, recipientId);
            ps.setString(3, body);
            ps.setString(4, sentAt);
            ps.executeUpdate();
        }
    }

    // ─── Queries ─────────────────────────────────────────────

    public Optional<Account> login(String username, String password) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement(ACCOUNT_SQL + " WHERE u.username = ?")) {
            ps.setString(1, username == null ? "" : username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Passwords.verify(password, rs.getString("password"))
                        ? Optional.of(readAccount(rs))
                        : Optional.empty();
            }
        }
    }

    public Optional<Account> findById(String userId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement(ACCOUNT_SQL + " WHERE u.id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readAccount(rs)) : Optional.empty();
            }
        }
    }

    public List<CourseRow> courses(Account account) throws SQLException {
        String sql = switch (account.role()) {
            case "STUDENT" -> "SELECT c.code, c.title, c.credits, " + INSTRUCTORS_SQL
                    + ", e.attestation1, e.attestation2, e.final_score"
                    + " FROM courses c JOIN enrollments e ON e.course_id = c.id"
                    + " WHERE e.student_id = ? ORDER BY c.code";
            case "TEACHER" -> "SELECT c.code, c.title, c.credits, " + INSTRUCTORS_SQL
                    + ", NULL AS attestation1, NULL AS attestation2, NULL AS final_score"
                    + " FROM courses c JOIN course_instructors ci ON ci.course_id = c.id"
                    + " WHERE ci.teacher_id = ? ORDER BY c.code";
            default -> "SELECT c.code, c.title, c.credits, " + INSTRUCTORS_SQL
                    + ", NULL AS attestation1, NULL AS attestation2, NULL AS final_score"
                    + " FROM courses c ORDER BY c.code";
        };

        List<CourseRow> rows = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            if (sql.contains("= ?")) {
                ps.setString(1, account.id());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Double att1 = nullableDouble(rs, "attestation1");
                    Double att2 = nullableDouble(rs, "attestation2");
                    Double finalScore = nullableDouble(rs, "final_score");
                    boolean scored = att1 != null && att2 != null && finalScore != null;
                    rows.add(new CourseRow(
                            rs.getString("code"),
                            rs.getString("title"),
                            rs.getInt("credits"),
                            rs.getString("instructor"),
                            scored ? att1 + att2 + finalScore : null,
                            scored ? new Mark(att1, att2, finalScore).getLetterGrade() : null));
                }
            }
        }
        return rows;
    }

    public List<SessionRow> sessions(Account account) throws SQLException {
        String filter = switch (account.role()) {
            case "STUDENT" -> " WHERE c.id IN (SELECT course_id FROM enrollments WHERE student_id = ?)";
            case "TEACHER" -> " WHERE c.id IN (SELECT course_id FROM course_instructors WHERE teacher_id = ?)";
            default -> "";
        };
        String sql = "SELECT c.code, c.title, s.kind, s.day_of_week, s.start_time, s.end_time, s.room, "
                + INSTRUCTORS_SQL
                + " FROM sessions s JOIN courses c ON c.id = s.course_id"
                + filter
                + " ORDER BY s.day_of_week, s.start_time";

        List<SessionRow> rows = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            if (!filter.isEmpty()) {
                ps.setString(1, account.id());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SessionRow(
                            rs.getString("code"),
                            rs.getString("title"),
                            rs.getString("kind"),
                            rs.getInt("day_of_week"),
                            rs.getString("start_time"),
                            rs.getString("end_time"),
                            rs.getString("room"),
                            rs.getString("instructor")));
                }
            }
        }
        return rows;
    }

    // ─── Registration ────────────────────────────────────────

    public StudentProfile profile(String userId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT credits, failed_courses, major, year_of_study FROM students WHERE user_id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new StudentProfile(0, 0, "", 0);
                }
                return new StudentProfile(rs.getInt("credits"), rs.getInt("failed_courses"),
                        rs.getString("major"), rs.getInt("year_of_study"));
            }
        }
    }

    public List<CourseOption> options(Account account) throws SQLException {
        StudentProfile profile = profile(account.id());
        String sql = "SELECT c.code, c.title, c.credits, c.target_major, c.target_year, " + INSTRUCTORS_SQL
                + ", EXISTS (SELECT 1 FROM enrollments e WHERE e.course_id = c.id AND e.student_id = ?)"
                + " AS enrolled FROM courses c ORDER BY c.code";

        List<CourseOption> options = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, account.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean enrolled = rs.getInt("enrolled") != 0;
                    String targetMajor = rs.getString("target_major");
                    int targetYear = rs.getInt("target_year");
                    int credits = rs.getInt("credits");

                    String reason = null;
                    if (enrolled) {
                        reason = "Already registered";
                    } else if (profile.credits() + credits > CREDIT_LIMIT) {
                        reason = "Credit limit is " + CREDIT_LIMIT;
                    } else if (profile.failedCourses() > 3) {
                        reason = "Too many failed courses";
                    } else if (!"Any".equals(targetMajor) && !targetMajor.equals(profile.major())) {
                        reason = "Only for " + targetMajor;
                    } else if (targetYear != 0 && targetYear != profile.year()) {
                        reason = "Only for year " + targetYear;
                    }

                    options.add(new CourseOption(rs.getString("code"), rs.getString("title"), credits,
                            rs.getString("instructor"), targetMajor, targetYear,
                            enrolled, reason == null, reason));
                }
            }
        }
        return options;
    }

    /** Registers the student, or returns the reason the course cannot be taken. */
    public String enroll(Account account, String code) throws SQLException {
        StudentProfile profile = profile(account.id());
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                int courseId = 0;
                int credits = 0;
                String targetMajor = "Any";
                int targetYear = 0;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id, credits, target_major, target_year FROM courses WHERE code = ?")) {
                    ps.setString(1, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            courseId = rs.getInt("id");
                            credits = rs.getInt("credits");
                            targetMajor = rs.getString("target_major");
                            targetYear = rs.getInt("target_year");
                        }
                    }
                }
                if (courseId == 0) {
                    connection.rollback();
                    return "Course not found";
                }

                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT 1 FROM enrollments WHERE student_id = ? AND course_id = ?")) {
                    ps.setString(1, account.id());
                    ps.setInt(2, courseId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            connection.rollback();
                            return "Already registered";
                        }
                    }
                }

                if (profile.credits() + credits > CREDIT_LIMIT) {
                    connection.rollback();
                    return "Credit limit is " + CREDIT_LIMIT;
                }
                if (profile.failedCourses() > 3) {
                    connection.rollback();
                    return "Too many failed courses";
                }
                if (!"Any".equals(targetMajor) && !targetMajor.equals(profile.major())) {
                    connection.rollback();
                    return "Only for " + targetMajor;
                }
                if (targetYear != 0 && targetYear != profile.year()) {
                    connection.rollback();
                    return "Only for year " + targetYear;
                }

                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO enrollments (student_id, course_id) VALUES (?, ?)")) {
                    ps.setString(1, account.id());
                    ps.setInt(2, courseId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE students SET credits = credits + ? WHERE user_id = ?")) {
                    ps.setInt(1, credits);
                    ps.setString(2, account.id());
                    ps.executeUpdate();
                }
                connection.commit();
                return null;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    // ─── Grades and transcript ───────────────────────────────

    public List<GradeRow> studentGrades(Account account) throws SQLException {
        String sql = "SELECT c.code, c.title, c.credits, e.attestation1, e.attestation2, e.final_score"
                + " FROM enrollments e JOIN courses c ON c.id = e.course_id"
                + " WHERE e.student_id = ? ORDER BY c.code";

        List<GradeRow> rows = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, account.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Double att1 = nullableDouble(rs, "attestation1");
                    Double att2 = nullableDouble(rs, "attestation2");
                    Double finalScore = nullableDouble(rs, "final_score");
                    rows.add(new GradeRow(rs.getString("code"), rs.getString("title"),
                            rs.getInt("credits"), att1, att2, finalScore,
                            totalOf(att1, att2, finalScore), letterOf(att1, att2, finalScore)));
                }
            }
        }
        return rows;
    }

    public List<TeacherCourse> teacherGrades(Account account) throws SQLException {
        String sql = "SELECT c.code, c.title, u.id AS student_id, u.first_name, u.last_name,"
                + " e.attestation1, e.attestation2, e.final_score"
                + " FROM courses c"
                + " JOIN course_instructors ci ON ci.course_id = c.id AND ci.teacher_id = ?"
                + " LEFT JOIN enrollments e ON e.course_id = c.id"
                + " LEFT JOIN users u ON u.id = e.student_id"
                + " ORDER BY c.code, u.last_name";

        Map<String, String> titles = new LinkedHashMap<>();
        Map<String, List<StudentMark>> students = new LinkedHashMap<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, account.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("code");
                    titles.putIfAbsent(code, rs.getString("title"));
                    List<StudentMark> list = students.computeIfAbsent(code, key -> new ArrayList<>());

                    String studentId = rs.getString("student_id");
                    if (studentId == null) {
                        continue;
                    }
                    Double att1 = nullableDouble(rs, "attestation1");
                    Double att2 = nullableDouble(rs, "attestation2");
                    Double finalScore = nullableDouble(rs, "final_score");
                    list.add(new StudentMark(studentId,
                            (rs.getString("first_name") + " " + rs.getString("last_name")).trim(),
                            att1, att2, finalScore, totalOf(att1, att2, finalScore),
                            letterOf(att1, att2, finalScore)));
                }
            }
        }

        List<TeacherCourse> courses = new ArrayList<>();
        for (Map.Entry<String, List<StudentMark>> entry : students.entrySet()) {
            courses.add(new TeacherCourse(entry.getKey(), titles.get(entry.getKey()), entry.getValue()));
        }
        return courses;
    }

    public void setMark(Account teacher, String code, String studentId, Double att1, Double att2,
                        Double finalScore) throws SQLException {
        if (att1 == null || att2 == null || finalScore == null) {
            throw new IllegalArgumentException("All three marks are required");
        }
        if (att1 < 0 || att1 > 30 || att2 < 0 || att2 > 30 || finalScore < 0 || finalScore > 40) {
            throw new IllegalArgumentException("Attestations are 0-30 and the exam is 0-40");
        }

        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                int courseId = teacherCourseId(connection, teacher.id(), code);
                if (courseId == 0) {
                    throw new SecurityException("You do not teach " + code);
                }
                if (!isEnrolled(connection, studentId, courseId)) {
                    throw new IllegalArgumentException("Student is not registered to " + code);
                }

                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE enrollments SET attestation1 = ?, attestation2 = ?, final_score = ?"
                                + " WHERE student_id = ? AND course_id = ?")) {
                    ps.setDouble(1, att1);
                    ps.setDouble(2, att2);
                    ps.setDouble(3, finalScore);
                    ps.setString(4, studentId);
                    ps.setInt(5, courseId);
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public List<TranscriptEntry> transcript(Account account) throws SQLException {
        List<TranscriptEntry> entries = new ArrayList<>();
        for (GradeRow row : studentGrades(account)) {
            if (row.letter() == null) {
                continue;
            }
            entries.add(new TranscriptEntry(row.code(), row.title(), row.credits(), row.letter(),
                    pointsFor(row.letter()), row.total() >= 50));
        }
        return entries;
    }

    public static double pointsFor(String letter) {
        return switch (letter) {
            case "A" -> 4.0;
            case "A-" -> 3.67;
            case "B+" -> 3.33;
            case "B" -> 3.0;
            case "B-" -> 2.67;
            case "C+" -> 2.33;
            case "C" -> 2.0;
            case "C-" -> 1.67;
            case "D+" -> 1.33;
            case "D" -> 1.0;
            default -> 0.0;
        };
    }

    // ─── Attendance ──────────────────────────────────────────

    public List<AttendanceSummary> attendanceSummaries(Account account) throws SQLException {
        String sql = "SELECT c.code, c.title, COUNT(a.id) AS total,"
                + " COALESCE(SUM(CASE WHEN a.status IN ('PRESENT', 'LATE') THEN 1 ELSE 0 END), 0) AS present"
                + " FROM enrollments e"
                + " JOIN courses c ON c.id = e.course_id"
                + " LEFT JOIN attendance a ON a.student_id = e.student_id"
                + "   AND a.session_id IN (SELECT id FROM sessions WHERE course_id = c.id)"
                + " WHERE e.student_id = ?"
                + " GROUP BY c.id ORDER BY c.code";

        List<AttendanceSummary> rows = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, account.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new AttendanceSummary(rs.getString("code"), rs.getString("title"),
                            rs.getInt("present"), rs.getInt("total")));
                }
            }
        }
        return rows;
    }

    public List<AttendanceRecord> attendanceRecords(Account account) throws SQLException {
        boolean teacher = "TEACHER".equals(account.role());
        String sql = "SELECT c.code, u.first_name || ' ' || u.last_name AS student, a.date, a.status"
                + " FROM attendance a"
                + " JOIN sessions s ON s.id = a.session_id"
                + " JOIN courses c ON c.id = s.course_id"
                + " JOIN users u ON u.id = a.student_id"
                + (teacher
                ? " WHERE c.id IN (SELECT course_id FROM course_instructors WHERE teacher_id = ?)"
                : " WHERE a.student_id = ?")
                + " ORDER BY a.date DESC, c.code LIMIT 20";

        List<AttendanceRecord> rows = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, account.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new AttendanceRecord(rs.getString("code"), rs.getString("student"),
                            rs.getString("date"), rs.getString("status")));
                }
            }
        }
        return rows;
    }

    public List<MarkableSession> markableSessions(Account account) throws SQLException {
        List<MarkableSession> sessions = new ArrayList<>();
        try (Connection connection = connect()) {
            Map<Integer, List<Contact>> studentsByCourse = new HashMap<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT e.course_id, u.id, u.username, u.first_name, u.last_name"
                            + " FROM enrollments e JOIN users u ON u.id = e.student_id"
                            + " WHERE e.course_id IN"
                            + " (SELECT course_id FROM course_instructors WHERE teacher_id = ?)"
                            + " ORDER BY u.last_name")) {
                ps.setString(1, account.id());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        studentsByCourse.computeIfAbsent(rs.getInt("course_id"), key -> new ArrayList<>())
                                .add(new Contact(rs.getString("id"), rs.getString("username"),
                                        (rs.getString("first_name") + " "
                                                + rs.getString("last_name")).trim()));
                    }
                }
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT s.id, c.id AS course_id, c.code, c.title, s.day_of_week,"
                            + " s.start_time, s.end_time"
                            + " FROM sessions s JOIN courses c ON c.id = s.course_id"
                            + " WHERE c.id IN (SELECT course_id FROM course_instructors WHERE teacher_id = ?)"
                            + " ORDER BY s.day_of_week, s.start_time")) {
                ps.setString(1, account.id());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int courseId = rs.getInt("course_id");
                        sessions.add(new MarkableSession(rs.getLong("id"), rs.getString("code"),
                                rs.getString("title"), rs.getInt("day_of_week"),
                                rs.getString("start_time"), rs.getString("end_time"),
                                studentsByCourse.getOrDefault(courseId, List.of())));
                    }
                }
            }
        }
        return sessions;
    }

    public void markAttendance(Account teacher, long sessionId, String studentId, String date,
                               String status) throws SQLException {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Date must look like 2026-11-11");
        }
        if (!"PRESENT".equals(status) && !"ABSENT".equals(status) && !"LATE".equals(status)) {
            throw new IllegalArgumentException("Status must be PRESENT, ABSENT or LATE");
        }

        try (Connection connection = connect()) {
            int courseId = 0;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT c.id FROM sessions s JOIN courses c ON c.id = s.course_id"
                            + " JOIN course_instructors ci ON ci.course_id = c.id AND ci.teacher_id = ?"
                            + " WHERE s.id = ?")) {
                ps.setString(1, teacher.id());
                ps.setLong(2, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        courseId = rs.getInt(1);
                    }
                }
            }
            if (courseId == 0) {
                throw new SecurityException("This session is not yours");
            }
            if (!isEnrolled(connection, studentId, courseId)) {
                throw new IllegalArgumentException("Student is not registered to this course");
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO attendance (student_id, session_id, date, status) VALUES (?, ?, ?, ?)"
                            + " ON CONFLICT(student_id, session_id, date)"
                            + " DO UPDATE SET status = excluded.status")) {
                ps.setString(1, studentId);
                ps.setLong(2, sessionId);
                ps.setString(3, date);
                ps.setString(4, status);
                ps.executeUpdate();
            }
        }
    }

    // ─── Library ─────────────────────────────────────────────

    public List<Book> books() throws SQLException {
        String sql = "SELECT b.id, b.title, b.author, b.isbn, b.copies,"
                + " b.copies - (SELECT COUNT(*) FROM library_loans l"
                + "   WHERE l.book_id = b.id AND l.returned_at IS NULL) AS available"
                + " FROM library_books b ORDER BY b.title";

        List<Book> rows = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new Book(rs.getLong("id"), rs.getString("title"), rs.getString("author"),
                        rs.getString("isbn"), rs.getInt("copies"), rs.getInt("available")));
            }
        }
        return rows;
    }

    public List<Loan> loans(String userId) throws SQLException {
        String sql = "SELECT l.id, b.title, l.borrowed_at, l.due_at, l.returned_at"
                + " FROM library_loans l JOIN library_books b ON b.id = l.book_id"
                + " WHERE l.user_id = ?"
                + " ORDER BY l.returned_at IS NOT NULL, l.borrowed_at DESC";

        List<Loan> rows = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Loan(rs.getLong("id"), rs.getString("title"),
                            rs.getString("borrowed_at"), rs.getString("due_at"),
                            rs.getString("returned_at")));
                }
            }
        }
        return rows;
    }

    /** Borrows a book, or returns the reason it cannot be borrowed. */
    public String borrow(String userId, long bookId) throws SQLException {
        try (Connection connection = connect()) {
            int available = -1;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT b.copies - (SELECT COUNT(*) FROM library_loans l"
                            + " WHERE l.book_id = b.id AND l.returned_at IS NULL) AS available"
                            + " FROM library_books b WHERE b.id = ?")) {
                ps.setLong(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        available = rs.getInt(1);
                    }
                }
            }
            if (available < 0) {
                return "Book not found";
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM library_loans"
                            + " WHERE book_id = ? AND user_id = ? AND returned_at IS NULL")) {
                ps.setLong(1, bookId);
                ps.setString(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return "You already have this book";
                    }
                }
            }
            if (available <= 0) {
                return "No copies available";
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO library_loans (book_id, user_id, borrowed_at, due_at)"
                            + " VALUES (?, ?, date('now'), date('now', '+14 day'))")) {
                ps.setLong(1, bookId);
                ps.setString(2, userId);
                ps.executeUpdate();
            }
            return null;
        }
    }

    public boolean returnLoan(String userId, long loanId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE library_loans SET returned_at = date('now')"
                             + " WHERE id = ? AND user_id = ? AND returned_at IS NULL")) {
            ps.setLong(1, loanId);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    // ─── Messages ────────────────────────────────────────────

    public List<MessageRow> inbox(String userId) throws SQLException {
        return messages(userId, true);
    }

    public List<MessageRow> sent(String userId) throws SQLException {
        return messages(userId, false);
    }

    private List<MessageRow> messages(String userId, boolean inbox) throws SQLException {
        String sql = "SELECT s.username AS sender, r.username AS recipient, m.body, m.sent_at"
                + " FROM messages m"
                + " JOIN users s ON s.id = m.sender_id"
                + " JOIN users r ON r.id = m.recipient_id"
                + (inbox ? " WHERE m.recipient_id = ?" : " WHERE m.sender_id = ?")
                + " ORDER BY m.sent_at DESC, m.id DESC";

        List<MessageRow> rows = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new MessageRow(rs.getString("sender"), rs.getString("recipient"),
                            rs.getString("body"), rs.getString("sent_at")));
                }
            }
        }
        return rows;
    }

    public List<Contact> contacts(String userId) throws SQLException {
        String sql = "SELECT id, username, first_name, last_name FROM users WHERE id <> ?"
                + " ORDER BY last_name, first_name";

        List<Contact> rows = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Contact(rs.getString("id"), rs.getString("username"),
                            (rs.getString("first_name") + " " + rs.getString("last_name")).trim()));
                }
            }
        }
        return rows;
    }

    public void send(String senderId, String recipientUsername, String body) throws SQLException {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Message is empty");
        }
        if (body.length() > 2000) {
            throw new IllegalArgumentException("Message is too long");
        }

        try (Connection connection = connect()) {
            String recipientId = null;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id FROM users WHERE username = ?")) {
                ps.setString(1, recipientUsername);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        recipientId = rs.getString(1);
                    }
                }
            }
            if (recipientId == null) {
                throw new IllegalArgumentException("Unknown recipient");
            }
            if (recipientId.equals(senderId)) {
                throw new IllegalArgumentException("You cannot message yourself");
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO messages (sender_id, recipient_id, body, sent_at)"
                            + " VALUES (?, ?, ?, datetime('now'))")) {
                ps.setString(1, senderId);
                ps.setString(2, recipientId);
                ps.setString(3, body.trim());
                ps.executeUpdate();
            }
        }
    }

    // ─── Shared helpers ──────────────────────────────────────

    private static int teacherCourseId(Connection connection, String teacherId, String code)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT c.id FROM courses c"
                        + " JOIN course_instructors ci ON ci.course_id = c.id AND ci.teacher_id = ?"
                        + " WHERE c.code = ?")) {
            ps.setString(1, teacherId);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static boolean isEnrolled(Connection connection, String studentId, int courseId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM enrollments WHERE student_id = ? AND course_id = ?")) {
            ps.setString(1, studentId);
            ps.setInt(2, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Double totalOf(Double att1, Double att2, Double finalScore) {
        return att1 == null || att2 == null || finalScore == null ? null : att1 + att2 + finalScore;
    }

    private static String letterOf(Double att1, Double att2, Double finalScore) {
        return att1 == null || att2 == null || finalScore == null
                ? null
                : new Mark(att1, att2, finalScore).getLetterGrade();
    }

    private static Account readAccount(ResultSet rs) throws SQLException {
        int year = rs.getInt("year_of_study");
        Integer yearOrNull = rs.wasNull() ? null : year;
        double gpa = rs.getDouble("gpa");
        Double gpaOrNull = rs.wasNull() ? null : gpa;
        return new Account(
                rs.getString("id"),
                rs.getString("username"),
                rs.getString("role"),
                (rs.getString("first_name") + " " + rs.getString("last_name")).trim(),
                rs.getString("major"),
                yearOrNull,
                gpaOrNull,
                rs.getString("department"),
                rs.getString("title"));
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
