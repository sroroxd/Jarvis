package de.art.jarvis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dauerhaftes Gedächtnis.
 * „Merk dir …“ und „Vergiss …“ werden direkt in der App erledigt (ohne KI-Aufruf, also zuverlässig und kostenlos).
 * Zusätzlich darf die KI mit [MERKEN: …] von sich aus Vorlieben ablegen.
 */
final class Memory {
    private static final Pattern REMEMBER_TAG = Pattern.compile("\\[\\s*MERKEN\\s*:\\s*(.+?)\\s*\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORGET_TAG = Pattern.compile("\\[\\s*VERGESSEN\\s*:\\s*(.+?)\\s*\\]", Pattern.CASE_INSENSITIVE);

    private static final String PREFIX = "^(?:(?:hey|okay|ok)\\s+)?(?:jarvis[,!.]?\\s+)?(?:bitte\\s+)?";
    private static final Pattern REMEMBER_CMD = Pattern.compile(
            PREFIX + "merk(?:e|t)?\\s+dir(?:\\s+bitte)?[,:.!]?\\s*(?:(?:dass|das|folgendes)[,:]?\\s+)?(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FORGET_CMD = Pattern.compile(
            PREFIX + "vergiss(?:\\s+bitte)?[,:.!]?\\s*(?:(?:dass|das mit|das|die sache mit)\\s+)?(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "dass", "das", "der", "die", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer",
            "und", "oder", "aber", "mit", "von", "für", "auf", "bei", "ich", "mich", "mir", "mein", "meine",
            "immer", "nicht", "bitte", "jarvis", "wieder", "habe", "hast", "soll", "sollst", "wenn", "auch"));

    private static final int MAX_FACTS = 60;

    private Memory() {}

    /** Ergebnis eines lokal erkannten Befehls: Antworttext, oder null wenn kein Befehl. */
    static String handleCommand(String utterance, Prefs prefs) {
        String text = utterance.trim();
        Matcher m = REMEMBER_CMD.matcher(text);
        if (m.matches()) {
            String fact = tidy(m.group(1));
            if (fact.isEmpty()) return "Was soll ich mir merken, Sir?";
            List<String> facts = load(prefs);
            if (!facts.contains(fact)) facts.add(fact);
            save(prefs, facts);
            return "Gemerkt, Sir.";
        }
        m = FORGET_CMD.matcher(text);
        if (m.matches()) {
            String what = tidy(m.group(1));
            // „Vergiss nicht, …“ ist eine normale Bitte, kein Löschbefehl.
            if (what.toLowerCase(Locale.GERMANY).startsWith("nicht")) return null;
            if (keywords(what).isEmpty()) return "Was genau soll ich vergessen, Sir?";
            List<String> facts = load(prefs);
            String removed = removeBestMatch(facts, what);
            if (removed == null) return "Dazu habe ich nichts gespeichert.";
            save(prefs, facts);
            return "Vergessen: " + removed;
        }
        return null;
    }

    /** Verarbeitet [MERKEN:]/[VERGESSEN:]-Zeilen der KI und entfernt sie aus dem Text. */
    static String applyTags(String answer, Prefs prefs) {
        List<String> facts = load(prefs);
        boolean changed = false;
        Matcher f = FORGET_TAG.matcher(answer);
        while (f.find()) {
            if (removeBestMatch(facts, f.group(1)) != null) changed = true;
        }
        Matcher r = REMEMBER_TAG.matcher(answer);
        while (r.find()) {
            String fact = tidy(r.group(1));
            if (!fact.isEmpty() && !facts.contains(fact)) {
                facts.add(fact);
                changed = true;
            }
        }
        if (changed) save(prefs, facts);
        String cleaned = FORGET_TAG.matcher(REMEMBER_TAG.matcher(answer).replaceAll("")).replaceAll("");
        return cleaned.replaceAll("\n{3,}", "\n\n").trim();
    }

    /** Entfernt den Fakt mit den meisten gemeinsamen Stichwörtern; gibt ihn zurück oder null. */
    private static String removeBestMatch(List<String> facts, String query) {
        Set<String> keys = keywords(query);
        if (keys.isEmpty()) return null;
        int best = -1;
        int bestScore = 0;
        for (int i = 0; i < facts.size(); i++) {
            Set<String> fk = keywords(facts.get(i));
            int score = 0;
            for (String k : keys) {
                for (String w : fk) {
                    if (w.startsWith(k) || k.startsWith(w)) {
                        score++;
                        break;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best < 0 ? null : facts.remove(best);
    }

    private static Set<String> keywords(String s) {
        Set<String> out = new HashSet<>();
        for (String w : s.toLowerCase(Locale.GERMANY).split("[^\\p{L}\\p{N}]+")) {
            if (w.length() >= 3 && !STOPWORDS.contains(w)) out.add(w.length() > 5 ? w.substring(0, 5) : w);
        }
        return out;
    }

    private static String tidy(String s) {
        String t = s == null ? "" : s.trim().replaceAll("[.!?\\s]+$", "");
        if (t.isEmpty()) return t;
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static List<String> load(Prefs prefs) {
        List<String> facts = new ArrayList<>();
        for (String line : prefs.memory().split("\n")) {
            String t = line.replaceFirst("^[-•]\\s*", "").trim();
            if (!t.isEmpty()) facts.add(t);
        }
        return facts;
    }

    private static void save(Prefs prefs, List<String> facts) {
        while (facts.size() > MAX_FACTS) facts.remove(0);
        StringBuilder sb = new StringBuilder();
        for (String fact : facts) sb.append("- ").append(fact).append('\n');
        prefs.setMemory(sb.toString());
    }
}
