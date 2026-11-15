package de.art.jarvis;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Authentifizierte Verschlüsselung; der Feldname bindet den Inhalt an seinen Zweck. */
final class SecretCipher {
    private SecretCipher() {}

    static String encrypt(SecretKey key, String name, String value) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key); // Der Provider erzeugt für jeden Schreibvorgang eine frische IV.
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        return "v1:" + Base64.getEncoder().encodeToString(cipher.getIV()) + ":"
                + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    static String decrypt(SecretKey key, String name, String stored) throws GeneralSecurityException {
        try {
            String[] parts = stored.split(":", -1);
            if (parts.length != 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException();
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            if (iv.length != 12) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Ungültiges Schlüsselformat", e);
        }
    }
}
