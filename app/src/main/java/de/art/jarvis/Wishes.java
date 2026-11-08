package de.art.jarvis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wünsche an die Entwicklung: Art sagt im Gespräch, was Jarvis besser machen soll, und der Wunsch
 * landet als Aufgabe (GitHub-Issue) im Repo – ohne dass Art zu Claude wechseln und abtippen muss.
 *
 * Reine Java-Logik ohne Android (Erkennung, Steuerzeile, Speicherformat), damit sie in den
 * automatischen Tests läuft. Das Senden macht {@link WishSender}.
 *
 * Speicherformat in den Einstellungen: eine Zeile je Wunsch, Felder durch Tabulator getrennt:
 * {@code status \t titel \t beschreibung}; status ist „offen“ oder „#12“ (Nummer des Issues).
 */
final class Wishes {
    static final String OPEN = "offen";
    /** Kennzeichen im Titel, damit die Entwicklung die Wünsche aus der App sofort findet. */
    static final String TITLE_PREFIX = "Wunsch: ";

    static final class Wish {
        final String status;
        final String title;
        final String body;

        Wish(String status, String title, String body) {
            this.status = status;
            this.title = title;
            this.body = body;
        }

        boolean open() {
            return OPEN.equals(status);
        }
    }

    private Wishes() {}

    // ------------------------------------------------------------ Sprachbefehl

    private static final Pattern COMMAND = Pattern.compile(
            "^(?:(?:notier|notiere|schreib|schreibe)(?:\\s+(?:dir|mal|bitte))*\\s+(?:f(?:ü|ue)r|an)\\s+die\\s+entwicklung"
                    + "|(?:ein\\s+)?wunsch\\s+(?:f(?:ü|ue)r|an)\\s+(?:die\\s+entwicklung|jarvis|die\\s+app)"
                    + "|verbesserungswunsch|feature[- ]?wunsch"
                    + "|f(?:ü|ue)r\\s+(?:die\\s+n(?:ä|ae)chste\\s+version|das\\s+n(?:ä|ae)chste\\s+(?:build|update)))"
                    + "(?=[\\s:,.]|$)\\s*[:,.-]?\\s*(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Wunschtext, wenn Art ausdrücklich etwas für die Entwicklung notiert – sonst {@code null}. */
    static String command(String utterance) {
        if (utterance == null) return null;
        String t = utterance.trim()
                .replaceAll("(?i)^\\s*(?:(?:hey|okay|ok)\\s+)?(?:jarvis\\s*[,:]?\\s*)?", "")
                .replaceAll("\\s+", " ")
                .trim();
        Matcher m = COMMAND.matcher(t);
        if (!m.find()) return null;
        String wish = m.group(1) == null ? "" : m.group(1).trim();
        wish = wish.replaceAll("^(?:dass|das)\\s+", "").trim();
        return wish.replaceAll("[\\s.!?]+", "").length() < 4 ? null : wish;
    }

    /** Kurzer Titel aus einem frei gesprochenen Wunsch: erster Satz, höchstens etwa 70 Zeichen. */
    static String titleFrom(String text) {
        String t = text == null ? "" : text.trim();
        int end = t.length();
        for (String stop : new String[] {". ", "! ", "? ", "; "}) {
            int i = t.indexOf(stop);
            if (i > 0 && i < end) end = i;
        }
        t = t.substring(0, end).trim();
        if (t.length() > 70) {
            int cut = t.lastIndexOf(' ', 70);
            t = t.substring(0, cut > 30 ? cut : 70).trim() + " …";
        }
        if (!t.isEmpty()) t = t.substring(0, 1).toUpperCase(Locale.GERMANY) + t.substring(1);
        return t;
    }

    // ------------------------------------------------------------ Steuerzeile von Claude

    /** [WUNSCH: Titel | Beschreibung] – die Beschreibung darf fehlen. */
    private static final Pattern TAG = Pattern.compile(
            "\\[\\s*WUNSCH\\s*:\\s*([^\\]|]+?)\\s*(?:\\|\\s*([^\\]]*?)\\s*)?\\]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Alle Wünsche aus einer Antwort, als {Titel, Beschreibung}. */
    static List<String[]> tags(String answer) {
        List<String[]> out = new ArrayList<>();
        if (answer == null) return out;
        Matcher m = TAG.matcher(answer);
        while (m.find()) {
            String title = clean(m.group(1));
            String body = m.group(2) == null ? "" : clean(m.group(2));
            if (!title.isEmpty()) out.add(new String[] {title, body});
        }
        return out;
    }

    static String stripTags(String answer) {
        if (answer == null) return "";
        return TAG.matcher(answer).replaceAll("").replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }

    // ------------------------------------------------------------ Speicher

    static List<Wish> load(String stored) {
        List<Wish> out = new ArrayList<>();
        if (stored == null) return out;
        for (String line : stored.split("\n")) {
            if (line.trim().isEmpty()) continue;
            String[] f = line.split("\t", -1);
            if (f.length < 2) continue;
            out.add(new Wish(f[0].trim(), f[1].trim(), f.length > 2 ? f[2].trim() : ""));
        }
        return out;
    }

    static String save(List<Wish> wishes) {
        StringBuilder sb = new StringBuilder();
        for (Wish w : wishes) {
            sb.append(clean(w.status)).append('\t').append(clean(w.title)).append('\t').append(clean(w.body)).append('\n');
        }
        return sb.toString();
    }

    /** Neuer, noch nicht gesendeter Wunsch; doppelte Titel werden nicht zweimal angelegt. */
    static String add(String stored, String title, String body) {
        List<Wish> list = load(stored);
        String t = clean(title);
        for (Wish w : list) {
            if (w.title.equalsIgnoreCase(t)) return save(list);
        }
        list.add(new Wish(OPEN, t, clean(body)));
        return save(list);
    }

    /** Wörter ohne Aussage für den Themenvergleich (alle Wünsche betreffen ohnehin Jarvis). */
    private static final java.util.Set<String> STOPWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "und", "oder", "als", "für", "fuer", "mit", "von", "vom", "beim", "bei", "zum", "zur", "der", "die", "das",
            "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "nicht", "noch", "mehr", "soll", "sollen",
            "wird", "werden", "bitte", "jarvis", "sir", "art", "wunsch", "auch", "sich"));

    /** Stichwörter eines Titels, auf fünf Buchstaben gekürzt („Priorität“ und „priorisieren“ → „prior“). */
    static java.util.Set<String> keywords(String title) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (title == null) return out;
        for (String w : title.toLowerCase(Locale.GERMANY).split("[^\\p{L}\\p{N}]+")) {
            if (w.length() < 3 || STOPWORDS.contains(w)) continue;
            out.add(w.length() > 5 ? w.substring(0, 5) : w);
        }
        return out;
    }

    /**
     * Der schon notierte Wunsch zum selben Thema, sonst null. Gleicher Titel – oder mindestens zwei gemeinsame
     * Stichwörter, die zwei Drittel des kürzeren Titels ausmachen. Anlass: Art nennt einen Wunsch im Gespräch
     * mehrmals, Claude formuliert ihn jedes Mal etwas anders („TFT-Coaching priorisieren“, „TFT-Coaching als
     * Priorität“, „TFT-Coaching beschleunigen“) – daraus wurden am 8. Nov. neun Issues für drei Wünsche.
     */
    static Wish similar(String stored, String title) {
        return similar(stored, title, "");
    }

    /** Füllwörter, die in Beschreibungen fast jedes Wunsches stehen – für den Vergleich mit Beschreibung. */
    private static final java.util.Set<String> TEXT_STOPWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "wie", "etwa", "möchte", "wünscht", "beispiel", "inklusive", "können", "kann", "sowie", "damit", "dass",
            "sobald", "während", "ohne", "über", "aktuell", "derzeit", "jedes", "immer", "ganz", "einbinden", "open",
            "source", "projekt"));

    private static java.util.Set<String> textKeywords(String text) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (text == null) return out;
        for (String w : text.toLowerCase(Locale.GERMANY).split("[^\\p{L}\\p{N}]+")) {
            if (w.length() < 3 || STOPWORDS.contains(w) || TEXT_STOPWORDS.contains(w)) continue;
            out.add(w.length() > 5 ? w.substring(0, 5) : w);
        }
        return out;
    }

    /**
     * Wie {@link #similar(String, String)}, zusätzlich mit der Beschreibung: Teilen die Titel mindestens ein Stichwort
     * und Titel plus Beschreibung mindestens drei, die 30 % des kürzeren Wunsches ausmachen, ist es dasselbe Thema.
     * Anlass, 8. Nov.: „Brutalere Stimme“, „Stimmenauswahl in der App“ und „Stimmauswahl mit harten, menschlichen
     * Stimmen“ (#50–#52) teilen im Titel nur „Stimme“, die Beschreibungen sind fast gleich. Gegen alle 33 echten
     * Wünsche bis 8. Nov. geprüft: 28 statt 11 doppelte Paare erkannt, keines falsch zusammengelegt (WishesTest).
     */
    static Wish similar(String stored, String title, String body) {
        String t = clean(title);
        java.util.Set<String> k = keywords(t);
        java.util.Set<String> kt = textKeywords(t);
        java.util.Set<String> kAll = textKeywords(t + " " + clean(body));
        for (Wish w : load(stored)) {
            if (w.title.equalsIgnoreCase(t)) return w;
            java.util.Set<String> o = keywords(w.title);
            if (k.isEmpty() || o.isEmpty()) continue;
            int common = 0;
            for (String word : k) if (o.contains(word)) common++;
            if (common >= 2 && common * 3 >= Math.min(k.size(), o.size()) * 2) return w;
            // Mit Beschreibung: gleiches Stichwort im Titel und genug gemeinsamer Inhalt.
            java.util.Set<String> ot = textKeywords(w.title);
            boolean titleShared = false;
            for (String word : kt) if (ot.contains(word)) titleShared = true;
            if (!titleShared) continue;
            java.util.Set<String> oAll = textKeywords(w.title + " " + w.body);
            int shared = 0;
            for (String word : kAll) if (oAll.contains(word)) shared++;
            if (shared >= 3 && shared * 10 >= Math.min(kAll.size(), oAll.size()) * 3) return w;
        }
        return null;
    }

    /** Markiert einen Wunsch als gesendet (Issue-Nummer). */
    static String markSent(String stored, String title, int issue) {
        List<Wish> list = load(stored);
        List<Wish> out = new ArrayList<>();
        for (Wish w : list) {
            out.add(w.title.equalsIgnoreCase(title) ? new Wish("#" + issue, w.title, w.body) : w);
        }
        return save(out);
    }

    /** JSON für „Create an issue“ (POST /repos/{owner}/{repo}/issues: title, body). */
    static String issueJson(String title, String body, String date) {
        String text = (body == null || body.isEmpty() ? title : body)
                + "\n\n---\nVon Jarvis aus einem Gespräch mit Art notiert (" + date + ").";
        return "{\"title\":" + AgentReply.jsonText(TITLE_PREFIX + title) + ",\"body\":" + AgentReply.jsonText(text) + "}";
    }

    /** Zeilen für die Anzeige in den Einstellungen, solange der Stand auf GitHub noch nicht geladen ist. */
    static String display(String stored) {
        return display(stored, null);
    }

    /** Stand eines Issues auf GitHub, wie {@link #display(String, java.util.Map)} ihn braucht. */
    static final String ISSUE_OPEN = "offen";
    static final String ISSUE_DONE = "erledigt";
    static final String ISSUE_MERGED = "zusammengelegt";

    /**
     * Zeilen für die Anzeige, mit dem echten Stand auf GitHub. Vorher stand vor jedem gesendeten Wunsch ein „✓“ –
     * gemeint war „gesendet“, Art las „erledigt“ (8. Nov.: 35 Häkchen, obwohl vieles noch offen war). Jetzt zuerst
     * die offenen, dann die erledigten; zusammengelegte Doppelte nur als Anzahl.
     *
     * @param states Nummer des Issues → {@link #ISSUE_OPEN}, {@link #ISSUE_DONE} oder {@link #ISSUE_MERGED};
     *               null = noch nicht geladen
     */
    static String display(String stored, java.util.Map<Integer, String> states) {
        List<Wish> list = load(stored);
        if (list.isEmpty()) return "Noch keine Wünsche notiert.";
        StringBuilder unsent = new StringBuilder();
        StringBuilder open = new StringBuilder();
        StringBuilder done = new StringBuilder();
        int merged = 0;
        for (Wish w : list) {
            if (w.open()) {
                unsent.append("✎ ").append(w.title).append(" (noch nicht gesendet)\n");
                continue;
            }
            String state = states == null ? null : states.get(issueNumber(w.status));
            if (ISSUE_MERGED.equals(state)) {
                merged++;
            } else if (ISSUE_DONE.equals(state)) {
                done.append("✓ ").append(w.title).append(" (").append(w.status).append(", erledigt)\n");
            } else if (ISSUE_OPEN.equals(state)) {
                open.append("○ ").append(w.title).append(" (").append(w.status).append(", offen)\n");
            } else {
                open.append("↗ ").append(w.title).append(" (").append(w.status).append(", gesendet)\n");
            }
        }
        StringBuilder sb = new StringBuilder().append(unsent).append(open).append(done);
        if (merged > 0) {
            sb.append("≈ ").append(merged).append(merged == 1 ? " doppelter Wunsch" : " doppelte Wünsche")
                    .append(" mit anderen zusammengelegt\n");
        }
        return sb.toString().trim();
    }

    /** „#17“ → 17, sonst 0. */
    static int issueNumber(String status) {
        if (status == null || !status.startsWith("#")) return 0;
        try {
            return Integer.parseInt(status.substring(1).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Stand der Issues aus der Antwort von GET /repos/{owner}/{repo}/issues?state=all (Felder number, state,
     * state_reason; Pull Requests haben zusätzlich „pull_request“ und werden übersprungen).
     */
    static java.util.Map<Integer, String> issueStates(String json) {
        java.util.Map<Integer, String> out = new java.util.HashMap<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null || o.has("pull_request")) continue;
                int n = o.optInt("number", 0);
                if (n <= 0) continue;
                String reason = o.optString("state_reason", "");
                if ("open".equals(o.optString("state"))) out.put(n, ISSUE_OPEN);
                else if ("not_planned".equals(reason) || "duplicate".equals(reason)) out.put(n, ISSUE_MERGED);
                else out.put(n, ISSUE_DONE);
            }
        } catch (org.json.JSONException ignored) {
            // kaputte Antwort – dann eben ohne Stand
        }
        return out;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').replaceAll(" {2,}", " ").trim();
    }
}
