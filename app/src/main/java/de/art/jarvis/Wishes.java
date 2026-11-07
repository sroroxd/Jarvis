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

    /** Zeilen für die Anzeige in den Einstellungen. */
    static String display(String stored) {
        List<Wish> list = load(stored);
        if (list.isEmpty()) return "Noch keine Wünsche notiert.";
        StringBuilder sb = new StringBuilder();
        for (Wish w : list) {
            sb.append(w.open() ? "○ " : "✓ ").append(w.title);
            sb.append(w.open() ? " (noch nicht gesendet)" : " (Aufgabe " + w.status + ")").append('\n');
        }
        return sb.toString().trim();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').replaceAll(" {2,}", " ").trim();
    }
}
