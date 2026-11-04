package de.art.jarvis;

import java.util.ArrayList;
import java.util.List;

/**
 * Gesprächsverlauf für den Startbildschirm – vollständig statt nur des letzten Wortwechsels.
 *
 * Reine Java-Logik ohne Android, damit sie in den automatischen Tests läuft.
 *
 * Regeln, damit wachsende Texte nicht als viele Einträge erscheinen:
 * - Art: Folgt ein Art-Text direkt auf einen Art-Text, ist es dieselbe Äußerung, die die
 *   Spracherkennung gerade verfeinert (Zwischenergebnisse) – der Eintrag wird ersetzt.
 * - Jarvis: Beginnt der neue Text mit dem bisherigen, wächst dieselbe Antwort (Gesprächsmodus
 *   liefert sie stückweise) – der Eintrag wird ersetzt. Ein anderer Text ist eine neue Meldung.
 */
final class ChatLog {
    /** Mehr behält der Startbildschirm nicht – ältere Einträge fallen vorne heraus. */
    static final int MAX_ENTRIES = 80;

    static final class Entry {
        final boolean art;
        final String text;

        Entry(boolean art, String text) {
            this.art = art;
            this.text = text;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    synchronized void art(String text) {
        String t = clean(text);
        if (t.isEmpty()) return;
        Entry last = last();
        if (last != null && last.art) {
            entries.set(entries.size() - 1, new Entry(true, t));
        } else {
            add(new Entry(true, t));
        }
    }

    synchronized void jarvis(String text) {
        String t = clean(text);
        if (t.isEmpty()) return;
        Entry last = last();
        if (last != null && !last.art && t.startsWith(last.text)) {
            entries.set(entries.size() - 1, new Entry(false, t));
        } else {
            add(new Entry(false, t));
        }
    }

    synchronized void clear() {
        entries.clear();
    }

    /** Kopie – der Startbildschirm darf die Liste lesen, während der Dienst weiterschreibt. */
    synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    synchronized int size() {
        return entries.size();
    }

    private Entry last() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    private void add(Entry e) {
        entries.add(e);
        while (entries.size() > MAX_ENTRIES) entries.remove(0);
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }
}
