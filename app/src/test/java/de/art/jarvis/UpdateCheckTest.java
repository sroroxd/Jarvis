package de.art.jarvis;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        assertTrue(UpdateCheck.releaseUrl(true, "sammeltest").endsWith("/releases?per_page=30"));
        assertNull(UpdateCheck.releaseUrl(true, ""));
        assertNull(UpdateCheck.releaseUrl(true, null));
    }

    @Test
    public void testAppNimmtHoechstenBuildAusAllenZweigen() throws Exception {
        org.json.JSONArray list = new org.json.JSONArray("["
                + "{\"tag_name\":\"test-sammeltest\",\"name\":\"Jarvis Test (Zweig sammeltest, Build 51)\","
                + "\"assets\":[{\"name\":\"JarvisTest.apk\",\"browser_download_url\":\"https://x/51/JarvisTest.apk\"}]},"
                + "{\"tag_name\":\"vorschau\",\"name\":\"Gerätetest (Build 99)\",\"assets\":[{\"name\":\"01_start.png\"}]},"
                + "{\"tag_name\":\"v1.0.43\",\"name\":\"Jarvis 1.0.43\","
                + "\"assets\":[{\"name\":\"Jarvis.apk\",\"browser_download_url\":\"https://x/43/Jarvis.apk\"}]},"
                + "{\"tag_name\":\"test-selbst-update\",\"name\":\"Jarvis Test (Zweig selbst-update, Build 61)\","
                + "\"assets\":[{\"name\":\"JarvisTest.apk\",\"browser_download_url\":\"https://x/61/JarvisTest.apk\"}]}]");
        UpdateCheck.Result r = UpdateCheck.newestTestApp(list);
        assertEquals(61, r.build);
        assertEquals("https://x/61/JarvisTest.apk", r.url);
    }

    @Test
    public void downloadZuerstUeberDieApi() throws Exception {
        String api = "https://api.github.com/repos/sroroxd/Jarvis/releases/assets/723452802";
        String link = "https://github.com/sroroxd/Jarvis/releases/download/test-x/JarvisTest.apk";
        org.json.JSONArray list = new org.json.JSONArray("[{\"tag_name\":\"test-x\",\"name\":\"Jarvis Test (Zweig x, Build 75)\","
                + "\"assets\":[{\"name\":\"JarvisTest.apk\",\"url\":\"" + api + "\",\"browser_download_url\":\"" + link + "\"}]}]");
        UpdateCheck.Result r = UpdateCheck.newestTestApp(list);
        assertEquals(api, r.apiUrl);
        assertArrayEquals(new String[] {api, link}, UpdateCheck.downloadUrls(r.apiUrl, r.url));
        // Ohne API-Adresse (oder mit fremder) bleibt es beim normalen Link.
        assertArrayEquals(new String[] {link}, UpdateCheck.downloadUrls("", link));
        assertArrayEquals(new String[] {link}, UpdateCheck.downloadUrls("https://example.com/a", link));
    }

    @Test
    public void neuerVersuchNurBeiStoerungen() {
        assertTrue(UpdateCheck.retryable(504));
        assertTrue(UpdateCheck.retryable(502));
        assertTrue(UpdateCheck.retryable(429));
        assertTrue(UpdateCheck.retryable(403));
        assertFalse(UpdateCheck.retryable(404));
        assertFalse(UpdateCheck.retryable(200));
    }

    @Test
    public void ohneTestAppKeinErgebnis() throws Exception {
        assertNull(UpdateCheck.newestTestApp(new org.json.JSONArray("[{\"tag_name\":\"v1.0.43\",\"name\":\"Jarvis 1.0.43\"}]")));
        assertNull(UpdateCheck.newestTestApp(null));
    }
}
