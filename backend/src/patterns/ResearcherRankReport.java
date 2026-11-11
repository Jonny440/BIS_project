package patterns;

import database.DataStore;
import research.Researcher;
import research.ResearchPaper;
import research.ResearchUtils;
import users.User;

import java.util.ArrayList;
import java.util.List;

public class ResearcherRankReport implements ReportStrategy {

    @Override
    public String getName() { return "Рейтинг исследователей"; }

    @Override
    public void generate(DataStore store) {
        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.println("║   ОТЧЁТ: РЕЙТИНГ ИССЛЕДОВАТЕЛЕЙ     ║");
        System.out.println("╚══════════════════════════════════════╝");

        List<Researcher> researchers = new ArrayList<>();
        for (User u : store.getUsers())
            if (u instanceof Researcher r) researchers.add(r);

        if (researchers.isEmpty()) { System.out.println("  Исследователей нет."); return; }

        System.out.printf("  %-22s %-8s %-8s %-6s%n", "Имя", "h-index", "Цитат.", "Статей");
        System.out.println("  " + "─".repeat(50));

        for (Researcher r : researchers) {
            int totalCitations = r.getResearchPapers().stream()
                    .mapToInt(ResearchPaper::getCitations).sum();
            System.out.printf("  %-22s %-8d %-8d %-6d%n",
                    r.getResearcherName(), r.getHIndex(),
                    totalCitations, r.getResearchPapers().size());
        }

        Researcher top = ResearchUtils.getTopCitedResearcher(researchers);
        if (top != null)
            System.out.println("\n  🏆 Топ по цитированиям: " + top.getResearcherName()
                    + " (h-index: " + top.getHIndex() + ")");
    }
}
