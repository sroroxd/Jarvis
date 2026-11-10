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

    /** Ohne API-Filter nach einer Pause nur Namen, Antippen oder ausdrücklichen Auftrag akzeptieren. */
    static boolean addressed(String text, boolean hasBrainKey, boolean openMic, long now, long lastExchange) {
        return hasBrainKey || Commands.agentTask(text) != null
                || !ListenRules.needsGate(openMic, now, lastExchange, text);
    }
}
