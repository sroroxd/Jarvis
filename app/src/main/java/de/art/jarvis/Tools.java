package de.art.jarvis;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Jarvis' „Hände“ auf dem Handy: Werkzeuge, die Claude aufrufen kann.
 * Derzeit: Kalender lesen und Termine eintragen (alle synchronisierten Kalender, z. B. Google und Samsung).
 * Die Websuche läuft nicht hier, sondern direkt bei Anthropic (siehe {@link ClaudeClient}).
 */
final class Tools implements ClaudeClient.ToolRunner {
    static final String CALENDAR_READ = "kalender_lesen";
    static final String CALENDAR_ADD = "termin_anlegen";
    private static final int MAX_ENTRIES = 40;
    private static final int DEFAULT_REMINDER_MIN = 30;

    private final Context context;

    Tools(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public JSONArray definitions() throws JSONException {
        JSONArray defs = new JSONArray();
        defs.put(new JSONObject()
                .put("name", CALENDAR_READ)
                .put("description", "Liest Arts Termine aus dem Kalender seines Handys (alle synchronisierten Kalender, "
                        + "z. B. Google und Samsung). Nutze das bei Fragen wie „Was steht morgen an?“ oder „Hab ich Freitag Zeit?“. "
                        + "Rechne relative Angaben wie „morgen“ oder „nächste Woche“ mit der aktuellen Zeit aus der Systemanweisung um.")
                .put("input_schema", new JSONObject()
                        .put("type", "object")
                        .put("properties", new JSONObject()
                                .put("von", new JSONObject().put("type", "string")
                                        .put("description", "Erster Tag, Format JJJJ-MM-TT"))
                                .put("bis", new JSONObject().put("type", "string")
                                        .put("description", "Letzter Tag einschließlich, Format JJJJ-MM-TT. Weglassen = nur der Tag „von“.")))
                        .put("required", new JSONArray().put("von"))));
        defs.put(new JSONObject()
                .put("name", CALENDAR_ADD)
                .put("description", "Trägt einen neuen Termin in Arts Handy-Kalender ein. Nur verwenden, wenn Art ausdrücklich "
                        + "einen Termin eintragen lassen will. Fehlt bei einem nicht ganztägigen Termin die Uhrzeit, frag kurz nach, statt zu raten.")
                .put("input_schema", new JSONObject()
                        .put("type", "object")
                        .put("properties", new JSONObject()
                                .put("titel", new JSONObject().put("type", "string")
                                        .put("description", "Kurzer Titel, z. B. „Zahnarzt“"))
                                .put("beginn", new JSONObject().put("type", "string")
                                        .put("description", "Ortszeit im Format JJJJ-MM-TTTHH:MM, bei ganztägigen Terminen nur JJJJ-MM-TT"))
                                .put("ende", new JSONObject().put("type", "string")
                                        .put("description", "Wie beginn. Weglassen = eine Stunde bzw. ein Tag"))
                                .put("ganztaegig", new JSONObject().put("type", "boolean"))
                                .put("ort", new JSONObject().put("type", "string"))
                                .put("erinnerung_minuten", new JSONObject().put("type", "integer")
                                        .put("description", "Erinnerung so viele Minuten vorher. Weglassen = 30 (ganztägig: keine)")))
                        .put("required", new JSONArray().put("titel").put("beginn"))));
        return defs;
    }

    @Override
    public JSONObject run(JSONObject toolUse) throws JSONException {
        String name = toolUse.optString("name");
        JSONObject input = toolUse.optJSONObject("input");
        if (input == null) input = new JSONObject();
        String text;
        boolean error = false;
        try {
            switch (name) {
                case CALENDAR_READ:
                    text = readCalendar(input);
                    break;
                case CALENDAR_ADD:
                    text = addEvent(input);
                    break;
                default:
                    text = "Unbekanntes Werkzeug: " + name;
                    error = true;
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            text = e.getMessage();
            error = true;
        } catch (SecurityException e) {
            text = noPermission();
            error = true;
        } catch (Exception e) {
            text = "Fehler im Werkzeug „" + name + "“: " + e.getMessage();
            error = true;
        }
        JSONObject result = new JSONObject()
                .put("type", "tool_result")
                .put("tool_use_id", toolUse.optString("id"))
                .put("content", text == null ? "" : text);
        if (error) result.put("is_error", true);
        return result;
    }

    // ---------------------------------------------------------------- Kalender

    private boolean granted(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static String noPermission() {
        return "Jarvis hat keinen Zugriff auf den Kalender. Art muss ihn erlauben: Jarvis-App öffnen, auf den Kern tippen "
                + "und den Kalenderzugriff bestätigen – oder in den Android-Einstellungen unter Apps → Jarvis → Berechtigungen.";
    }

    private String readCalendar(JSONObject input) {
        if (!granted(Manifest.permission.READ_CALENDAR)) return failWith(noPermission());
        ZoneId zone = ZoneId.systemDefault();
        LocalDate from = Agenda.parseDay(input.optString("von"));
        String bisRaw = input.optString("bis", "");
        LocalDate to = bisRaw.trim().isEmpty() ? from : Agenda.parseDay(bisRaw);
        if (to.isBefore(from)) {
            LocalDate t = from;
            from = to;
            to = t;
        }
        if (to.isAfter(from.plusDays(62))) to = from.plusDays(62); // höchstens gut zwei Monate auf einmal

        // Großzügig abfragen (ganztägige Termine liegen in UTC), danach genau filtern.
        Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, Agenda.startOf(from.minusDays(1), zone));
        ContentUris.appendId(uri, Agenda.startOf(to.plusDays(2), zone));
        String[] projection = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        };
        List<Agenda.Entry> entries = new ArrayList<>();
        try (Cursor c = context.getContentResolver().query(uri.build(), projection,
                CalendarContract.Instances.VISIBLE + "=1", null, CalendarContract.Instances.BEGIN + " ASC")) {
            if (c != null) {
                while (c.moveToNext() && entries.size() < MAX_ENTRIES) {
                    Agenda.Entry e = new Agenda.Entry(c.getString(0), c.getLong(1), c.getLong(2),
                            c.getInt(3) == 1, c.getString(4), c.getString(5));
                    if (Agenda.overlaps(e, from, to, zone)) entries.add(e);
                }
            }
        }
        return Agenda.describe(entries, from, to, zone);
    }

    private String addEvent(JSONObject input) {
        if (!granted(Manifest.permission.WRITE_CALENDAR) || !granted(Manifest.permission.READ_CALENDAR)) {
            return failWith(noPermission());
        }
        String title = input.optString("titel", "").trim();
        if (title.isEmpty()) throw new IllegalArgumentException("Titel fehlt.");
        ZoneId zone = ZoneId.systemDefault();
        Agenda.Slot slot = Agenda.slot(input.optString("beginn"), input.optString("ende", ""),
                input.optBoolean("ganztaegig", false), zone);

        long calendarId = -1;
        String calendarName = "";
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.IS_PRIMARY,
        };
        String selection = CalendarContract.Calendars.VISIBLE + "=1 AND "
                + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=" + CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;
        int best = -1;
        try (Cursor c = context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, projection,
                selection, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    // Bevorzugt: Hauptkalender des Google-Kontos, dann irgendein Google-Kalender, dann der Rest.
                    boolean google = "com.google".equals(c.getString(2));
                    boolean primary = !c.isNull(3) && c.getInt(3) == 1;
                    int score = (google ? 2 : 0) + (primary ? 1 : 0);
                    if (score > best) {
                        best = score;
                        calendarId = c.getLong(0);
                        calendarName = c.getString(1);
                    }
                }
            }
        }
        if (calendarId < 0) throw new IllegalStateException("Auf dem Handy ist kein beschreibbarer Kalender eingerichtet.");

        ContentValues v = new ContentValues();
        v.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        v.put(CalendarContract.Events.TITLE, title);
        v.put(CalendarContract.Events.DTSTART, slot.begin);
        v.put(CalendarContract.Events.DTEND, slot.end);
        v.put(CalendarContract.Events.ALL_DAY, slot.allDay ? 1 : 0);
        v.put(CalendarContract.Events.EVENT_TIMEZONE, slot.allDay ? "UTC" : TimeZone.getDefault().getID());
        String place = input.optString("ort", "").trim();
        if (!place.isEmpty()) v.put(CalendarContract.Events.EVENT_LOCATION, place);
        ContentResolver cr = context.getContentResolver();
        Uri created = cr.insert(CalendarContract.Events.CONTENT_URI, v);
        if (created == null) throw new IllegalStateException("Der Kalender hat den Termin nicht angenommen.");

        int reminder = input.has("erinnerung_minuten")
                ? input.optInt("erinnerung_minuten", DEFAULT_REMINDER_MIN)
                : (slot.allDay ? -1 : DEFAULT_REMINDER_MIN);
        if (reminder >= 0) {
            ContentValues r = new ContentValues();
            r.put(CalendarContract.Reminders.EVENT_ID, ContentUris.parseId(created));
            r.put(CalendarContract.Reminders.MINUTES, reminder);
            r.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
            try {
                cr.insert(CalendarContract.Reminders.CONTENT_URI, r);
            } catch (Exception ignored) {
                // Termin steht – nur die Erinnerung fehlt dann.
            }
        }
        Agenda.Entry e = new Agenda.Entry(title, slot.begin, slot.end, slot.allDay, place, calendarName);
        return "Eingetragen: " + Agenda.describe(e, zone)
                + (reminder >= 0 ? ", Erinnerung " + reminder + " Minuten vorher." : ".");
    }

    /** Fehlertext, den {@link #run} als is_error markiert. */
    private static String failWith(String message) {
        throw new IllegalStateException(message);
    }
}
