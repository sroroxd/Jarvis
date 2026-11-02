package de.art.jarvis;

/**
 * Tägliche TFT-Meta: Texte für die Recherche und für Jarvis' Grundanweisung.
 * Reines Java ohne Android-Abhängigkeiten, daher automatisch testbar.
 */
final class Meta {
    /** Günstiges Modell für die Recherche – die Suche selbst kostet ohnehin ~1 Cent pro Treffer. */
    static final String MODEL = "claude-haiku-5-5";
    static final int MAX_SEARCHES = 3;

    private Meta() {}

    /** Ist die gespeicherte Meta von heute? */
    static boolean isFresh(String metaDate, String today) {
        return metaDate != null && !metaDate.isEmpty() && metaDate.equals(today);
    }

    static String system() {
        return "Du bist ein präziser Analyst für Teamfight Tactics. Du recherchierst im Internet und gibst nur wieder, "
                + "was die Quellen tatsächlich sagen. Nichts erfinden.";
    }

    static String prompt(String today) {
        return "Heute ist " + today + ". Recherchiere die AKTUELLE Meta von Teamfight Tactics für Ranked auf EUW: "
                + "das heute gültige Set und den heute gültigen Patch. Bevorzuge aktuelle Quellen wie tactics.tools, MetaTFT, "
                + "TFT Academy, Mobalytics oder lolchess und achte auf das Datum der Seiten.\n"
                + "Fasse für einen Sprach-Coach zusammen, höchstens 1800 Zeichen, knappe Stichpunkte:\n"
                + "1. Set und Patch-Nummer.\n"
                + "2. Die 5 bis 8 stärksten Comps: Kern-Einheiten, Carry, wichtigste Gegenstände.\n"
                + "3. Starke Augments oder Mechaniken dieses Patches.\n"
                + "4. Was zuletzt deutlich verstärkt oder abgeschwächt wurde.\n"
                + "Verwende die deutschen Namen des deutschen Spiel-Clients. Wenn du eine deutsche Bezeichnung nicht sicher "
                + "kennst, schreib den englischen Namen in Klammern dazu. Keine Einleitung, kein Schlusssatz.";
    }

    /** Block für die Grundanweisung im Spielmodus – leer, wenn keine Meta vorliegt. */
    static String forPersona(String meta, String metaDate) {
        if (meta == null || meta.trim().isEmpty()) {
            return "Du hast heute keine aktuelle Meta-Recherche. Bei Fragen nach der aktuell stärksten Comp oder nach "
                    + "Patch-Details sag offen, dass dein Stand veraltet sein kann.";
        }
        return "AKTUELLE TFT-META (recherchiert am " + metaDate + ", aus dem Internet). Sie hat Vorrang vor deinem "
                + "älteren Wissen über Comps, Augments und Patches:\n" + meta.trim();
    }
}
