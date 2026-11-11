package patterns;

import comparators.StudentGpaComparator;
import database.DataStore;
import users.Student;
import users.User;

import java.util.ArrayList;
import java.util.List;

public class StudentGpaReport implements ReportStrategy {

    @Override
    public String getName() { return "Топ студентов по GPA"; }

    @Override
    public void generate(DataStore store) {
        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.println("║     ОТЧЁТ: ТОП СТУДЕНТОВ ПО GPA     ║");
        System.out.println("╚══════════════════════════════════════╝");

        List<Student> students = new ArrayList<>();
        for (User u : store.getUsers())
            if (u instanceof Student s) students.add(s);

        if (students.isEmpty()) { System.out.println("  Студентов нет."); return; }

        students.sort(new StudentGpaComparator());
        System.out.printf("  %-4s %-22s %-6s %-14s %-5s%n", "#", "Имя", "GPA", "Специальность", "Курс");
        System.out.println("  " + "─".repeat(56));
        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);
            System.out.printf("  %-4d %-22s %-6.2f %-14s %-5d%n",
                    i + 1, s.getFullName(), s.getGpa(), s.getMajor(), s.getYearOfStudy());
        }
    }
}
