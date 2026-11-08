package de.art.jarvis;

/**
 * Antwort des Jarvis-Agenten (Termux) – zerlegt aus dem JSON der Brücke.
 *
 * Eigener kleiner Leser statt {@code org.json}: Das Protokoll ist flach und auf
 * beiden Seiten selbst festgelegt, und so läuft die Zerlegung ohne Android und
 * ohne zusätzliche Test-Bibliothek in den automatischen Tests mit.
 *
 * Felder der Brücke: {@code antwort}, {@code sitzung}, {@code kosten},
 * {@code dauer}, {@code schritte}, {@code fehler}.
 */
final class AgentReply {
    /** Vorgelesene Antwort. Leer, wenn etwas schiefging. */
    final String antwort;
    /** Klartext-Fehler für Art, oder {@code null} bei Erfolg. */
    final String fehler;
    /** Kosten dieses Auftrags in Dollar. Beim Abo meldet Claude Code 0. */
    final double kosten;
    /** Dauer in Millisekunden, wie vom Agenten gemeldet. */
    final long dauer;

    private AgentReply(String antwort, String fehler, double kosten, long dauer) {
        this.antwort = antwort;
        this.fehler = fehler;
        this.kosten = kosten;
        this.dauer = dauer;
    }

    static AgentReply fehler(String text) {
        return new AgentReply("", text, 0, 0);
    }

    boolean ok() {
        return fehler == null;
    }

    /**
     * Zerlegt die Antwort der Brücke. {@code code} ist der HTTP-Status;
     * {@code body} darf leer oder unlesbar sein – dann kommt eine verständliche
     * Meldung heraus, nie eine Ausnahme.
     */
    static AgentReply parse(int code, String body) {
        String roh = body == null ? "" : body.trim();
        String gemeldet = text(roh, "fehler");
        if (!gemeldet.isEmpty()) return fehler(gemeldet);

        if (code == 401) return fehler("Der Agent weist mich ab: falsches Token in den Einstellungen.");
        if (code == 409) return fehler("Der Agent arbeitet noch am letzten Auftrag.");
        if (code == 404) return fehler("Der Agent läuft, kennt diesen Auftrag aber nicht. Er ist wohl veraltet.");
        if (code < 200 || code >= 300) {
            return fehler("Der Agent hat mit Fehler " + code + " geantwortet.");
        }

        String antwort = text(roh, "antwort");
        if (antwort.isEmpty()) return fehler("Der Agent hat keine Antwort geschickt.");
        return new AgentReply(antwort, null, zahl(roh, "kosten"), (long) zahl(roh, "dauer"));
    }

    /** Baut einen JSON-Text mit Anführungszeichen und den nötigen Maskierungen. */
    static String jsonText(String s) {
        StringBuilder sb = new StringBuilder("\"");
        String t = s == null ? "" : s;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Liest ein Text-Feld der obersten Ebene. Leer, wenn es fehlt oder {@code null} ist. */
    static String text(String json, String name) {
        int i = feldWert(json, name);
        if (i < 0 || i >= json.length() || json.charAt(i) != '"') return "";
        StringBuilder sb = new StringBuilder();
        for (int p = i + 1; p < json.length(); p++) {
            char c = json.charAt(p);
            if (c == '"') return sb.toString();
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (++p >= json.length()) break;
            char e = json.charAt(p);
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'u':
                    if (p + 4 < json.length()) {
                        try {
                            sb.append((char) Integer.parseInt(json.substring(p + 1, p + 5), 16));
                            p += 4;
                        } catch (NumberFormatException ignored) {
                            // kaputte Escape-Folge: überspringen
                        }
                    }
                    break;
                default: sb.append(e); // \" \\ \/ und alles andere wörtlich
            }
        }
        return "";
    }

    /** Liest ein Zahlen-Feld der obersten Ebene. 0, wenn es fehlt oder keine Zahl ist. */
    static double zahl(String json, String name) {
        int i = feldWert(json, name);
        if (i < 0) return 0;
        int ende = i;
        while (ende < json.length() && "-+.eE0123456789".indexOf(json.charAt(ende)) >= 0) ende++;
        if (ende == i) return 0;
        try {
            return Double.parseDouble(json.substring(i, ende));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Sucht {@code "name":} auf der obersten Ebene und liefert die Position des
     * Werts. Läuft den Text einmal durch und überspringt Zeichenketten und
     * tiefere Objekte, damit ein gleichnamiger Schlüssel in einem Unterobjekt
     * oder ein Treffer mitten in einem Text nichts verfälscht.
     */
    private static int feldWert(String json, String name) {
        if (json == null || json.isEmpty()) return -1;
        int tiefe = 0;
        for (int p = 0; p < json.length(); p++) {
            char c = json.charAt(p);
            if (c == '{' || c == '[') {
                tiefe++;
            } else if (c == '}' || c == ']') {
                tiefe--;
            } else if (c == '"') {
                int schluss = stringEnde(json, p);
                if (schluss < 0) return -1;
                if (tiefe == 1 && passt(json, p, schluss, name)) {
                    int q = schluss + 1;
                    while (q < json.length() && Character.isWhitespace(json.charAt(q))) q++;
                    if (q < json.length() && json.charAt(q) == ':') {
                        q++;
                        while (q < json.length() && Character.isWhitespace(json.charAt(q))) q++;
                        return q;
                    }
                }
                p = schluss;
            }
        }
        return -1;
    }

    /** Position des schließenden Anführungszeichens zu dem bei {@code start}. */
    private static int stringEnde(String json, int start) {
        for (int p = start + 1; p < json.length(); p++) {
            char c = json.charAt(p);
            if (c == '\\') {
                p++;
            } else if (c == '"') {
                return p;
            }
        }
        return -1;
    }

    /** Vergleicht den Schlüssel zwischen den Anführungszeichen mit {@code name}. */
    private static boolean passt(String json, int auf, int zu, String name) {
        int laenge = zu - auf - 1;
        if (laenge != name.length()) return false;
        return json.regionMatches(auf + 1, name, 0, laenge);
    }
}
