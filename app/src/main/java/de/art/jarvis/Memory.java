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
 * Dauerhaftes Gedächtnis – arbeitet nur mit Text (ein Fakt pro Zeile „- …“) und ist dadurch testbar.
 * „Merk dir …“ und „Vergiss …“ werden direkt in der App erledigt (ohne KI-Aufruf: zuverlässig und kostenlos).
 * Zusätzlich darf die KI mit [MERKEN: …] von sich aus Vorlieben ablegen.
 */
final class Memory {
    /** Antworttext plus neuer Gedächtnisinhalt. */
    static final class Outcome {
        final String reply;
        final String memory;

        Outcome(String reply, String memory) {
            this.reply = reply;
            this.memory = memory;
        }
    }

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

    static final int MAX_FACTS = 60;

    private Memory() {}

    /** Erkennt „Merk dir …“ / „Vergiss …“. Gibt null zurück, wenn es kein solcher Befehl ist. */
    static Outcome command(String utterance, String memory) {
        String text = utterance == null ? "" : utterance.trim();
        Matcher m = REMEMBER_CMD.matcher(text);
        if (m.matches()) {
            String fact = tidy(m.group(1));
            if (fact.isEmpty()) return new Outcome("Was soll ich mir merken, Sir?", memory);
            List<String> facts = load(memory);
            if (!facts.contains(fact)) facts.add(fact);
            return new Outcome("Gemerkt, Sir.", save(facts));
        }
        m = FORGET_CMD.matcher(text);
        if (m.matches()) {
            String what = tidy(m.group(1));
            // „Vergiss nicht, …“ ist eine normale Bitte, kein Löschbefehl.
            if (what.toLowerCase(Locale.GERMANY).startsWith("nicht")) return null;
            if (keywords(what).isEmpty()) return new Outcome("Was genau soll ich vergessen, Sir?", memory);
            List<String> facts = load(memory);
            String removed = removeBestMatch(facts, what);
            if (removed == null) return new Outcome("Dazu habe ich nichts gespeichert.", memory);
            return new Outcome("Vergessen: " + removed, save(facts));
        }
        return null;
    }

    /** Verarbeitet [MERKEN:]/[VERGESSEN:]-Zeilen der KI und entfernt sie aus dem Antworttext. */
    static Outcome tags(String answer, String memory) {
        String a = answer == null ? "" : answer;
        List<String> facts = load(memory);
        Matcher f = FORGET_TAG.matcher(a);
        while (f.find()) removeBestMatch(facts, f.group(1));
        Matcher r = REMEMBER_TAG.matcher(a);
        while (r.find()) {
            String fact = tidy(r.group(1));
            if (!fact.isEmpty() && !facts.contains(fact)) facts.add(fact);
        }
        String cleaned = FORGET_TAG.matcher(REMEMBER_TAG.matcher(a).replaceAll("")).replaceAll("");
        return new Outcome(cleaned.replaceAll("\n{3,}", "\n\n").trim(), save(facts));
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

    static List<String> load(String memory) {
        List<String> facts = new ArrayList<>();
        if (memory == null) return facts;
        for (String line : memory.split("\n")) {
            String t = line.replaceFirst("^[-•]\\s*", "").trim();
            if (!t.isEmpty()) facts.add(t);
        }
        return facts;
    }

    static String save(List<String> facts) {
        while (facts.size() > MAX_FACTS) facts.remove(0);
        StringBuilder sb = new StringBuilder();
        for (String fact : facts) sb.append("- ").append(fact).append('\n');
        return sb.toString().trim();
    }
}
