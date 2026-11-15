package de.art.jarvis;

/** Fängt die beiden Android-Startgrenzen ab, bevor der Assistent Mikrofon oder Anbieter öffnet. */
final class ForegroundStart {
    private ForegroundStart() {}

    /** null = Start erlaubt; andernfalls eine Anzeige ohne interne Android-Fehler oder Zugangsdaten. */
    static String attempt(boolean microphoneGranted, Runnable start) {
        if (!microphoneGranted) {
            return "Ohne Mikrofonberechtigung kann Jarvis nicht starten. Bitte Mikrofon in den App-Berechtigungen erlauben und hier erneut starten.";
        }
        try {
            start.run();
            return null;
        } catch (SecurityException denied) {
            // PERMISSION_GRANTED allein garantiert bei While-in-use noch keinen erlaubten FGS-Start.
            return "Android hat den Mikrofon-Dienststart nicht erlaubt. Öffne Jarvis im Vordergrund, prüfe die Mikrofonberechtigung und tippe erneut auf Starten.";
        } catch (IllegalStateException denied) {
            // Einschließlich ForegroundServiceStartNotAllowedException (ab Android 12).
            return "Android lässt den Dienst gerade nicht starten. Öffne Jarvis im Vordergrund und tippe erneut auf Starten.";
        }
    }
}
