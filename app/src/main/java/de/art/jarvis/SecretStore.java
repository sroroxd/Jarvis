package de.art.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Zugangsdaten liegen ausschließlich verschlüsselt in einer von Backups ausgeschlossenen Datei. */
final class SecretStore {
    private static final Object LOCK = new Object();
    private static final String ALIAS = "jarvis.credentials.v1";
    private final SharedPreferences encrypted;
    private final SharedPreferences legacy;
    private String error = "";

    SecretStore(Context context, SharedPreferences legacy) {
        this.legacy = legacy;
        encrypted = context.getSharedPreferences("jarvis_secrets", Context.MODE_PRIVATE);
    }

    String read(String name) {
        synchronized (LOCK) {
            try {
                if (encrypted.contains(name)) {
                    String value = SecretCipher.decrypt(key(false), name, encrypted.getString(name, ""));
                    // Auch einen nach einem Prozessabbruch verbliebenen Klartext erst nach erfolgreichem Lesen entfernen.
                    if (legacy.contains(name) && !legacy.edit().remove(name).commit()) throw new IllegalStateException();
                    return value;
                }
                String value = legacy.getString(name, "");
                if (value.isEmpty()) return "";
                if (!write(name, value)) return "";
                return value;
            } catch (Exception e) {
                error = "Gespeicherte Zugangsdaten konnten nicht sicher gelesen werden. Bitte die Schlüssel erneut eintragen.";
                return ""; // Vorhandene Daten bleiben erhalten; keine Klartext-Notlösung.
            }
        }
    }

    boolean write(String name, String value) {
        synchronized (LOCK) {
            try {
                String plain = value == null ? "" : value;
                if (plain.isEmpty()) {
                    // Ein leeres Formular darf einen unlesbaren gespeicherten Schlüssel nicht unbemerkt löschen.
                    if (encrypted.contains(name)) SecretCipher.decrypt(key(false), name, encrypted.getString(name, ""));
                    if (!legacy.edit().remove(name).commit() || !encrypted.edit().remove(name).commit()) {
                        throw new IllegalStateException();
                    }
                } else {
                    String sealed = SecretCipher.encrypt(key(true), name, plain);
                    if (!encrypted.edit().putString(name, sealed).commit()) throw new IllegalStateException();
                    // Erst die dauerhafte neue Kopie prüfen, dann die alte entfernen.
                    if (!plain.equals(SecretCipher.decrypt(key(false), name, encrypted.getString(name, "")))) {
                        throw new IllegalStateException();
                    }
                    if (!legacy.edit().remove(name).commit()) throw new IllegalStateException();
                }
                return true;
            } catch (Exception e) {
                error = "Zugangsdaten konnten nicht sicher gespeichert werden. Bitte erneut versuchen; alte Daten bleiben erhalten.";
                return false;
            }
        }
    }

    String error() { return error; }

    private SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        if (!create) throw new IllegalStateException("Keystore-Schlüssel fehlt");
        // Nur ein ausdrücklicher Schreibvorgang darf nach Schlüsselverlust einen neuen Schlüssel anlegen.
        // Lesen erzeugt niemals einen Ersatz und lässt alle vorhandenen Chiffrate unangetastet.
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
}
