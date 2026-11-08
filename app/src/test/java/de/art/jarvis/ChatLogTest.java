package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** Gesprächsverlauf auf dem Startbildschirm. */
public class ChatLogTest {

    @Test
    public void wortwechselWerdenAngehaengt() {
        ChatLog log = new ChatLog();
        log.jarvis("Zu Diensten, Sir.");
        log.art("Was steht morgen an?");
        log.jarvis("Um zehn das Zahnarzttermin.");
        log.art("Und danach?");
        log.jarvis("Nichts weiter.");
        List<ChatLog.Entry> e = log.snapshot();
        assertEquals(5, e.size());
        assertFalse(e.get(0).art);
        assertTrue(e.get(1).art);
        assertEquals("Nichts weiter.", e.get(4).text);
    }

    @Test
    public void zwischenergebnisseDerSpracherkennungErsetzen() {
        ChatLog log = new ChatLog();
        log.art("Was");
        log.art("Was soll ich");
        log.art("Was soll ich kaufen");
        assertEquals(1, log.size());
        assertEquals("Was soll ich kaufen", log.snapshot().get(0).text);
    }

    @Test
    public void wachsendeAntwortImGespraechsmodusErsetzt() {
        ChatLog log = new ChatLog();
        log.art("Wie stehe ich?");
        log.jarvis("Du hast");
        log.jarvis("Du hast vierzig Gold");
        log.jarvis("Du hast vierzig Gold, geh auf Stufe sieben.");
        assertEquals(2, log.size());
        assertEquals("Du hast vierzig Gold, geh auf Stufe sieben.", log.snapshot().get(1).text);
    }

    @Test
    public void andereMeldungNachEinerAntwortBleibtErhalten() {
        ChatLog log = new ChatLog();
        log.art("Danke, Gesprächsmodus aus");
        log.jarvis("Gern geschehen.");
        log.jarvis("Gesprächsmodus beendet. Gespräch 4:12 · ca. 0,06 $");
        assertEquals(3, log.size());
        assertEquals("Gern geschehen.", log.snapshot().get(1).text);
    }

    @Test
    public void leeresWirdIgnoriertUndClearLeert() {
        ChatLog log = new ChatLog();
        log.art("");
        log.jarvis(null);
        log.art("   ");
        assertEquals(0, log.size());
        log.art("Hallo");
        log.clear();
        assertEquals(0, log.size());
    }

    @Test
    public void aeltesteEintraegeFallenHeraus() {
        ChatLog log = new ChatLog();
        for (int i = 0; i < ChatLog.MAX_ENTRIES + 10; i++) {
            log.art("Frage " + i);
            log.jarvis("Antwort " + i);
        }
        List<ChatLog.Entry> e = log.snapshot();
        assertEquals(ChatLog.MAX_ENTRIES, e.size());
        assertEquals("Antwort " + (ChatLog.MAX_ENTRIES + 9), e.get(e.size() - 1).text);
    }
}
