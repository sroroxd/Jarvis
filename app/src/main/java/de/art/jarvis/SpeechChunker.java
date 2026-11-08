package de.art.jarvis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Zerlegt eine gestreamte Antwort in sprechbare Sätze: Sobald ein Satz fertig ist, kann ihn die
 * Sprachausgabe vorlesen, während Claude noch am nächsten schreibt.
 *
 * <ul>
 *   <li>Satzende ist „.“, „!“ oder „?“ mit Leerraum dahinter (auch nach Anführungszeichen), oder ein Zeilenumbruch.</li>
 *   <li>Kein Satzende nach Abkürzungen („z. B.“, „ca.“, „Nr.“) und nach einer Zahl am Satzanfang
 *       („51. Soll ich …“ bleibt zusammen, „1. Dann …“ in einer Liste ebenso).</li>
 *   <li>Steuerzeilen in eckigen Klammern ([MERKEN: …], [WUNSCH: …], [DAUERGESPRÄCH: AN]) werden nie
 *       vorgelesen – der Dienst wertet sie am Ende aus der vollständigen Antwort aus.</li>
 *   <li>Jeder Satz läuft durch {@link Text#cleanForSpeech}.</li>
 * </ul>
 *
 * <p>Reine Java-Logik ohne Android, daher automatisch testbar.
 */
final class SpeechChunker {
    private static final Set<String> ABBREVIATIONS = new HashSet<>(Arrays.asList(
            "z", "b", "d", "h", "u", "a", "o", "s", "v", "ca", "bzw", "usw", "etc", "dr", "nr", "st", "vgl", "ggf",
            "evtl", "inkl", "bspw", "zb", "ua", "dh", "std", "min", "sek", "tsd", "mio", "mrd", "mr", "mrs", "prof",
            "abs", "abb", "allg", "jh", "jhd", "geb", "max", "sog", "zzgl", "ff"));
    private static final String CLOSERS = "\"'“”„»«)’";

    private final StringBuilder buf = new StringBuilder();
    private int bracketDepth;
    /** Beginnt der Puffer gerade an einem Zeilenanfang (nach einem Umbruch)? */
    private boolean lineStart;

    /** Neues Textstück; liefert alle Sätze, die damit fertig geworden sind (evtl. keinen). */
    List<String> feed(String delta) {
        if (delta != null) {
            for (int i = 0; i < delta.length(); i++) {
                char c = delta.charAt(i);
                if (c == '[') {
                    bracketDepth++;
                    continue;
                }
                if (c == ']' && bracketDepth > 0) {
                    bracketDepth--;
                    continue;
                }
                if (bracketDepth == 0) buf.append(c);
            }
        }
        return drain(false);
    }

    /** Antwort zu Ende: der Rest, bereinigt (leer, wenn nichts mehr zu sagen ist). */
    String finish() {
        bracketDepth = 0;
        List<String> rest = drain(true);
        return String.join(" ", rest);
    }

    private List<String> drain(boolean all) {
        List<String> out = new ArrayList<>();
        int end;
        while ((end = boundary()) >= 0) {
            String sentence = buf.substring(0, end);
            boolean newline = end < buf.length() && buf.charAt(end) == '\n';
            buf.delete(0, end);
            add(out, sentence);
            lineStart = newline;
            // Leerraum zwischen den Sätzen fällt weg.
            while (buf.length() > 0 && Character.isWhitespace(buf.charAt(0))) {
                if (buf.charAt(0) == '\n') lineStart = true;
                buf.deleteCharAt(0);
            }
        }
        if (all && buf.length() > 0) {
            add(out, buf.toString());
            buf.setLength(0);
        }
        return out;
    }

    private void add(List<String> out, String sentence) {
        String s = sentence.trim();
        // Nummerierung am Zeilenanfang einer mehrzeiligen Antwort („2. Dann rollen“) nicht vorlesen.
        if (lineStart) s = s.replaceFirst("^\\d+[.)]\\s+", "");
        String clean = Text.cleanForSpeech(s);
        if (!clean.isEmpty()) out.add(clean);
    }

    /** Position direkt hinter dem ersten fertigen Satz, oder -1. */
    private int boundary() {
        for (int i = 0; i < buf.length(); i++) {
            char c = buf.charAt(i);
            if (c == '\n') {
                if (buf.substring(0, i).trim().isEmpty()) continue; // Leerzeilen am Anfang
                return i;
            }
            if (c != '.' && c != '!' && c != '?') continue;
            int j = i + 1;
            while (j < buf.length() && CLOSERS.indexOf(buf.charAt(j)) >= 0) j++;
            if (j >= buf.length()) return -1; // erst sehen, was folgt
            if (!Character.isWhitespace(buf.charAt(j))) continue; // „3.5“, „…?“-Mitte
            if (c == '.' && !endsSentence(i)) continue;
            return j;
        }
        return -1;
    }

    /** Ist der Punkt an Position i ein Satzende? Nicht nach Abkürzungen und nicht nach einer Zahl am Satzanfang. */
    private boolean endsSentence(int dot) {
        int start = dot;
        while (start > 0 && !Character.isWhitespace(buf.charAt(start - 1))) start--;
        String token = buf.substring(start, dot).replaceAll("[^\\p{L}\\p{N}]", "");
        if (token.isEmpty()) return true;
        if (token.chars().allMatch(Character::isDigit)) {
            // „51. Soll ich …“ / „1. Dann …“: Zahl als erstes Wort → kein Satzende. „Stufe 6. Danach …“ → Satzende.
            return !buf.substring(0, start).trim().isEmpty();
        }
        return !ABBREVIATIONS.contains(token.toLowerCase(Locale.GERMANY));
    }
}
