package de.art.jarvis;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

/**
 * Telefonmodus fürs Dauergespräch: Jarvis' Stimme läuft wie bei einem Videoanruf über den Lautsprecher.
 *
 * <p>Warum: Die Echo-Unterdrückung des Handys filtert laut Berichten zu Samsung-Geräten nur im Kommunikationsmodus
 * heraus, was das Handy selbst abspielt (Recherche vom 7. Nov., STATUS.md). Spielt Jarvis seine Stimme als normalen
 * Ton ab, kommt sie am Mikrofon lauter an als Art – und Dazwischenreden kann nicht klappen (Gerätetest 8. Nov.).
 *
 * <p>Laut Android-Doku (AudioManager): {@code setMode(MODE_IN_COMMUNICATION)} und
 * {@code setCommunicationDevice(eingebauter Lautsprecher)}, sonst käme der Ton aus der Hörmuschel; beim Verlassen
 * {@code clearCommunicationDevice()} und zurück in den Normalmodus. Stirbt der Prozess, setzt Android beides selbst zurück.
 *
 * <p>Nur ohne Kopfhörer: Mit Kopfhörern gibt es kein Echo, und Bluetooth würde in den schlechten Telefon-Ton wechseln.
 * Alle Methoden laufen auf dem Haupt-Thread.
 */
final class TalkAudio {
    private final AudioManager am;
    private boolean active;

    TalkAudio(Context context) {
        am = context.getSystemService(AudioManager.class);
    }

    boolean active() {
        return active;
    }

    /** Schaltet den Telefonmodus ein; false, wenn er nicht passt (Kopfhörer) oder das System ablehnt. */
    boolean enter() {
        if (active) return true;
        // setCommunicationDevice gibt es erst ab Android 12 (API 31); die App läuft ab Android 11.
        if (android.os.Build.VERSION.SDK_INT < 31 || am == null || headphonesConnected()) return false;
        AudioDeviceInfo speaker = null;
        for (AudioDeviceInfo d : am.getAvailableCommunicationDevices()) {
            if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                speaker = d;
                break;
            }
        }
        if (speaker == null) return false;
        try {
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            if (!am.setCommunicationDevice(speaker)) {
                am.setMode(AudioManager.MODE_NORMAL);
                return false;
            }
        } catch (RuntimeException e) {
            am.setMode(AudioManager.MODE_NORMAL);
            return false;
        }
        active = true;
        return true;
    }

    void exit() {
        if (!active || am == null) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice();
        } catch (RuntimeException ignored) {
            // schon zurückgesetzt
        }
        am.setMode(AudioManager.MODE_NORMAL);
        active = false;
    }

    /** Wie die Handy-Stimme abgespielt wird: im Telefonmodus als Sprachkommunikation, sonst wie bisher (Medien). */
    static AudioAttributes speech(boolean voiceCall) {
        return new AudioAttributes.Builder()
                .setUsage(voiceCall ? AudioAttributes.USAGE_VOICE_COMMUNICATION : AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    private boolean headphonesConnected() {
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                case AudioDeviceInfo.TYPE_BLE_HEADSET:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_HEARING_AID:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }
}
