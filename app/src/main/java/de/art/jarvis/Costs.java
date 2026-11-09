package de.art.jarvis;

import java.util.Locale;

/**
 * Was eine Claude-Anfrage kostet (Arts Plan vom 30. Okt.: Kosten in Cent pro Antwort und als Tagessumme).
 *
 * <p>Preise in US-Dollar pro Million Tokens laut platform.claude.com/docs/en/about-claude/pricing, geprüft am
 * 8. Nov. 2026: Spalten Eingabe, Cache schreiben (5 Minuten), Cache lesen, Ausgabe. „input_tokens“ der API zählt nur
 * die Tokens, die weder aus dem Zwischenspeicher gelesen noch hineingeschrieben wurden – die drei Posten addieren sich.
 * Websuche: 10 Dollar je 1.000 Suchen. Haiku 5.5 kostet über 100.000 Tokens Eingabe mehr; so lange Anfragen schickt
 * Jarvis nicht.
 *
 * <p>Reines Java ohne Android, daher automatisch testbar.
 */
final class Costs {
    private Costs() {}

    /** Preise je Million Tokens: Eingabe, Cache schreiben, Cache lesen, Ausgabe. Null = unbekanntes Modell. */
    static double[] prices(String model) {
        String m = model == null ? "" : model.toLowerCase(Locale.ROOT);
        // OpenAI-Modellseite, 10.11.2026; Standardtarif ohne regionale/Service-Aufschläge.
        if (m.equals("gpt-6-luna")) return new double[] {0.10, 0.125, 0.01, 0.50};
        if (m.contains("haiku-5-5")) return new double[] {0.10, 0.125, 0.01, 0.50};
        if (m.contains("sonnet-5-5")) return new double[] {2.0, 2.50, 0.10, 10.0};
        if (m.contains("sonnet-5")) return new double[] {2.0, 2.50, 0.20, 10.0};
        if (m.contains("opus-5-5")) return new double[] {4.0, 5.0, 0.20, 20.0};
        if (m.contains("fable-5-1")) return new double[] {10.0, 12.50, 0.25, 50.0};
        return null;
    }

    /** Kosten in US-Dollar; 0, wenn das Modell unbekannt ist (dann lieber nichts anzeigen als etwas Falsches). */
    static double usd(String model, int input, int cacheWrite, int cacheRead, int output, int searches) {
        double[] p = prices(model);
        if (p == null) return 0;
        return (input * p[0] + cacheWrite * p[1] + cacheRead * p[2] + output * p[3]) / 1_000_000.0
                + searches * 0.01;
    }

    static boolean known(String model) {
        return prices(model) != null;
    }

    /**
     * Wofür das Geld ausgegeben wurde. Arts Handy zeigte am 8. Nov. abends 875 ct an einem Tag – ohne Aufschlüsselung
     * war nicht zu sagen, ob Fragen, Filter, Wächter oder Recherche den Posten trieben. Jeder Weg zu Claude läuft
     * auf einem eigenen Thread ({@link ClaudeClient}); der Thread bestimmt die Art.
     */
    enum Kind {
        /** Arts Fragen und die ungefragten Ansagen (Sonnet). */
        FRAGEN("Fragen"),
        /** Filter vor Claude bei offenem Mikrofon (Haiku, nur Text). */
        FILTER("Filter"),
        /** Phasen-Wächter des Coachings (Haiku mit Bild). */
        WAECHTER("Wächter"),
        /** Tägliche Meta-Recherche mit Websuche. */
        RECHERCHE("Recherche");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** „Fragen 312 ct · Wächter 4,1 ct“ – nur Posten über null, größter zuerst; leer, wenn nichts ausgegeben wurde. */
    static String breakdown(double[] usdByKind) {
        Kind[] kinds = Kind.values();
        Integer[] order = new Integer[kinds.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(at(usdByKind, b), at(usdByKind, a)));
        StringBuilder sb = new StringBuilder();
        for (int i : order) {
            double usd = at(usdByKind, i);
            if (usd <= 0) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(kinds[i].label).append(' ').append(cents(usd));
        }
        return sb.toString();
    }

    private static double at(double[] a, int i) {
        return a != null && i < a.length ? a[i] : 0;
    }

    /**
     * Tagesgrenze erreicht? limitCents 0 = keine Grenze. Über der Grenze antwortet Jarvis bei offenem Mikrofon nur
     * noch, wenn Art ihn beim Namen nennt oder ein Gespräch läuft, und der Phasen-Wächter pausiert – Arts eigene
     * Fragen gehen immer durch.
     */
    static boolean overLimit(double spentUsd, int limitCents) {
        return limitCents > 0 && spentUsd * 100 >= limitCents;
    }

    /** „0,6 ct“, „12 ct“ – US-Cent, unter einem Cent mit einer Nachkommastelle. */
    static String cents(double usd) {
        double ct = usd * 100;
        if (ct < 10) return String.format(Locale.GERMANY, "%.1f ct", ct);
        return String.format(Locale.GERMANY, "%.0f ct", ct);
    }
}
