package de.art.jarvis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Build-Nummern aus GitHub-Releases lesen. */
public class UpdateCheckTest {

    @Test
    public void testAppNameTraegtBuild() {
        assertEquals(245, UpdateCheck.buildNumber("Jarvis Test (Zweig sammeltest, Build 245)", "test-sammeltest"));
    }

    @Test
    public void normaleAppAusDemTag() {
        assertEquals(32, UpdateCheck.buildNumber("Jarvis 1.0.32", "v1.0.32"));
    }

    @Test
    public void alteTestReleasesOhneBuildGebenNull() {
        // So hießen die Test-Releases vor dieser Änderung – dann lieber keine Meldung als eine falsche.
        assertEquals(0, UpdateCheck.buildNumber("Jarvis Test (Zweig sammeltest)", "test-sammeltest"));
        assertEquals(0, UpdateCheck.buildNumber(null, null));
    }

    @Test
    public void adressen() {
        assertTrue(UpdateCheck.releaseUrl(false, "").endsWith("/releases/latest"));
        assertTrue(UpdateCheck.releaseUrl(true, "sammeltest").endsWith("/releases/tags/test-sammeltest"));
        assertNull(UpdateCheck.releaseUrl(true, ""));
        assertNull(UpdateCheck.releaseUrl(true, null));
    }
}
