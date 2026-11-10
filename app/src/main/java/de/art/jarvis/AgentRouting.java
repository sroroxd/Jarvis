package de.art.jarvis;

/** Agentbetrieb ohne separaten API-Schlüssel; Spiel-/Bildmodus benötigt weiter die schnelle API. */
final class AgentRouting {
    static boolean canStart(boolean hasBrainKey, boolean agentReady, boolean gameMode) {
        return hasBrainKey || (agentReady && !gameMode);
    }

    static String task(String text, boolean hasBrainKey, boolean agentReady, boolean gameMode) {
        if (!agentReady || gameMode) return null;
        String explicit = Commands.agentTask(text);
        return explicit != null ? explicit : (hasBrainKey ? null : text);
    }
}
