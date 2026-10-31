package de.art.jarvis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Liest [MERKEN: …] und [VERGESSEN: …] aus Antworten, speichert sie und entfernt sie aus dem Text. */
final class Memory {
    private static final Pattern REMEMBER = Pattern.compile("\\[\\s*MERKEN\\s*:\\s*(.+?)\\s*\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORGET = Pattern.compile("\\[\\s*VERGESSEN\\s*:\\s*(.+?)\\s*\\]", Pattern.CASE_INSENSITIVE);
    private static final int MAX_FACTS = 60;

    private Memory() {}

    static String apply(String answer, Prefs prefs) {
        List<String> facts = new ArrayList<>();
        for (String line : prefs.memory().split("\n")) {
            String t = line.replaceFirst("^[-•]\\s*", "").trim();
            if (!t.isEmpty()) facts.add(t);
        }
        boolean changed = false;

        Matcher f = FORGET.matcher(answer);
        while (f.find()) {
            String key = f.group(1).toLowerCase(Locale.GERMANY);
            for (int i = facts.size() - 1; i >= 0; i--) {
                if (facts.get(i).toLowerCase(Locale.GERMANY).contains(key)) {
                    facts.remove(i);
                    changed = true;
                }
            }
        }

        Matcher r = REMEMBER.matcher(answer);
        while (r.find()) {
            String fact = r.group(1).trim();
            if (!fact.isEmpty() && !facts.contains(fact)) {
                facts.add(fact);
                changed = true;
            }
        }

        if (changed) {
            while (facts.size() > MAX_FACTS) facts.remove(0);
            StringBuilder sb = new StringBuilder();
            for (String fact : facts) sb.append("- ").append(fact).append('\n');
            prefs.setMemory(sb.toString());
        }

        String cleaned = FORGET.matcher(REMEMBER.matcher(answer).replaceAll("")).replaceAll("");
        return cleaned.replaceAll("\n{3,}", "\n\n").trim();
    }
}
