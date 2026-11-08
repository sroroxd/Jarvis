package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Wunsch #23: erst die Werte ablesen, dann befehlen – die Ablese-Zeile wird nie vorgelesen. */
public class GameReadoutTest {

    @Test
    public void ableseZeileWirdGefundenUndEntfernt() {
        String a = "[STAND: Phase 3-2 | Gold 32 | Stufe 6 | Leben 54]\nKaufen Sie den zweiten Kämpfer. Danach: Stufe 7.";
        assertEquals("Phase 3-2 | Gold 32 | Stufe 6 | Leben 54", GameReadout.find(a));
        assertEquals("Kaufen Sie den zweiten Kämpfer. Danach: Stufe 7.", GameReadout.strip(a));
    }

    @Test
    public void ohneAbleseZeileBleibtAllesWieEsIst() {
        assertNull(GameReadout.find("Rollen Sie auf Stufe 7."));
        assertNull(GameReadout.find("[STAND: ]"));
        assertNull(GameReadout.find(null));
        assertEquals("Rollen Sie auf Stufe 7.", GameReadout.strip("Rollen Sie auf Stufe 7."));
    }

    @Test
    public void verlaufBehaeltDieWerte() {
        assertEquals("[STAND: Gold 32 | Stufe ?] Halten Sie Zinsen.",
                GameReadout.forHistory("Gold 32 | Stufe ?", "Halten Sie Zinsen."));
        assertEquals("Halten Sie Zinsen.", GameReadout.forHistory(null, "Halten Sie Zinsen."));
    }

    /** Beim satzweisen Sprechen kommt die Zeile zuerst – sie darf nie in der Sprachausgabe landen. */
    @Test
    public void ableseZeileWirdNieVorgelesen() {
        SpeechChunker c = new SpeechChunker();
        List<String> said = new ArrayList<>();
        String stream = "[STAND: Phase 4-1 | Gold 50 | Stufe 7 | Leben 31]\nStabilisieren Sie jetzt. Danach: rollen.";
        for (int i = 0; i < stream.length(); i += 4) said.addAll(c.feed(stream.substring(i, Math.min(stream.length(), i + 4))));
        String rest = c.finish();
        if (!rest.isEmpty()) said.add(rest);
        assertEquals(List.of("Stabilisieren Sie jetzt.", "Danach: rollen."), said);
    }
}
