package com.example.parchapp;

import com.example.parchapp.data.HelpFaqEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// groups the questions by screen: the screen the user came from first, "general" last, the rest as they come.
// no Android classes here, so it runs in the unit tests, and it also runs off the main thread (in a future)
final class HelpSections {
    static final String GENERAL = "general";

    private HelpSections() {
    }

    static List<HelpSection> originFirst(List<HelpFaqEntity> rows, String origin) {
        Map<String, List<HelpFaqEntity>> byScreen = new LinkedHashMap<>();
        for (HelpFaqEntity row : rows) {
            byScreen.computeIfAbsent(row.screen, screen -> new ArrayList<>()).add(row);
        }
        List<HelpSection> sections = new ArrayList<>();
        for (Map.Entry<String, List<HelpFaqEntity>> entry : byScreen.entrySet()) {
            List<HelpFaqEntity> questions = new ArrayList<>(entry.getValue());
            Collections.sort(questions, Comparator.comparingInt(q -> q.position));
            sections.add(new HelpSection(entry.getKey(), titleOf(entry.getKey()), questions,
                    entry.getKey().equals(origin)));
        }
        // the sort is stable: only the origin goes up and "general" goes down, the rest keeps its order
        Collections.sort(sections, Comparator.comparingInt(HelpSections::rank));
        return sections;
    }

    private static int rank(HelpSection section) {
        if (section.origin) return 0;
        return section.screen.equals(GENERAL) ? 2 : 1;
    }

    static String titleOf(String screen) {
        switch (screen) {
            case "groups": return "Groups";
            case "schedule": return "Schedule";
            case "compare": return "Compare availability";
            case "chat": return "Chat";
            case "andes": return "Andes Events";
            case "dashboard": return "Home";
            case "status": return "Status";
            case "alerts": return "Alerts";
            case "profile": return "Profile";
            case GENERAL: return "General";
            default:
                return screen.isEmpty() ? screen : Character.toUpperCase(screen.charAt(0)) + screen.substring(1);
        }
    }
}
