package de.art.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** OpenAI-Schlüssel nur auf dem Gerät, verschlüsselt mit Android Keystore. Nie ins APK oder Backup schreiben. */
final class OpenAiCredentials {
    private static final String ALIAS = "jarvis_openai_api_key_v1";
    private static final String STORE = "openai_credential_private";
    private static final String CIPHER = "ciphertext";
    private static final String IV = "iv";

    private final SharedPreferences preferences;

    OpenAiCredentials(Context context) {
        preferences = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    void save(String key) throws GeneralSecurityException {
        String value = key == null ? "" : key.trim();
        if (value.isEmpty()) {
            clear();
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        preferences.edit()
                .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply();
    }

    String load() throws GeneralSecurityException {
        String data = preferences.getString(CIPHER, "");
        String iv = preferences.getString(IV, "");
        if (data.isEmpty() || iv.isEmpty()) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deviceKey(),
                new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    void clear() {
        preferences.edit().remove(CIPHER).remove(IV).apply();
    }

    private static SecretKey deviceKey() throws GeneralSecurityException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        try {
            store.load(null);
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException("Keystore konnte nicht geöffnet werden", e);
        }
        SecretKey existing = (SecretKey) store.getKey(ALIAS, null);
        if (existing != null) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
