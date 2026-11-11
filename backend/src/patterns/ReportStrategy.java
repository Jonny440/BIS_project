package patterns;

import database.DataStore;

/**
 * Strategy Pattern — 4-й паттерн проектирования.
 *
 * Позволяет менять алгоритм генерации отчёта без изменения клиентского кода.
 * Новая команда может добавить любой новый отчёт, просто реализовав этот интерфейс.
 *
 * Принцип: Open for extension, Closed for modification (OCP).
 */
public interface ReportStrategy {
    void generate(DataStore store);
    String getName();
}
