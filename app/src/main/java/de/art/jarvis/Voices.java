package de.art.jarvis;

import android.content.Context;
import android.content.pm.PackageManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Gemeinsame Stimm-Logik für Einstellungen und Dienst. */
final class Voices {
    static final String GOOGLE_TTS = "com.google.android.tts";

    private Voices() {}

    /** Bevorzugt Googles Sprachausgabe (natürlichere Stimmen), sonst die Standard-Engine. */
    static TextToSpeech create(Context context, TextToSpeech.OnInitListener listener) {
        try {
            context.getPackageManager().getPackageInfo(GOOGLE_TTS, 0);
            return new TextToSpeech(context, listener, GOOGLE_TTS);
        } catch (PackageManager.NameNotFoundException e) {
            return new TextToSpeech(context, listener);
        }
    }

    /** Alle nutzbaren deutschen Stimmen, beste Qualität zuerst. */
    static List<Voice> german(TextToSpeech tts) {
        List<Voice> list = new ArrayList<>();
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return list;
            for (Voice v : voices) {
                if (!"de".equals(v.getLocale().getLanguage())) continue;
                Set<String> f = v.getFeatures();
                if (f != null && f.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                list.add(v);
            }
        } catch (Exception ignored) {
            return list;
        }
        Collections.sort(list, (a, b) -> {
            if (a.getQuality() != b.getQuality()) return b.getQuality() - a.getQuality();
            return a.getName().compareTo(b.getName());
        });
        return list;
    }

    static String label(Voice v, int index) {
        StringBuilder sb = new StringBuilder("Stimme ").append(index + 1);
        String country = v.getLocale().getCountry();
        if (!country.isEmpty() && !"DE".equals(country)) sb.append(" (").append(country).append(')');
        sb.append(v.isNetworkConnectionRequired() ? " – online, sehr natürlich" : " – offline");
        if (v.getQuality() >= Voice.QUALITY_VERY_HIGH) sb.append(" ★★");
        else if (v.getQuality() >= Voice.QUALITY_HIGH) sb.append(" ★");
        return sb.toString();
    }

    /** Wendet gespeicherte Stimme, Tonhöhe und Tempo an. */
    static void apply(TextToSpeech tts, Prefs prefs) {
        tts.setLanguage(Locale.GERMANY);
        List<Voice> voices = german(tts);
        Voice chosen = null;
        String wanted = prefs.voiceName();
        for (Voice v : voices) {
            if (v.getName().equals(wanted)) {
                chosen = v;
                break;
            }
        }
        if (chosen == null && !voices.isEmpty()) chosen = voices.get(0);
        if (chosen != null) tts.setVoice(chosen);
        tts.setPitch(prefs.pitch());
        tts.setSpeechRate(prefs.rate());
    }
}
