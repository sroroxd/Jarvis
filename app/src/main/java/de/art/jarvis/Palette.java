package de.art.jarvis;

import android.content.Context;

/** Eine Farbe pro Zustand – gleich für Reaktor, schwebendes J und Statuspunkt. */
final class Palette {
    private Palette() {}

    static int color(Context c, JarvisService.State state) {
        if (state == null) return c.getColor(R.color.state_off);
        switch (state) {
            case LISTENING: return c.getColor(R.color.state_listening);
            case THINKING: return c.getColor(R.color.state_thinking);
            case SPEAKING: return c.getColor(R.color.state_speaking);
            default: return c.getColor(R.color.state_idle);
        }
    }

    static int off(Context c) {
        return c.getColor(R.color.state_off);
    }
}
