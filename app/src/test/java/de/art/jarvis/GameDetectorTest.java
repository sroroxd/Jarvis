package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Automatische Spielerkennung: wann der Spielmodus von selbst an- und ausgeht. */
public class GameDetectorTest {
    private static final String TFT = GameDetector.TFT_PACKAGE;
    private static final String JARVIS = "de.art.jarvis.test";
    private static final String DISCORD = "com.discord";

    @Test
    public void tftNachVorneSchaltetSofortEin() {
        GameDetector d = new GameDetector();
        d.event(1000, GameDetector.ACTIVITY_RESUMED, TFT);
        assertEquals(GameDetector.Change.ENTERED, d.update(1500));
        assertTrue(d.inGame());
        assertEquals(GameDetector.Change.NONE, d.update(5000)); // nur einmal melden
    }

    @Test
    public void kurzerBlickAufEineAndereAppBeendetNichts() {
        GameDetector d = new GameDetector();
        d.event(1000, GameDetector.ACTIVITY_RESUMED, TFT);
        d.update(1000);
        d.event(10_000, GameDetector.ACTIVITY_RESUMED, DISCORD);
        assertEquals(GameDetector.Change.NONE, d.update(25_000)); // 15 s weg
        d.event(26_000, GameDetector.ACTIVITY_RESUMED, TFT);       // zurück im Spiel
        assertEquals(GameDetector.Change.NONE, d.update(27_000));
        assertEquals(GameDetector.Change.NONE, d.update(60_000));
        assertTrue(d.inGame());
    }

    @Test
    public void nachZwanzigSekundenAusserhalbIstSchluss() {
        GameDetector d = new GameDetector();
        d.event(1000, GameDetector.ACTIVITY_RESUMED, TFT);
        d.update(1000);
        d.event(100_000, GameDetector.ACTIVITY_RESUMED, JARVIS);
        assertEquals(GameDetector.Change.NONE, d.update(119_000));
        assertEquals(GameDetector.Change.LEFT, d.update(120_000));
        assertFalse(d.inGame());
        assertEquals(GameDetector.Change.NONE, d.update(200_000));
    }

    @Test
    public void nurNachVorneKommenZaehlt() {
        GameDetector d = new GameDetector();
        d.event(1000, 2 /* ACTIVITY_PAUSED */, TFT);
        assertEquals(GameDetector.Change.NONE, d.update(2000));
        // Ältere Ereignisse (überlappende Abfragen) überschreiben nichts Neueres.
        d.event(5000, GameDetector.ACTIVITY_RESUMED, DISCORD);
        d.event(4000, GameDetector.ACTIVITY_RESUMED, TFT);
        assertEquals(DISCORD, d.foreground());
    }

    @Test
    public void regionaleAbleger() {
        assertTrue(GameDetector.isTft("com.riotgames.league.teamfighttactics"));
        assertTrue(GameDetector.isTft("com.riotgames.league.teamfighttacticsvn"));
        assertFalse(GameDetector.isTft("com.riotgames.league.wildrift"));
        assertFalse(GameDetector.isTft(null));
    }
}
