package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Prüft echte Schreibabläufe mit einem Anbieter-Double, ohne persönliche Kalender anzufassen. */
public class CalendarWriteTest {
    private static final class Store implements CalendarWrite.Store {
        int events;
        int reminders;
        int minutes = -1;
        boolean acceptEvent = true;
        boolean acceptReminder = true;
        RuntimeException eventError;
        RuntimeException reminderError;

        @Override
        public boolean insertEvent() {
            events++;
            if (eventError != null) throw eventError;
            return acceptEvent;
        }

        @Override
        public boolean insertReminder(int value) {
            reminders++;
            assertEquals("Erinnerung erst nach dem Termin schreiben", 1, events);
            minutes = value;
            if (reminderError != null) throw reminderError;
            return acceptReminder;
        }
    }

    @Test
    public void erinnerungErstNachErfolgreichemTerminBestaetigen() {
        Store store = new Store();
        CalendarWrite.Result result = CalendarWrite.save(store, 30);
        assertEquals(1, store.events);
        assertEquals(1, store.reminders);
        assertEquals(30, store.minutes);
        assertEquals(CalendarWrite.Reminder.SAVED, result.reminder);
        assertEquals("Eingetragen: Zahnarzt, Erinnerung 30 Minuten vorher.", result.describe("Zahnarzt"));
    }

    @Test
    public void nullBeimErinnerungsInsertMeldetTeilerfolgOhneZweitenTermin() {
        Store store = new Store();
        store.acceptReminder = false; // Androids insert hat null statt einer Erinnerungs-URI geliefert.
        CalendarWrite.Result result = CalendarWrite.save(store, 30);
        assertEquals(1, store.events);
        assertEquals(1, store.reminders);
        assertEquals(CalendarWrite.Reminder.FAILED, result.reminder);
        assertTrue(result.describe("Zahnarzt").startsWith("Eingetragen: Zahnarzt."));
        assertTrue(result.describe("Zahnarzt").contains("Erinnerung konnte nicht bestätigt werden"));
        assertTrue(result.describe("Zahnarzt").contains("bitte nicht erneut anlegen"));
        assertFalse(result.describe("Zahnarzt").contains("30 Minuten vorher"));
    }

    @Test
    public void exceptionBeimErinnerungsInsertLaesstDenTerminBestehen() {
        Store store = new Store();
        store.reminderError = new SecurityException("Keine Berechtigung mehr");
        CalendarWrite.Result result = CalendarWrite.save(store, 10);
        assertEquals(1, store.events);
        assertEquals(1, store.reminders);
        assertEquals(CalendarWrite.Reminder.FAILED, result.reminder);
        assertFalse(result.describe("Zahnarzt").contains("10 Minuten vorher"));
    }

    @Test
    public void ohneErinnerungKeinErinnerungsInsert() {
        Store store = new Store();
        CalendarWrite.Result result = CalendarWrite.save(store, -1);
        assertEquals(1, store.events);
        assertEquals(0, store.reminders);
        assertEquals(CalendarWrite.Reminder.NONE, result.reminder);
        assertEquals("Eingetragen: Urlaub.", result.describe("Urlaub"));
    }

    @Test
    public void sofortigeErinnerungMitNullMinutenWirdGespeichert() {
        Store store = new Store();
        CalendarWrite.Result result = CalendarWrite.save(store, 0);
        assertEquals(1, store.reminders);
        assertEquals(0, store.minutes);
        assertEquals(CalendarWrite.Reminder.SAVED, result.reminder);
    }

    @Test
    public void abgelehnterTerminSchreibtKeineErinnerung() {
        Store store = new Store();
        store.acceptEvent = false;
        assertThrows(IllegalStateException.class, () -> CalendarWrite.save(store, 30));
        assertEquals(1, store.events);
        assertEquals(0, store.reminders);
    }

    @Test
    public void terminExceptionBleibtEinFehlerStattFalscherErfolgsmeldung() {
        Store store = new Store();
        store.eventError = new SecurityException("Keine Kalenderberechtigung");
        SecurityException thrown = assertThrows(SecurityException.class, () -> CalendarWrite.save(store, 30));
        assertSame(store.eventError, thrown);
        assertEquals(1, store.events);
        assertEquals(0, store.reminders);
    }
}
