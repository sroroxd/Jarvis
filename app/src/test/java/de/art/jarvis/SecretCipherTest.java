package de.art.jarvis;

import org.junit.Test;
import java.security.GeneralSecurityException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import static org.junit.Assert.*;

public class SecretCipherTest {
    private SecretKey key() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }

    @Test public void roundTripUndZufaelligeIv() throws Exception {
        SecretKey key = key();
        String first = SecretCipher.encrypt(key, "api_key", "test-geheim-äöü");
        String second = SecretCipher.encrypt(key, "api_key", "test-geheim-äöü");
        assertNotEquals(first, second);
        assertFalse(first.contains("test-geheim"));
        assertEquals("test-geheim-äöü", SecretCipher.decrypt(key, "api_key", first));
    }

    @Test public void vertauschteFelderUndFalscherSchluesselWerdenAbgewiesen() throws Exception {
        SecretKey key = key();
        String sealed = SecretCipher.encrypt(key, "api_key", "test-geheim");
        assertThrows(GeneralSecurityException.class, () -> SecretCipher.decrypt(key, "github_token", sealed));
        assertThrows(GeneralSecurityException.class, () -> SecretCipher.decrypt(key(), "api_key", sealed));
    }

    @Test public void manipulationUndKaputteFormateWerdenAbgewiesen() throws Exception {
        SecretKey key = key();
        String sealed = SecretCipher.encrypt(key, "api_key", "test-geheim");
        String[] parts = sealed.split(":");
        byte[] bytes = java.util.Base64.getDecoder().decode(parts[2]);
        bytes[0] ^= 1;
        String changed = parts[0] + ":" + parts[1] + ":" + java.util.Base64.getEncoder().encodeToString(bytes);
        assertThrows(GeneralSecurityException.class, () -> SecretCipher.decrypt(key, "api_key", changed));
        for (String invalid : new String[]{"", "test-geheim", "v2:AA==:AA==", "v1:!:!", "v1:AA==:AA=="}) {
            assertThrows(GeneralSecurityException.class, () -> SecretCipher.decrypt(key, "api_key", invalid));
        }
    }
}
