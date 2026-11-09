package de.art.jarvis;

/**
 * Speichert Termin und optionale Erinnerung getrennt. Ist nur die Erinnerung fehlgeschlagen,
 * bleibt der Termin bestehen und wird nicht noch einmal angelegt. Ohne Android automatisch testbar.
 */
final class CalendarWrite {
    private CalendarWrite() {}

    interface Store {
        /** true nur, wenn der Anbieter eine URI für den neuen Termin zurückgegeben hat. */
        boolean insertEvent();

        /** true nur, wenn auch die Erinnerung vom Anbieter angenommen wurde. */
        boolean insertReminder(int minutes);
    }

    enum Reminder {
        NONE, SAVED, FAILED
    }

    static final class Result {
        final Reminder reminder;
        final int minutes;

        private Result(Reminder reminder, int minutes) {
            this.reminder = reminder;
            this.minutes = minutes;
        }

        String describe(String eventDescription) {
            String event = "Eingetragen: " + eventDescription;
            if (reminder == Reminder.SAVED) return event + ", Erinnerung " + minutes + " Minuten vorher.";
            if (reminder == Reminder.FAILED) {
                return event + ". Die Erinnerung konnte nicht bestätigt werden; bitte im Kalender prüfen. "
                        + "Der Termin ist bereits gespeichert; bitte nicht erneut anlegen.";
            }
            return event + ".";
        }
    }

    /** Terminfehler bleiben Fehler; Erinnerungsfehler sind ein Teilerfolg ohne erneuten Termin-Insert. */
    static Result save(Store store, int reminderMinutes) {
        if (!store.insertEvent()) throw new IllegalStateException("Der Kalender hat den Termin nicht angenommen.");
        if (reminderMinutes < 0) return new Result(Reminder.NONE, reminderMinutes);
        try {
            return new Result(store.insertReminder(reminderMinutes) ? Reminder.SAVED : Reminder.FAILED,
                    reminderMinutes);
        } catch (RuntimeException ignored) {
            // Der Termin wurde bereits angelegt. Die Erinnerung lässt sich nicht bestätigen.
            return new Result(Reminder.FAILED, reminderMinutes);
        }
    }
}
