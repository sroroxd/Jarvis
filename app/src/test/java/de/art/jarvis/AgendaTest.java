package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

/** Prüft die Datums-Logik der Kalender-Werkzeuge (Berliner Zeit, Winter- und Sommerzeit). */
public class AgendaTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static long at(int y, int m, int d, int h, int min) {
        return LocalDateTime.of(y, m, d, h, min).atZone(BERLIN).toInstant().toEpochMilli();
    }

    private static long utcDay(int y, int m, int d) {
        return LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    @Test
    public void terminOhneEndeDauertEineStunde() {
        Agenda.Slot s = Agenda.slot("2026-11-03T10:00", "", false, BERLIN);
        assertFalse(s.allDay);
        assertEquals(at(2026, 11, 3, 10, 0), s.begin);
        assertEquals(at(2026, 11, 3, 11, 0), s.end);
    }

    @Test
    public void leerzeichenSekundenUndZeitzoneWerdenToleriert() {
        assertEquals(at(2026, 11, 3, 10, 0), Agenda.slot("2026-11-03 10:00", null, false, BERLIN).begin);
        assertEquals(at(2026, 11, 3, 10, 0), Agenda.slot("2026-11-03T10:00:00+01:00", null, false, BERLIN).begin);
    }

    @Test
    public void nurDatumIstGanztaegigInUtc() {
        Agenda.Slot s = Agenda.slot("2026-11-03", "", false, BERLIN);
        assertTrue(s.allDay);
        assertEquals(utcDay(2026, 11, 3), s.begin);
        assertEquals(utcDay(2026, 11, 4), s.end);
    }

    @Test
    public void mehrtaegigGanztaegigEndetNachDemLetztenTag() {
        Agenda.Slot s = Agenda.slot("2026-12-24", "2026-12-26", true, BERLIN);
        assertEquals(utcDay(2026, 12, 24), s.begin);
        assertEquals(utcDay(2026, 12, 27), s.end);
    }

    @Test(expected = IllegalArgumentException.class)
    public void endeVorBeginnWirdAbgelehnt() {
        Agenda.slot("2026-11-03T10:00", "2026-11-03T09:00", false, BERLIN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void beginnInZeitumstellungslueckeWirdNichtHinterDasEndeVerschoben() {
        // Früher wurde 02:30 still zu 03:30, also hinter das ausdrücklich genannte Ende 03:00.
        Agenda.slot("2026-03-29T02:30", "2026-03-29T03:00", false, BERLIN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void beginnInZeitumstellungslueckeWirdAuchOhneEndeAbgelehnt() {
        Agenda.slot("2026-03-29T02:30", "", false, BERLIN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void ausdruecklichesEndeInZeitumstellungslueckeWirdAbgelehnt() {
        Agenda.slot("2026-03-29T01:30", "2026-03-29T02:30", false, BERLIN);
    }

    @Test
    public void eineStundeOhneEndeUeberspringtDieFruehlingsluecke() {
        Agenda.Slot s = Agenda.slot("2026-03-29T01:30", "", false, BERLIN);
        assertEquals(3_600_000L, s.end - s.begin);
        assertEquals(Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(), s.end);
        assertEquals("Sonntag, 29. März 2026, 01:30 bis 03:30 Uhr: Test",
                Agenda.describe(new Agenda.Entry("Test", s.begin, s.end, false, "", ""), BERLIN));
    }

    @Test
    public void eineStundeOhneEndeBleibtAuchBeiRueckstellungEineStunde() {
        Agenda.Slot s = Agenda.slot("2026-10-25T02:30", "", false, BERLIN);
        assertEquals(3_600_000L, s.end - s.begin);
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), s.begin);
        assertEquals(Instant.parse("2026-10-25T01:30:00Z").toEpochMilli(), s.end);
    }

    @Test
    public void gueltigerExpliziterZeitraumUeberZeitumstellungBleibtErhalten() {
        Agenda.Slot s = Agenda.slot("2026-03-29T01:30", "2026-03-29T03:30", false, BERLIN);
        assertEquals(Instant.parse("2026-03-29T00:30:00Z").toEpochMilli(), s.begin);
        assertEquals(Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(), s.end);
    }

    @Test
    public void ganztaegigBleibtAmZeitumstellungstagUtc() {
        Agenda.Slot s = Agenda.slot("2026-03-29", "", true, BERLIN);
        assertTrue(s.allDay);
        assertEquals(utcDay(2026, 3, 29), s.begin);
        assertEquals(utcDay(2026, 3, 30), s.end);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unlesbaresDatumWirdAbgelehnt() {
        Agenda.parseDay("morgen");
    }

    @Test
    public void ganztaegigerTerminErscheintNurAnSeinemTag() {
        Agenda.Entry e = new Agenda.Entry("Urlaub", utcDay(2026, 11, 3), utcDay(2026, 11, 4), true, "", "");
        assertTrue(Agenda.overlaps(e, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), BERLIN));
        // Wichtig: in UTC endet der Tag um 01:00 Berliner Zeit – darf trotzdem nicht am Folgetag auftauchen.
        assertFalse(Agenda.overlaps(e, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4), BERLIN));
        assertFalse(Agenda.overlaps(e, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), BERLIN));
    }

    @Test
    public void spaeterAbendterminGehoertZumRichtigenTag() {
        Agenda.Entry e = new Agenda.Entry("Kino", at(2026, 7, 10, 23, 30), at(2026, 7, 11, 0, 0), false, "", "");
        assertTrue(Agenda.overlaps(e, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 10), BERLIN));
        assertFalse(Agenda.overlaps(e, LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 11), BERLIN));
    }

    @Test
    public void beschreibungIstDeutschUndVollstaendig() {
        Agenda.Entry e = new Agenda.Entry("Zahnarzt", at(2026, 11, 3, 10, 0), at(2026, 11, 3, 11, 0), false,
                "Praxis Müller", "Google");
        assertEquals("Dienstag, 3. November 2026, 10:00 bis 11:00 Uhr: Zahnarzt (Ort: Praxis Müller) [Kalender: Google]",
                Agenda.describe(e, BERLIN));
    }

    @Test
    public void listeUndLeererTag() {
        LocalDate d = LocalDate.of(2026, 11, 3);
        assertEquals("Keine Termine am Dienstag, 3. November 2026.",
                Agenda.describe(Collections.emptyList(), d, d, BERLIN));
        Agenda.Entry a = new Agenda.Entry("Urlaub", utcDay(2026, 11, 3), utcDay(2026, 11, 4), true, null, null);
        Agenda.Entry b = new Agenda.Entry(" ", at(2026, 11, 3, 18, 0), at(2026, 11, 3, 18, 0), false, null, null);
        assertEquals("Termine am Dienstag, 3. November 2026:\n"
                        + "- Dienstag, 3. November 2026, ganztägig: Urlaub\n"
                        + "- Dienstag, 3. November 2026, 18:00 Uhr: (ohne Titel)",
                Agenda.describe(Arrays.asList(a, b), d, d, BERLIN));
    }
}
