package de.art.jarvis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Echo-Schranke für die OpenAI-Realtime-Sitzung: Solange Jarvis' eigene Stimme aus dem Lautsprecher kommt,
 * geht KEIN Mikrofonton an OpenAI – außer Art spricht hörbar lauter als das gelernte Echo (Dazwischenreden).
 *
 * Warum: Im Gerätetest vom 16. Nov. (Build 185, S24 Ultra) hat die Server-Spracherkennung Jarvis' eigene
 * Antwort als neue Frage verstanden. Jarvis lobte daraufhin seine eigene Formulierung („Danke, das ist klar
 * und professionell ausgedrückt“) und geriet in einen Schnellfeuer-Loop: 4 Antworten in 8 Sekunden, rund
 * 11 500 Tokens in 2 Minuten – kostenpflichtig und ohne Zutun. Die Hardware-Echounterdrückung allein reicht
 * auf dem Gerät nicht; dieselbe Erfahrung führte in der kostenlosen Sprachkette zu {@link SpeechGate}.
 *
 * Arbeitsweise (reine Java-Logik, testbar ohne Android, siehe EchoGateTest):
 * - Spielt gerade nichts (und seit {@link #TAIL_MS} nichts mehr), gehen alle Blöcke durch – wie bisher.
 * - Beginnt die Wiedergabe, lernt {@link SpeechGate} den Echo-Pegel; bis zum Durchbruch wird jeder Block
 *   verworfen. Die letzten Blöcke bleiben in einer kurzen Vorlaufliste.
 * - Schlägt die Schranke an (Art deutlich über dem Echo, über ~200 ms), wird die Vorlaufliste nachgereicht –
 *   so hört der Server auch den Satzanfang – und ab dann geht alles durch, bis die Wiedergabe endet.
 * - Kurze Sprechpausen zwischen Sätzen (unter {@link #REARM_MS}) behalten das gelernte Echo, sonst müsste
 *   die Schranke mitten in der Antwort neu lernen und wäre dort blind.
 */
final class EchoGate {
    /** So lange nach dem letzten hörbaren Ton bleibt die Schranke zu (Lautsprecher→Mikrofon-Laufzeit, Puffer). */
    static final int TAIL_MS = 300;
    /** Pausen bis zu dieser Länge gelten als Satzpause derselben Antwort: gelerntes Echo bleibt. */
    static final int REARM_MS = 2000;
    /** Vorlauf in 20-ms-Blöcken, der beim Durchbruch nachgereicht wird (500 ms – Satzanfang plus Anstieg). */
    static final int PRELUDE = 25;

    private final SpeechGate gate;
    private volatile long audibleUntil = Long.MIN_VALUE;
    private boolean active;
    private boolean tripped;
    private long quietSince = Long.MIN_VALUE;
    private final ArrayDeque<byte[]> prelude = new ArrayDeque<>();

    EchoGate(double marginDb) {
        gate = new SpeechGate(marginDb);
    }

    /** Wiedergabe hat (weiterhin) hörbaren Ton – darf von jedem Thread kommen. */
    void audible(long now) {
        audibleUntil = now + TAIL_MS;
    }

    /** Wiedergabe wurde abgebrochen und geleert: nur noch die kurze Restlaufzeit abdecken. */
    void flushed(long now) {
        audibleUntil = Math.min(audibleUntil, now + 120);
    }

    /** Lernt die Schranke gerade oder hält sie zu? (für Tests und Messzeilen) */
    boolean gating(long now) {
        return now < audibleUntil && !tripped;
    }

    /**
     * Ein 20-ms-Mikrofonblock (PCM16, little-endian). Liefert die Blöcke, die an OpenAI gehen dürfen:
     * meist diesen einen, beim Durchbruch zusätzlich den Vorlauf, während der Wiedergabe keinen.
     */
    List<byte[]> feed(byte[] frame, int length, long now) {
        boolean playing = now < audibleUntil;
        if (!playing) {
            if (active) {
                active = false;
                tripped = false;
                prelude.clear();
                quietSince = now;
            }
            return Collections.singletonList(copy(frame, length));
        }
        if (!active) {
            active = true;
            tripped = false;
            // Nach einer längeren Stille ist es eine neue Antwort: Echo neu lernen. Kurze Satzpausen nicht.
            if (quietSince == Long.MIN_VALUE || now - quietSince > REARM_MS) {
                gate.reset();
                gate.voiceStarted();
            }
        }
        if (tripped) return Collections.singletonList(copy(frame, length));
        short[] pcm = toShorts(frame, length);
        boolean art = gate.feed(pcm, 0, pcm.length);
        prelude.addLast(copy(frame, length));
        while (prelude.size() > PRELUDE) prelude.removeFirst();
        if (!art) return Collections.emptyList();
        tripped = true;
        List<byte[]> out = new ArrayList<>(prelude);
        prelude.clear();
        return out;
    }

    private static byte[] copy(byte[] frame, int length) {
        byte[] c = new byte[length];
        System.arraycopy(frame, 0, c, 0, length);
        return c;
    }

    private static short[] toShorts(byte[] frame, int length) {
        short[] pcm = new short[length / 2];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) ((frame[2 * i] & 0xff) | (frame[2 * i + 1] << 8));
        }
        return pcm;
    }
}
