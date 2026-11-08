package de.art.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
    static final String SHERPA_TTS = "org.woheller69.ttsengine";

    private Voices() {}

    /** Eine installierte Sprachausgabe-Engine. */
    static final class Engine {
        final String pkg;
        final String label;

        Engine(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    /** Alle installierten Sprachausgabe-Engines (ohne eine Sprachausgabe-Instanz zu starten). */
    static List<Engine> engines(Context context) {
        List<Engine> list = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> services = pm.queryIntentServices(
                new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0);
        for (ResolveInfo ri : services) {
            if (ri.serviceInfo == null) continue;
            String pkg = ri.serviceInfo.packageName;
            CharSequence label = ri.loadLabel(pm);
            list.add(new Engine(pkg, label == null ? pkg : label.toString()));
        }
        return list;
    }

    private static boolean isSherpa(String pkg, String label) {
        String s = (pkg + " " + label).toLowerCase(Locale.ROOT);
        return s.contains("sherpa") || s.contains("woheller69");
    }

    /** Gewählte Engine oder automatisch: SherpaTTS (für Alan) → Google → Systemstandard (null). */
    static String resolveEngine(Context context, Prefs prefs) {
        List<Engine> engines = engines(context);
        String wanted = prefs.engine();
        if (!wanted.isEmpty()) {
            for (Engine e : engines) if (e.pkg.equals(wanted)) return wanted;
        }
        for (Engine e : engines) if (isSherpa(e.pkg, e.label)) return e.pkg;
        for (Engine e : engines) if (e.pkg.equals(GOOGLE_TTS)) return e.pkg;
        return null;
    }

    static TextToSpeech create(Context context, String enginePkg, TextToSpeech.OnInitListener listener) {
        return enginePkg == null
                ? new TextToSpeech(context, listener)
                : new TextToSpeech(context, listener, enginePkg);
    }

    /** Deutsche und englische Stimmen der Engine: Deutsch zuerst, dann nach Qualität. */
    static List<Voice> list(TextToSpeech tts) {
        List<Voice> list = new ArrayList<>();
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return list;
            for (Voice v : voices) {
                String lang = v.getLocale().getLanguage();
                if (!"de".equals(lang) && !"en".equals(lang)) continue;
                Set<String> f = v.getFeatures();
                if (f != null && f.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                list.add(v);
            }
        } catch (Exception ignored) {
            return list;
        }
        Collections.sort(list, (a, b) -> {
            boolean aDe = "de".equals(a.getLocale().getLanguage());
            boolean bDe = "de".equals(b.getLocale().getLanguage());
            if (aDe != bDe) return aDe ? -1 : 1;
            if (a.getQuality() != b.getQuality()) return b.getQuality() - a.getQuality();
            return a.getName().compareTo(b.getName());
        });
        return list;
    }

    static String label(Voice v) {
        Locale l = v.getLocale();
        StringBuilder sb = new StringBuilder(v.getName());
        sb.append(" – ").append(l.getDisplayLanguage(Locale.GERMANY));
        if (!l.getCountry().isEmpty()) sb.append(" (").append(l.getDisplayCountry(Locale.GERMANY)).append(')');
        if (v.isNetworkConnectionRequired()) sb.append(", online");
        if (v.getQuality() >= Voice.QUALITY_VERY_HIGH) sb.append(" ★★");
        else if (v.getQuality() >= Voice.QUALITY_HIGH) sb.append(" ★");
        return sb.toString();
    }

    /**
     * Die zu nutzende Stimme: gespeicherte Wahl → Miro → Thorsten → Alan (Namenssuche)
     * → bei SherpaTTS: deutsche, dann britische Stimme → beste deutsche Stimme.
     */
    static Voice choose(TextToSpeech tts, Prefs prefs, String enginePkg) {
        List<Voice> voices = list(tts);
        String wanted = prefs.voiceName();
        if (!wanted.isEmpty()) {
            for (Voice v : voices) if (v.getName().equals(wanted)) return v;
        }
        for (String hint : Prefs.DEFAULT_VOICE_HINTS) {
            for (Voice v : voices) {
                if (v.getName().toLowerCase(Locale.ROOT).contains(hint)) return v;
            }
        }
        if (enginePkg != null && isSherpa(enginePkg, "")) {
            // SherpaTTS benennt Stimmen oft nur nach Sprache – dann zählt die Sprache des geladenen Modells.
            for (Voice v : voices) if ("de".equals(v.getLocale().getLanguage())) return v;
            for (Voice v : voices) {
                Locale l = v.getLocale();
                if ("en".equals(l.getLanguage()) && "GB".equals(l.getCountry())) return v;
            }
        }
        for (Voice v : voices) if ("de".equals(v.getLocale().getLanguage())) return v;
        return voices.isEmpty() ? null : voices.get(0);
    }

    /** Wendet Stimme, Tonhöhe und Tempo an. */
    static void apply(TextToSpeech tts, Prefs prefs, String enginePkg) {
        Voice chosen = choose(tts, prefs, enginePkg);
        if (chosen != null) {
            tts.setVoice(chosen);
        } else if (tts.setLanguage(Locale.GERMANY) < TextToSpeech.LANG_AVAILABLE) {
            tts.setLanguage(Locale.UK);
        }
        tts.setPitch(prefs.pitch());
        tts.setSpeechRate(prefs.rate());
    }
}
