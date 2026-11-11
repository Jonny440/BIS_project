package patterns;

import courses.Course;
import courses.Mark;
import database.DataStore;
import users.Student;

import java.util.Map;

public class CourseStatReport implements ReportStrategy {
    private final Course course;

    public CourseStatReport(Course course) {
        this.course = course;
    }

    @Override
    public String getName() {
        return "Статистика по курсу: " + course.getName();
    }

    @Override
    public void generate(DataStore store) {
        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.println("║      ОТЧЁТ: " + course.getName());
        System.out.println("╚══════════════════════════════════════╝");

        Map<Student, Mark> marks = course.getStudentMarks();
        if (marks.isEmpty()) {
            System.out.println("  Оценок нет.");
            return;
        }

        double total = 0, max = -1, min = 101;
        int passed = 0, failed = 0;

        for (Map.Entry<Student, Mark> entry : marks.entrySet()) {
            double score = entry.getValue().getTotal();
            total += score;
            if (score > max) max = score;
            if (score < min) min = score;
            if (entry.getValue().isPassed()) passed++; else failed++;
        }

        double avg = total / marks.size();
        System.out.printf("  Студентов:      %d%n", marks.size());
        System.out.printf("  Средний балл:   %.1f%n", avg);
        System.out.printf("  Макс. балл:     %.1f%n", max);
        System.out.printf("  Мин. балл:      %.1f%n", min);
        System.out.printf("  Сдали:          %d%n", passed);
        System.out.printf("  Не сдали:       %d%n", failed);
        System.out.printf("  Успеваемость:   %.0f%%%n", (double) passed / marks.size() * 100);

        System.out.println("\n  Детали:");
        for (Map.Entry<Student, Mark> e : marks.entrySet()) {
            System.out.printf("  %-20s | %s | %.0f баллов%n",
                    e.getKey().getFullName(),
                    e.getValue().getLetterGrade(),
                    e.getValue().getTotal());
        }
    }
}
