# University Management System

University management portal: a Java backend, a SQLite database and a single-page Angular front end.

Students sign in to a dashboard with their university to-do list, then see their timetable, register for
courses, and follow their grades, transcript, attendance, library loans and messages. Teachers see the courses they teach, enter marks and mark
attendance. Managers and administrators see every course.

```
backend/    Java sources: domain model, console UI, SQLite layer and the HTTP JSON API
frontend/   Angular application: sign-in page and the schedule page
```

## Requirements

- JDK 17 or newer
- Node.js 24.15 or newer with npm (Angular CLI 22 refuses older releases)
- No Maven or Gradle. `backend/build.sh` compiles with `javac` and downloads the SQLite JDBC driver
  into `backend/lib` on the first run.

## Run the backend

```bash
cd backend
./build.sh        # download the JDBC driver, compile into out/
./run-server.sh   # http://localhost:8080
```

`PORT` changes the port and `DB_PATH` the database file:

```bash
PORT=9090 DB_PATH=data/dev.db ./run-server.sh
```

The original console interface is still available and does not use the database:

```bash
./run-console.sh
```

## Run the front end

```bash
cd frontend
npm install       # first time only
npm start         # http://localhost:4200
```

The dev server forwards every `/api` request to `http://localhost:8080` (see `proxy.conf.json`), so
start the backend first.

## Demo accounts

The database is created and seeded automatically on the first backend start.

| Role      | Username | Password   |
| --------- | -------- | ---------- |
| Student   | student1 | stud123    |
| Student   | student2 | stud123    |
| Teacher   | teacher1 | teach123   |
| Teacher   | teacher2 | teach123   |
| Manager   | manager  | manager123 |
| Admin     | admin    | admin123   |

Passwords are stored as salted PBKDF2 hashes, never as plain text.

## HTTP API

| Method | Path                  | Who            | Description                                              |
| ------ | --------------------- | -------------- | -------------------------------------------------------- |
| POST   | `/api/auth/login`     | –              | `{username, password}` → `{token, user}`                  |
| POST   | `/api/auth/logout`    | Bearer         | Ends the session                                         |
| GET    | `/api/me`             | Bearer         | Current user                                             |
| GET    | `/api/schedule`       | Bearer         | `{user, courses, sessions}` for the current user          |
| GET    | `/api/registration`   | student        | Credits in use and every course with an eligibility flag  |
| POST   | `/api/registration`   | student        | `{code}` → registers the student, 409 with the reason     |
| GET    | `/api/grades`         | student/teacher| Student rows, or the teacher's courses with their students |
| POST   | `/api/grades`         | teacher        | `{code, studentId, att1, att2, finalScore}`               |
| GET    | `/api/transcript`     | student        | Graded courses, credits earned and GPA                    |
| GET    | `/api/attendance`     | student/teacher| Attendance summary, or the teacher's sessions to mark     |
| POST   | `/api/attendance`     | teacher        | `{sessionId, studentId, date, status}`                    |
| GET    | `/api/library`        | Bearer         | Books with availability and the user's loans              |
| POST   | `/api/library/borrow` | Bearer         | `{bookId}`                                                |
| POST   | `/api/library/return` | Bearer         | `{loanId}`                                                |
| GET    | `/api/messages`       | Bearer         | `{inbox, sent, contacts}`                                 |
| POST   | `/api/messages`       | Bearer         | `{recipient, body}`                                       |
| GET    | `/api/tasks`          | Bearer         | `{tasks}` — to-do list with deadline, priority and state  |
| POST   | `/api/tasks`          | Bearer         | `{title, dueDate, priority}` → adds a task                |
| POST   | `/api/tasks/done`     | Bearer         | `{id, done}` → ticks a task off or reopens it             |

Tokens are random session ids kept in backend memory, so restarting the server signs everybody out.

## Database

SQLite, one file at `backend/data/university.db`, created on first start and excluded from git.
Deleting the file resets the demo data.

| Table                | Contents                                                        |
| -------------------- | --------------------------------------------------------------- |
| `users`              | id, username, PBKDF2 password, name, gender, role               |
| `students`           | major, year of study, GPA, credits, failed courses              |
| `employees`          | salary, department, school, title, h-index                      |
| `courses`            | code, title, credits, target major and year                     |
| `course_instructors` | which teachers teach which course                               |
| `sessions`           | weekly timetable: course, type, day of week, start, end, room   |
| `enrollments`        | student ↔ course with attestation 1, attestation 2, final exam  |
| `attendance`         | student, session, date and PRESENT / ABSENT / LATE              |
| `library_books`      | title, author, ISBN, number of copies                          |
| `library_loans`      | book, borrower, borrowed / due / returned dates                |
| `messages`           | sender, recipient, text and timestamp                         |
| `tasks`              | to-do list: title, deadline, HIGH / MEDIUM / LOW priority, done |

Total score and letter grade follow the rules in `courses.Mark`.

## Layout

```
backend/src
  api/          HTTP server, routes, JSON helpers
  db/           SQLite schema, seed data, queries, password hashing
  auth/ users/ courses/ research/ management/ patterns/ …   domain model and console UI
frontend/src/app
  core/         auth, schedule and portal services, API error type, guards
  login/        sign-in page
  dashboard/    to-do list with deadlines and coloured priorities
  schedule/     today's classes, weekly timetable, enrolled courses
  register/     course registration
  grades/       own marks (student) or mark entry (teacher)
  transcript/   graded courses, credits and GPA
  attendance/   attendance summary (student) or marking (teacher)
  library/      books, borrowing and returns
  messages/     inbox, sent and compose
  topbar/       navigation, signed-in user, sign out
```

The web API is the SQLite-backed interface. The console application keeps its original in-memory
`DataStore` and remains a separate demo of the domain model.
