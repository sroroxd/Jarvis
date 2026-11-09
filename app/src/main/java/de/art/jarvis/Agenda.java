package de.art.jarvis;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Datums-Logik der Kalender-Werkzeuge: Eingaben von Claude lesen, Zeiträume prüfen, Termine beschreiben.
 * Reines Java ohne Android-Abhängigkeiten, daher automatisch testbar.
 *
 * Ganztägige Termine speichert Android in UTC (Mitternacht bis Mitternacht), normale Termine als Zeitpunkt.
 */
final class Agenda {
    private Agenda() {}

    /** Ein Termin, wie er aus dem Kalender kommt. */
    static final class Entry {
        final String title;
        final long begin;
        final long end;
        final boolean allDay;
        final String location;
        final String calendar;

        Entry(String title, long begin, long end, boolean allDay, String location, String calendar) {
            this.title = title == null || title.trim().isEmpty() ? "(ohne Titel)" : title.trim();
            this.begin = begin;
            this.end = end;
            this.allDay = allDay;
            this.location = location == null ? "" : location.trim();
            this.calendar = calendar == null ? "" : calendar.trim();
        }
    }

    /** Ein neuer Termin, fertig zum Eintragen. */
    static final class Slot {
        final long begin;
        final long end;
        final boolean allDay;

        Slot(long begin, long end, boolean allDay) {
            this.begin = begin;
            this.end = end;
            this.allDay = allDay;
        }
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMANY);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY);

    /** „2026-11-03“ → Datum. Wirft IllegalArgumentException mit verständlicher Meldung. */
    static LocalDate parseDay(String s) {
        if (s == null || s.trim().isEmpty()) throw new IllegalArgumentException("Datum fehlt (Format JJJJ-MM-TT).");
        String t = s.trim();
        if (t.length() > 10) t = t.substring(0, 10); // „2026-11-03T10:00“ → nur das Datum
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Datum nicht lesbar: „" + s + "“ (Format JJJJ-MM-TT).");
        }
    }

    /** „2026-11-03T10:00“ (auch mit Leerzeichen oder Sekunden) → Ortszeit. */
    static LocalDateTime parseDateTime(String s) {
        if (s == null || s.trim().isEmpty()) throw new IllegalArgumentException("Uhrzeit fehlt (Format JJJJ-MM-TTTHH:MM).");
        String t = s.trim().replace(' ', 'T');
        if (t.length() > 19) t = t.substring(0, 19); // Zeitzonen-Zusätze wie „Z“ oder „+01:00“ ignorieren: gemeint ist Ortszeit
        try {
            return LocalDateTime.parse(t);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Uhrzeit nicht lesbar: „" + s + "“ (Format JJJJ-MM-TTTHH:MM).");
        }
    }

    /**
     * Baut aus Claudes Angaben den einzutragenden Zeitraum.
     * Ohne Ende: normale Termine dauern eine Stunde, ganztägige einen Tag.
     * Ganztägig auch dann, wenn „beginn“ nur ein Datum ohne Uhrzeit ist.
     */
    static Slot slot(String beginn, String ende, boolean ganztaegig, ZoneId zone) {
        boolean dateOnly = beginn != null && beginn.trim().length() <= 10;
        if (ganztaegig || dateOnly) {
            LocalDate first = parseDay(beginn);
            LocalDate last = ende == null || ende.trim().isEmpty() ? first : parseDay(ende);
            if (last.isBefore(first)) throw new IllegalArgumentException("Das Ende liegt vor dem Beginn.");
            long b = first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long e = last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            return new Slot(b, e, true);
        }
        LocalDateTime start = parseDateTime(beginn);
        long b = localInstant(start, zone);
        long e;
        if (ende == null || ende.trim().isEmpty()) {
            // Eine Stunde dauert auch bei einer Zeitumstellung genau 60 Minuten. Die Uhrzeit kann
            // dabei z. B. von 01:30 auf 03:30 springen oder im Herbst zweimal 02:30 anzeigen.
            e = Instant.ofEpochMilli(b).plusSeconds(3600).toEpochMilli();
        } else {
            LocalDateTime stop = parseDateTime(ende);
            if (!stop.isAfter(start)) throw new IllegalArgumentException("Das Ende muss nach dem Beginn liegen.");
            e = localInstant(stop, zone);
        }
        if (e <= b) throw new IllegalArgumentException("Das Ende muss nach dem Beginn liegen.");
        return new Slot(b, e, false);
    }

    /** Nicht existierende Ortszeiten nie still verschieben oder als verkehrten Zeitraum speichern. */
    private static long localInstant(LocalDateTime time, ZoneId zone) {
        if (zone.getRules().getValidOffsets(time).isEmpty()) {
            throw new IllegalArgumentException("Die Uhrzeit „" + time + "“ gibt es wegen der Zeitumstellung nicht. "
                    + "Bitte eine andere Uhrzeit nennen.");
        }
        return time.atZone(zone).toInstant().toEpochMilli();
    }

    /** Erster Moment des Tages „from“ bis erster Moment nach dem Tag „to“ – in Ortszeit, als Millisekunden. */
    static long startOf(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** Liegt der Termin (auch teilweise) in den Tagen from … to (einschließlich)? */
    static boolean overlaps(Entry e, LocalDate from, LocalDate to, ZoneId zone) {
        if (e.allDay) {
            LocalDate first = Instant.ofEpochMilli(e.begin).atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate endExclusive = Instant.ofEpochMilli(e.end).atZone(ZoneOffset.UTC).toLocalDate();
            if (!endExclusive.isAfter(first)) endExclusive = first.plusDays(1);
            return !first.isAfter(to) && endExclusive.isAfter(from);
        }
        long rangeStart = startOf(from, zone);
        long rangeEnd = startOf(to.plusDays(1), zone);
        long end = Math.max(e.end, e.begin);
        return e.begin < rangeEnd && (end > rangeStart || (end == e.begin && e.begin >= rangeStart));
    }

    /** Eine Zeile pro Termin – Text für Claude, nicht zum direkten Vorlesen. */
    static String describe(List<Entry> entries, LocalDate from, LocalDate to, ZoneId zone) {
        String range = from.equals(to) ? DAY.format(from) : DAY.format(from) + " bis " + DAY.format(to);
        if (entries.isEmpty()) return "Keine Termine am " + range + ".";
        StringBuilder sb = new StringBuilder("Termine ").append(from.equals(to) ? "am " : "vom ").append(range).append(":\n");
        for (Entry e : entries) sb.append("- ").append(describe(e, zone)).append('\n');
        return sb.toString().trim();
    }

    static String describe(Entry e, ZoneId zone) {
        StringBuilder sb = new StringBuilder();
        if (e.allDay) {
            LocalDate first = Instant.ofEpochMilli(e.begin).atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate last = Instant.ofEpochMilli(e.end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
            sb.append(DAY.format(first));
            if (last.isAfter(first)) sb.append(" bis ").append(DAY.format(last));
            sb.append(", ganztägig");
        } else {
            ZonedDateTime b = Instant.ofEpochMilli(e.begin).atZone(zone);
            ZonedDateTime en = Instant.ofEpochMilli(Math.max(e.end, e.begin)).atZone(zone);
            sb.append(DAY.format(b)).append(", ").append(TIME.format(b));
            if (en.isAfter(b)) {
                sb.append(" bis ");
                if (!en.toLocalDate().equals(b.toLocalDate())) sb.append(DAY.format(en)).append(", ");
                sb.append(TIME.format(en));
            }
            sb.append(" Uhr");
        }
        sb.append(": ").append(e.title);
        if (!e.location.isEmpty()) sb.append(" (Ort: ").append(e.location).append(')');
        if (!e.calendar.isEmpty()) sb.append(" [Kalender: ").append(e.calendar).append(']');
        return sb.toString();
    }
}
