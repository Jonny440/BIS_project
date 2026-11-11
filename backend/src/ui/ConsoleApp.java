package ui;

import auth.AuthService;
import courses.*;
import database.DataStore;
import enums.*;
import exceptions.InvalidSupervisorException;
import management.*;
import patterns.*;
import research.*;
import research.comparators.*;
import users.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * ConsoleApp — главный интерактивный консольный интерфейс системы UMS.
 *
 * Следует совету преподавателя: "новая команда должна легко расширить код".
 * Каждое меню — отдельный метод. Добавить новую роль = добавить метод + строку в routeMenu().
 * ReportStrategy позволяет добавить новый отчёт без изменения ConsoleApp.
 */
public class ConsoleApp {

    private final Scanner sc = new Scanner(System.in);
    private final DataStore store = DataStore.getInstance();
    private final AuthService auth = new AuthService();
    private final AdminService adminService = new AdminService();
    private User currentUser;

    // ═══════════════════════════════════════════════════════
    //  СТАРТ
    // ═══════════════════════════════════════════════════════

    public void run() {
        initSampleData();
        printBanner();
        while (true) {
            if (doLogin()) routeMenu();
        }
    }

    private void printBanner() {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════╗");
        System.out.println("║      UNIVERSITY MANAGEMENT SYSTEM v2.0        ║");
        System.out.println("║         Research-Oriented University           ║");
        System.out.println("╚═══════════════════════════════════════════════╝");
    }

    // ═══════════════════════════════════════════════════════
    //  АУТЕНТИФИКАЦИЯ
    // ═══════════════════════════════════════════════════════

    private boolean doLogin() {
        System.out.println("\n┌─ ВХОД В СИСТЕМУ ───────────────────────────────┐");
        System.out.print("│  Username: ");
        String username = sc.nextLine().trim();
        System.out.print("│  Password: ");
        String password = sc.nextLine().trim();
        System.out.println("└────────────────────────────────────────────────┘");

        currentUser = auth.login(username, password, store.getUsers());
        if (currentUser == null) {
            System.out.println("❌ Неверный логин или пароль. Попробуйте снова.\n");
            return false;
        }
        System.out.println("✅ Добро пожаловать, " + currentUser.getFullName()
                + " [" + currentUser.getClass().getSimpleName() + "]");
        return true;
    }

    private void routeMenu() {
        // Легко добавить новую роль — просто новый else if
        if (currentUser instanceof Admin)        adminMenu();
        else if (currentUser instanceof Manager) managerMenu((Manager) currentUser);
        else if (currentUser instanceof Teacher) teacherMenu((Teacher) currentUser);
        else if (currentUser instanceof Student) studentMenu((Student) currentUser);
        else System.out.println("Роль пользователя не определена.");
    }

    // ═══════════════════════════════════════════════════════
    //  МЕНЮ АДМИНИСТРАТОРА
    // ═══════════════════════════════════════════════════════

    private void adminMenu() {
        while (true) {
            System.out.println("\n╔══════════════════════════════════════╗");
            System.out.println("║          МЕНЮ АДМИНИСТРАТОРА         ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.println("║  Пользователи:                       ║");
            System.out.println("║   1. Добавить пользователя           ║");
            System.out.println("║   2. Удалить пользователя            ║");
            System.out.println("║   3. Обновить пользователя           ║");
            System.out.println("║   4. Список всех пользователей       ║");
            System.out.println("║   5. Студенты по GPA                 ║");
            System.out.println("║   6. Пользователи по алфавиту        ║");
            System.out.println("║   7. Преподаватели с рейтингом       ║");
            System.out.println("║  Курсы:                              ║");
            System.out.println("║   8. Создать курс                    ║");
            System.out.println("║   9. Список курсов                   ║");
            System.out.println("║  10. Журнал действий (логи)          ║");
            System.out.println("║   0. Выйти из аккаунта               ║");
            System.out.println("╚══════════════════════════════════════╝");
            System.out.print("Ваш выбор: ");

            switch (sc.nextLine().trim()) {
                case "1"  -> addUserWizard();
                case "2"  -> removeUserById();
                case "3"  -> updateUserMenu();
                case "4"  -> adminService.printUsers();
                case "5"  -> adminService.printStudentsByGpa();
                case "6"  -> adminService.printUsersSortedByName();
                case "7"  -> adminService.printTeachers();
                case "8"  -> createCourseWizard();
                case "9"  -> listCourses();
                case "10" -> adminService.printLogs();
                case "0"  -> { System.out.println("Выход из аккаунта."); return; }
                default   -> System.out.println("Неверный выбор.");
            }
        }
    }

    // ─── Мастер создания пользователя ───────────────────────

    private void addUserWizard() {
        System.out.println("\n─── Добавить пользователя ───");
        System.out.println("  1. Студент");
        System.out.println("  2. Преподаватель");
        System.out.println("  3. Менеджер");
        System.out.println("  4. Администратор");
        System.out.println("  5. Сотрудник-исследователь");
        System.out.print("Тип: ");
        String type = sc.nextLine().trim();

        // Общие поля
        System.out.print("ID: ");           String id       = sc.nextLine().trim();
        System.out.print("Username: ");     String username = sc.nextLine().trim();
        System.out.print("Пароль: ");       String password = sc.nextLine().trim();
        System.out.print("Имя: ");          String first    = sc.nextLine().trim();
        System.out.print("Фамилия: ");      String last     = sc.nextLine().trim();
        Gender gender = chooseGender();

        User user = switch (type) {
            case "1" -> buildStudent(id, username, password, first, last, gender);
            case "2" -> buildTeacher(id, username, password, first, last, gender);
            case "3" -> buildManager(id, username, password, first, last, gender);
            case "4" -> buildAdmin(id, username, password, first, last, gender);
            case "5" -> buildResearcherEmployee(id, username, password, first, last, gender);
            default  -> { System.out.println("Неверный тип."); yield null; }
        };

        if (user == null) return;
        store.addUser(user);
        adminService.addUser(user);
        System.out.println("✅ Пользователь " + user.getFullName()
                + " [" + user.getClass().getSimpleName() + "] добавлен!");
    }

    private Student buildStudent(String id, String un, String pw, String f, String l, Gender g) {
        System.out.print("Специальность: ");    String major = sc.nextLine().trim();
        System.out.print("Год обучения (1-4): "); int year   = readInt(1, 4, 1);
        System.out.print("GPA (0.0-4.0): ");    double gpa   = readDouble(0.0, 4.0, 0.0);
        return new Student(id, un, pw, f, l, g, major, year, gpa, 0);
    }

    private Teacher buildTeacher(String id, String un, String pw, String f, String l, Gender g) {
        System.out.print("Зарплата: ");         double salary = readDouble(0, 10_000_000, 400_000);
        System.out.print("Кафедра/Отдел: ");    String dept   = sc.nextLine().trim();
        SchoolType school   = chooseSchool();
        TeacherTitle title  = chooseTitle();
        Teacher t = new Teacher(id, un, pw, f, l, g, salary, dept, school, title);
        System.out.print("h-index (для исследований): "); t.setHIndex(readInt(0, 100, 0));
        return t;
    }

    private Manager buildManager(String id, String un, String pw, String f, String l, Gender g) {
        System.out.print("Зарплата: ");      double salary = readDouble(0, 10_000_000, 400_000);
        System.out.print("Отдел: ");         String dept   = sc.nextLine().trim();
        SchoolType school   = chooseSchool();
        ManagerType mtype   = chooseManagerType();
        return new Manager(id, un, pw, f, l, g, salary, dept, school, mtype);
    }

    private Admin buildAdmin(String id, String un, String pw, String f, String l, Gender g) {
        System.out.print("Зарплата: ");  double salary = readDouble(0, 10_000_000, 400_000);
        System.out.print("Отдел: ");     String dept   = sc.nextLine().trim();
        SchoolType school = chooseSchool();
        return new Admin(id, un, pw, f, l, g, salary, dept, school);
    }

    private ResearcherEmployee buildResearcherEmployee(String id, String un, String pw, String f, String l, Gender g) {
        System.out.print("Зарплата: ");  double salary = readDouble(0, 10_000_000, 400_000);
        System.out.print("Лаборатория: "); String dept = sc.nextLine().trim();
        SchoolType school = chooseSchool();
        ResearcherEmployee re = new ResearcherEmployee(id, un, pw, f, l, g, salary, dept, school);
        System.out.print("h-index: "); re.setHIndex(readInt(0, 100, 0));
        return re;
    }

    private void removeUserById() {
        System.out.print("ID пользователя для удаления: ");
        User user = findById(sc.nextLine().trim());
        if (user == null) { System.out.println("❌ Не найден."); return; }
        if (user.equals(currentUser)) { System.out.println("❌ Нельзя удалить себя."); return; }
        store.getUsers().remove(user);
        adminService.removeUser(user);
        System.out.println("✅ " + user.getFullName() + " удалён.");
    }

    private void updateUserMenu() {
        System.out.print("ID пользователя: ");
        User user = findById(sc.nextLine().trim());
        if (user == null) { System.out.println("❌ Не найден."); return; }
        System.out.println("Что обновить?  1. Имя  2. Пароль");
        System.out.print("Выбор: ");
        String c = sc.nextLine().trim();
        if ("1".equals(c)) {
            System.out.print("Новое имя: ");    String f = sc.nextLine().trim();
            System.out.print("Новая фамилия: "); String l = sc.nextLine().trim();
            adminService.updateUserName(user, f, l);
        } else if ("2".equals(c)) {
            System.out.print("Новый пароль: ");
            adminService.updateUserPassword(user, sc.nextLine().trim());
        }
    }

    // ─── Курсы (Admin) ───────────────────────────────────────

    private void createCourseWizard() {
        System.out.println("\n─── Создать курс ───");
        System.out.print("Название курса: ");        String name    = sc.nextLine().trim();
        System.out.print("Количество кредитов: ");   int credits    = readInt(1, 10, 3);
        System.out.print("Для специальности (или Enter = Any): ");
        String major = sc.nextLine().trim();
        if (major.isEmpty()) major = "Any";
        System.out.print("Для года (0 = любой): ");  int year       = readInt(0, 4, 0);

        Course course = new Course(name, credits, major, year);
        store.addCourse(course);
        adminService.printLogs(); // log auto-added in addUser — just for demo
        System.out.println("✅ Курс '" + name + "' (" + credits + " кредитов) создан!");
        System.out.println("   Специальность: " + major + " | Год: " + (year == 0 ? "Любой" : year));
    }

    private void listCourses() {
        System.out.println("\n═══ Список курсов ══════════════════════════════");
        if (store.getCourses().isEmpty()) { System.out.println("  Курсов нет."); return; }
        System.out.printf("  %-4s %-22s %-6s %-14s %-5s %-8s %-8s%n",
                "#", "Название", "Кред.", "Специальность", "Год", "Студ.", "Преп.");
        System.out.println("  " + "─".repeat(72));
        for (int i = 0; i < store.getCourses().size(); i++) {
            Course c = store.getCourses().get(i);
            System.out.printf("  %-4d %-22s %-6d %-14s %-5s %-8d %-8d%n",
                    i + 1, c.getName(), c.getCredits(), c.getTargetMajor(),
                    c.getTargetYear() == 0 ? "Любой" : String.valueOf(c.getTargetYear()),
                    c.getStudents().size(), c.getInstructors().size());
        }
    }

    // ═══════════════════════════════════════════════════════
    //  МЕНЮ МЕНЕДЖЕРА
    // ═══════════════════════════════════════════════════════

    private void managerMenu(Manager manager) {
        ManagerService ms = new ManagerService(manager);

        while (true) {
            System.out.println("\n╔══════════════════════════════════════╗");
            System.out.println("║           МЕНЮ МЕНЕДЖЕРА             ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.println("║  Управление:                         ║");
            System.out.println("║   1. Создать курс                    ║");
            System.out.println("║   2. Список курсов                   ║");
            System.out.println("║   3. Назначить препода на курс       ║");
            System.out.println("║   4. Записать студента на курс       ║");
            System.out.println("║  Просмотр:                           ║");
            System.out.println("║   5. Студенты по GPA                 ║");
            System.out.println("║   6. Преподаватели с рейтингом       ║");
            System.out.println("║   7. Все пользователи                ║");
            System.out.println("║  Коммуникация:                       ║");
            System.out.println("║   8. Опубликовать новость            ║");
            System.out.println("║   9. Просмотр заявок                 ║");
            System.out.println("║  10. Одобрить / отклонить заявку     ║");
            System.out.println("║  Отчёты (Strategy Pattern):          ║");
            System.out.println("║  11. Генерировать отчёт              ║");
            System.out.println("║   0. Выйти из аккаунта               ║");
            System.out.println("╚══════════════════════════════════════╝");
            System.out.print("Ваш выбор: ");

            switch (sc.nextLine().trim()) {
                case "1"  -> createCourseWizard();
                case "2"  -> listCourses();
                case "3"  -> assignTeacherMenu(ms);
                case "4"  -> registerStudentMenu(ms);
                case "5"  -> ms.printStudentsByGpa(store.getUsers());
                case "6"  -> ms.printTeachersWithRating(store.getUsers());
                case "7"  -> adminService.printUsers();
                case "8"  -> publishNewsMenu(ms);
                case "9"  -> ms.printRequests();
                case "10" -> manageRequestMenu(ms);
                case "11" -> generateReportMenu();
                case "0"  -> { System.out.println("Выход из аккаунта."); return; }
                default   -> System.out.println("Неверный выбор.");
            }
        }
    }

    private void assignTeacherMenu(ManagerService ms) {
        System.out.println("\n─── Назначить преподавателя на курс ───");
        Teacher teacher = pickTeacher();
        if (teacher == null) return;
        Course course = pickCourse();
        if (course == null) return;
        ms.assignTeacherToCourse(teacher, course);
        System.out.println("✅ " + teacher.getFullName() + " назначен(а) на курс «" + course.getName() + "»");
    }

    private void registerStudentMenu(ManagerService ms) {
        System.out.println("\n─── Записать студента на курс ───");
        Student student = pickStudent();
        if (student == null) return;
        Course course = pickCourse();
        if (course == null) return;
        boolean ok = course.registerStudent(student);
        if (ok) System.out.println("✅ " + student.getFullName() + " записан(а) на курс «" + course.getName() + "»");
    }

    private void publishNewsMenu(ManagerService ms) {
        System.out.print("Заголовок новости: "); String title   = sc.nextLine().trim();
        System.out.print("Содержание:        "); String content = sc.nextLine().trim();
        News news = new News(title, content);
        news.attach(new NewsSubscriber("Студенческий портал"));
        news.attach(new NewsSubscriber("Портал преподавателей"));
        ms.publishNews(news);
        System.out.println("✅ Новость «" + title + "» опубликована!");
    }

    private void manageRequestMenu(ManagerService ms) {
        List<Request> requests = ms.getRequests();
        if (requests.isEmpty()) { System.out.println("Заявок нет."); return; }
        Request req = pickFromList(requests, "заявку");
        if (req == null) return;
        System.out.println("  1. Одобрить  2. Отклонить");
        System.out.print("Действие: ");
        String c = sc.nextLine().trim();
        if ("1".equals(c)) ms.approveRequest(req);
        else if ("2".equals(c)) ms.rejectRequest(req);
    }

    // ─── Strategy Pattern: отчёты ────────────────────────────

    private void generateReportMenu() {
        System.out.println("\n─── Генерация отчёта ───");
        System.out.println("  1. Статистика по курсу");
        System.out.println("  2. Топ студентов по GPA");
        System.out.println("  3. Рейтинг исследователей");
        System.out.print("Тип отчёта: ");

        ReportStrategy strategy = switch (sc.nextLine().trim()) {
            case "1" -> {
                Course c = pickCourse();
                yield c != null ? new CourseStatReport(c) : null;
            }
            case "2" -> new StudentGpaReport();
            case "3" -> new ResearcherRankReport();
            default  -> null;
        };

        if (strategy != null) {
            System.out.println("\nГенерирую: " + strategy.getName() + "...");
            strategy.generate(store);
        } else {
            System.out.println("Неверный тип отчёта.");
        }
    }

    // ═══════════════════════════════════════════════════════
    //  МЕНЮ ПРЕПОДАВАТЕЛЯ
    // ═══════════════════════════════════════════════════════

    private void teacherMenu(Teacher teacher) {
        while (true) {
            System.out.println("\n╔══════════════════════════════════════╗");
            System.out.println("║        МЕНЮ ПРЕПОДАВАТЕЛЯ            ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.println("║   1. Мои курсы                       ║");
            System.out.println("║   2. Студенты курса                  ║");
            System.out.println("║   3. Выставить оценку                ║");
            System.out.println("║   4. Отправить сообщение             ║");
            System.out.println("║   5. Подать заявку                   ║");
            System.out.println("║   0. Выйти                           ║");
            System.out.println("╚══════════════════════════════════════╝");
            System.out.print("Ваш выбор: ");

            switch (sc.nextLine().trim()) {
                case "1" -> teacher.viewCourses();
                case "2" -> viewStudentsInCourse(teacher);
                case "3" -> putMarkWizard(teacher);
                case "4" -> sendMessageMenu(teacher);
                case "5" -> submitRequestMenu(teacher);
                case "0" -> { System.out.println("Выход из аккаунта."); return; }
                default  -> System.out.println("Неверный выбор.");
            }
        }
    }

    private void viewStudentsInCourse(Teacher teacher) {
        if (teacher.getCourses().isEmpty()) { System.out.println("У вас нет курсов."); return; }
        Course course = pickFromList(teacher.getCourses(), "курс");
        if (course != null) teacher.viewStudents(course);
    }

    private void putMarkWizard(Teacher teacher) {
        System.out.println("\n─── Выставить оценку ───");
        if (teacher.getCourses().isEmpty()) { System.out.println("У вас нет курсов."); return; }
        Course course = pickFromList(teacher.getCourses(), "курс");
        if (course == null) return;
        if (course.getStudents().isEmpty()) { System.out.println("На этом курсе нет студентов."); return; }
        Student student = pickFromList(course.getStudents(), "студента");
        if (student == null) return;
        System.out.println("Студент: " + student.getFullName());
        System.out.print("Аттестация 1 (0-30): ");    double att1 = readDouble(0, 30, 0);
        System.out.print("Аттестация 2 (0-30): ");    double att2 = readDouble(0, 30, 0);
        System.out.print("Финальный экзамен (0-40): "); double fin = readDouble(0, 40, 0);
        teacher.putMark(course, student, new Mark(att1, att2, fin));
        Mark mark = course.getMark(student);
        System.out.println("✅ Оценка выставлена: " + mark.getLetterGrade()
                + " (" + mark.getTotal() + " баллов)");
    }

    // ═══════════════════════════════════════════════════════
    //  МЕНЮ СТУДЕНТА
    // ═══════════════════════════════════════════════════════

    private void studentMenu(Student student) {
        while (true) {
            System.out.println("\n╔══════════════════════════════════════╗");
            System.out.println("║          МЕНЮ СТУДЕНТА               ║");
            System.out.println("╠══════════════════════════════════════╣");
            System.out.printf( "║  GPA: %-5.2f  Кредиты: %-3d  Год: %-2d  ║%n",
                    student.getGpa(), student.getRegisteredCredits(), student.getYearOfStudy());
            System.out.println("╠══════════════════════════════════════╣");
            System.out.println("║   1. Все доступные курсы             ║");
            System.out.println("║   2. Записаться на курс              ║");
            System.out.println("║   3. Мои курсы                       ║");
            System.out.println("║   4. Мои оценки                      ║");
            System.out.println("║   5. Мой транскрипт                  ║");
            System.out.println("║   6. Оценить преподавателя           ║");
            System.out.println("║   7. Преподаватели курса             ║");
            System.out.println("║   8. Отправить сообщение             ║");
            System.out.println("║   0. Выйти                           ║");
            System.out.println("╚══════════════════════════════════════╝");
            System.out.print("Ваш выбор: ");

            switch (sc.nextLine().trim()) {
                case "1" -> listCourses();
                case "2" -> selfRegisterMenu(student);
                case "3" -> student.viewCourses();
                case "4" -> student.viewMarks();
                case "5" -> printTranscript(student);
                case "6" -> rateTeacherMenu(student);
                case "7" -> viewTeachersMenu(student);
                case "8" -> sendMessageMenu(student);
                case "0" -> { System.out.println("Выход из аккаунта."); return; }
                default  -> System.out.println("Неверный выбор.");
            }
        }
    }

    private void selfRegisterMenu(Student student) {
        System.out.println("\n─── Запись на курс ───");
        System.out.printf("Ваши кредиты: %d/21  |  Провалов: %d/3%n",
                student.getRegisteredCredits(), student.getFailedCoursesCount());
        if (store.getCourses().isEmpty()) { System.out.println("Курсов нет."); return; }
        listCourses();
        System.out.print("Номер курса: ");
        int idx = readInt(1, store.getCourses().size(), 0) - 1;
        if (idx < 0) return;
        Course course = store.getCourses().get(idx);
        boolean ok = course.registerStudent(student);
        if (ok) System.out.println("✅ Вы записаны на курс «" + course.getName() + "»!");
    }

    private void printTranscript(Student student) {
        System.out.println("\n─── Транскрипт: " + student.getFullName() + " ───");
        Transcript transcript = new Transcript();
        for (Course c : student.getRegisteredCourses()) {
            Mark m = c.getMark(student);
            if (m != null) transcript.addCourseMark(c, m);
        }
        transcript.printTranscript();
    }

    private void rateTeacherMenu(Student student) {
        List<Teacher> teachers = getTeachers();
        if (teachers.isEmpty()) { System.out.println("Преподавателей нет."); return; }
        Teacher t = pickFromList(teachers, "преподавателя");
        if (t == null) return;
        System.out.print("Оценка (1-5): ");
        int rating = readInt(1, 5, 3);
        student.rateTeacher(t, rating);
    }

    private void viewTeachersMenu(Student student) {
        if (student.getRegisteredCourses().isEmpty()) { System.out.println("Вы не записаны ни на один курс."); return; }
        Course c = pickFromList(student.getRegisteredCourses(), "курс");
        if (c != null) student.viewTeachersOfCourse(c);
    }

    // ═══════════════════════════════════════════════════════
    //  ОБЩИЕ МЕТОДЫ
    // ═══════════════════════════════════════════════════════

    private void sendMessageMenu(User sender) {
        System.out.print("Username получателя: ");
        User receiver = findByUsername(sc.nextLine().trim());
        if (receiver == null) { System.out.println("❌ Пользователь не найден."); return; }
        System.out.print("Сообщение: ");
        String text = sc.nextLine().trim();
        store.addMessage(new Message(sender, receiver, text));
        System.out.println("✅ Сообщение отправлено " + receiver.getFullName());
    }

    private void submitRequestMenu(User user) {
        System.out.print("Текст заявки: ");
        String content = sc.nextLine().trim();
        store.addRequest(new Request(user, content));
        System.out.println("✅ Заявка подана: " + content);
    }

    // ═══════════════════════════════════════════════════════
    //  ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ: ПИКЕРЫ
    // ═══════════════════════════════════════════════════════

    private Teacher pickTeacher() {
        List<Teacher> list = getTeachers();
        if (list.isEmpty()) { System.out.println("Преподавателей нет."); return null; }
        return pickFromList(list, "преподавателя");
    }

    private Student pickStudent() {
        List<Student> list = getStudents();
        if (list.isEmpty()) { System.out.println("Студентов нет."); return null; }
        return pickFromList(list, "студента");
    }

    private Course pickCourse() {
        if (store.getCourses().isEmpty()) { System.out.println("Курсов нет."); return null; }
        return pickFromList(store.getCourses(), "курс");
    }

    private <T> T pickFromList(List<T> list, String label) {
        System.out.println("\nВыберите " + label + ":");
        for (int i = 0; i < list.size(); i++)
            System.out.println("  " + (i + 1) + ". " + list.get(i));
        System.out.print("Номер: ");
        int idx = readInt(1, list.size(), 0) - 1;
        if (idx < 0 || idx >= list.size()) { System.out.println("Неверный выбор."); return null; }
        return list.get(idx);
    }

    // ═══════════════════════════════════════════════════════
    //  ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ: ENUM ВЫБОРЩИКИ
    // ═══════════════════════════════════════════════════════

    private Gender chooseGender() {
        System.out.println("  Пол:  1. Мужской   2. Женский");
        System.out.print("  Выбор: ");
        return "2".equals(sc.nextLine().trim()) ? Gender.FEMALE : Gender.MALE;
    }

    private SchoolType chooseSchool() {
        System.out.println("  Школа: 1.FIT  2.BS  3.ISE  4.SAM  5.KMA");
        System.out.print("  Выбор: ");
        return switch (sc.nextLine().trim()) {
            case "2" -> SchoolType.BS;
            case "3" -> SchoolType.ISE;
            case "4" -> SchoolType.SAM;
            case "5" -> SchoolType.KMA;
            default  -> SchoolType.FIT;
        };
    }

    private TeacherTitle chooseTitle() {
        System.out.println("  Звание: 1.Tutor  2.Lecturer  3.Senior Lecturer  4.Professor");
        System.out.print("  Выбор: ");
        return switch (sc.nextLine().trim()) {
            case "1" -> TeacherTitle.TUTOR;
            case "3" -> TeacherTitle.SENIOR_LECTURER;
            case "4" -> TeacherTitle.PROFESSOR;
            default  -> TeacherTitle.LECTURER;
        };
    }

    private ManagerType chooseManagerType() {
        System.out.println("  Тип менеджера: 1.OR  2.Department  3.Faculty");
        System.out.print("  Выбор: ");
        return switch (sc.nextLine().trim()) {
            case "2" -> ManagerType.DEPARTMENT;
            case "3" -> ManagerType.FACULTY;
            default  -> ManagerType.OR;
        };
    }

    // ═══════════════════════════════════════════════════════
    //  ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ: ДАННЫЕ
    // ═══════════════════════════════════════════════════════

    private List<Student> getStudents() {
        List<Student> list = new ArrayList<>();
        for (User u : store.getUsers()) if (u instanceof Student s) list.add(s);
        return list;
    }

    private List<Teacher> getTeachers() {
        List<Teacher> list = new ArrayList<>();
        for (User u : store.getUsers()) if (u instanceof Teacher t) list.add(t);
        return list;
    }

    private User findById(String id) {
        for (User u : store.getUsers()) if (u.getId().equals(id)) return u;
        return null;
    }

    private User findByUsername(String username) {
        for (User u : store.getUsers()) if (u.getUsername().equals(username)) return u;
        return null;
    }

    // ─── Безопасный ввод чисел ──────────────────────────────

    private int readInt(int min, int max, int fallback) {
        try {
            int v = Integer.parseInt(sc.nextLine().trim());
            return (v >= min && v <= max) ? v : fallback;
        } catch (Exception e) { return fallback; }
    }

    private double readDouble(double min, double max, double fallback) {
        try {
            double v = Double.parseDouble(sc.nextLine().trim());
            return (v >= min && v <= max) ? v : fallback;
        } catch (Exception e) { return fallback; }
    }

    // ═══════════════════════════════════════════════════════
    //  НАЧАЛЬНЫЕ ТЕСТОВЫЕ ДАННЫЕ
    // ═══════════════════════════════════════════════════════

    private void initSampleData() {
        if (!store.getUsers().isEmpty()) return; // уже инициализировано

        // Администратор
        Admin admin = new Admin("A1", "admin", "admin123",
                "Алина", "Сарсен", Gender.FEMALE, 400_000, "Администрация", SchoolType.FIT);

        // Менеджер
        Manager manager = new Manager("M1", "manager", "manager123",
                "Аружан", "Бектур", Gender.FEMALE, 450_000, "Регистратура", SchoolType.FIT, ManagerType.OR);

        // Преподаватели
        Teacher t1 = new Teacher("T1", "teacher1", "teach123",
                "Диас", "Танирберген", Gender.MALE, 500_000, "FIT", SchoolType.FIT, TeacherTitle.PROFESSOR);
        t1.setHIndex(5);

        Teacher t2 = new Teacher("T2", "teacher2", "teach123",
                "Мирас", "Оспанов", Gender.MALE, 480_000, "FIT", SchoolType.FIT, TeacherTitle.SENIOR_LECTURER);
        t2.becomeResearcher();
        t2.setHIndex(2);

        // Студенты
        Student s1 = new Student("S1", "student1", "stud123",
                "Нұржан", "Қалмурат", Gender.MALE, "CS", 2, 3.5, 0);
        Student s2 = new Student("S2", "student2", "stud123",
                "Алия", "Серік", Gender.FEMALE, "IS", 1, 3.9, 0);

        // Курсы
        Course oop  = new Course("OOP",  5, "CS",  2);
        Course math = new Course("Math", 4, "Any", 0);
        Course db   = new Course("Databases", 4, "IS", 1);

        // Назначение
        oop.addInstructor(t1);
        math.addInstructor(t2);
        db.addInstructor(t2);

        // Регистрация студентов
        oop.registerStudent(s1);
        math.registerStudent(s2);
        db.registerStudent(s2);

        // Сохраняем всё в DataStore
        store.addUser(admin);   adminService.addUser(admin);
        store.addUser(manager); adminService.addUser(manager);
        store.addUser(t1);      adminService.addUser(t1);
        store.addUser(t2);      adminService.addUser(t2);
        store.addUser(s1);      adminService.addUser(s1);
        store.addUser(s2);      adminService.addUser(s2);
        store.addCourse(oop);
        store.addCourse(math);
        store.addCourse(db);

        System.out.println("✅ Тестовые данные загружены успешно.");
        System.out.println();
        System.out.println("  Логины для входа:");
        System.out.println("  ┌─────────────┬────────────┬──────────────────┐");
        System.out.println("  │   Роль      │  Username  │    Password      │");
        System.out.println("  ├─────────────┼────────────┼──────────────────┤");
        System.out.println("  │ Admin       │ admin      │ admin123         │");
        System.out.println("  │ Manager     │ manager    │ manager123       │");
        System.out.println("  │ Teacher     │ teacher1   │ teach123         │");
        System.out.println("  │ Teacher     │ teacher2   │ teach123         │");
        System.out.println("  │ Student     │ student1   │ stud123          │");
        System.out.println("  │ Student     │ student2   │ stud123          │");
        System.out.println("  └─────────────┴────────────┴──────────────────┘");
    }
}
