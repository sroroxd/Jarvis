package de.art.jarvis;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Testbericht vom echten Handy: Die Debug-App merkt sich die letzten Ereignisse der Sprachkette (was gehört wurde,
 * wann Art dazwischengeredet hat, Echo und Schwelle, Antwortzeiten, Fehler der Spracherkennung). Art teilt den
 * Bericht per Knopf und fügt ihn in den Chat mit Claude ein – so sieht Claude den Gerätetest auf dem S24 Ultra,
 * ohne Rechner, ohne Token und ohne dass etwas öffentlich auf GitHub landet.
 *
 * Reine Java-Logik, siehe TestReportTest. Zugriff von mehreren Threads, daher synchronisiert.
 */
final class TestReport {
    static final int MAX_LINES = 400;
    /** Lange Zeilen (ganze Antworten) kürzen – der Bericht soll in eine Chat-Nachricht passen. */
    static final int MAX_LINE = 240;

    private final ArrayDeque<String> lines = new ArrayDeque<>();

    synchronized void add(long wallMillis, String line) {
        if (line == null) return;
        String l = line.replace('\n', ' ').trim();
        if (l.length() > MAX_LINE) l = l.substring(0, MAX_LINE) + " …";
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(wallMillis);
        lines.addLast(String.format(Locale.GERMANY, "%02d:%02d:%02d.%d %s", c.get(java.util.Calendar.HOUR_OF_DAY),
                c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND),
                c.get(java.util.Calendar.MILLISECOND) / 100, l));
        while (lines.size() > MAX_LINES) lines.removeFirst();
    }

    synchronized void clear() {
        lines.clear();
    }

    synchronized int size() {
        return lines.size();
    }

    /** Der ganze Bericht mit Kopfzeilen (Gerät, Build, Einstellungen), neueste Zeile zuletzt. */
    synchronized String text(String header) {
        StringBuilder sb = new StringBuilder("JARVIS-TESTBERICHT\n");
        if (header != null && !header.isEmpty()) sb.append(header.trim()).append('\n');
        sb.append("---\n");
        if (lines.isEmpty()) sb.append("(noch keine Ereignisse – Jarvis starten und sprechen)\n");
        for (String l : lines) sb.append(l).append('\n');
        return sb.toString();
    }
}
