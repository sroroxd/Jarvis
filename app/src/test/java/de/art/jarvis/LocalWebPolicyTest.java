package de.art.jarvis;

import org.junit.Test;
import java.net.InetAddress;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class LocalWebPolicyTest {
    @Test public void erlaubtOeffentlichesHttpsUndEntferntFragment() {
        assertEquals("https://example.com/a?q=hello", LocalWebPolicy.validate(
                "https://example.com/a?q=hello#abschnitt").toString());
        assertEquals("https://example.com:443/", LocalWebPolicy.validate("https://example.com:443/").toString());
        LocalWebPolicy.validate("https://8.8.8.8/");
        LocalWebPolicy.validate("https://172.32.0.1/");
        LocalWebPolicy.validate("https://100.128.0.1/");
        LocalWebPolicy.validate("https://192.169.0.1/");
        LocalWebPolicy.validate("https://[2606:4700:4700::1111]/");
    }

    @Test public void sperrtSchemesZugangsdatenUndLokaleNamen() {
        rejects("http://example.com", "file:///etc/passwd", "content://provider/item",
                "data:text/plain,hi", "javascript:alert(1)", "https://user:secret@example.com/",
                "https://user@example.com/", "https://localhost/", "https://a.localhost/",
                "https://router.local/", "https://server.internal/", "https://router.lan/",
                "https://server.home/", "https://singlehost/", "https://example.com:8443/",
                "https://example.com./", "https://example.com\\@127.0.0.1/",
                "https://example.com\r\nHost: localhost/", "https://example.com/%0d%0aX:bad",
                "https://%31%32%37.0.0.1/", "https://example.com%2f@127.0.0.1/",
                "https://[fe80::1%25wlan0]/", "https://", "", null);
    }

    @Test public void sperrtPrivateUndAlternativeAdressliterale() {
        rejects("https://127.0.0.1/", "https://127.1/", "https://2130706433/",
                "https://0177.0.0.1/", "https://0x7f000001/", "https://0x7f.0.0.1/",
                "https://0.0.0.0/", "https://10.0.0.1/", "https://172.16.0.1/",
                "https://8.8.8.999/", "https://8.008.8.8/", "https://1.2.3.4.5/",
                "https://172.31.255.255/", "https://192.168.1.1/", "https://169.254.169.254/",
                "https://100.64.0.1/", "https://198.18.0.1/", "https://224.0.0.1/",
                "https://255.255.255.255/", "https://192.0.2.1/", "https://198.51.100.1/",
                "https://203.0.113.1/", "https://[::1]/", "https://[::]/", "https://[fc00::1]/",
                "https://[fd12::1]/", "https://[fe80::1]/", "https://[ff02::1]/",
                "https://[::ffff:127.0.0.1]/", "https://[2001:db8::1]/",
                "https://[2002:7f00:1::]/", "https://[64:ff9b::7f00:1]/");
    }

    @Test public void prueftAuchDnsErgebnisseOhneNetzwerk() throws Exception {
        assertFalse(LocalWebPolicy.isPublicAddress(InetAddress.getByAddress(new byte[]{10, 0, 0, 1})));
        assertFalse(LocalWebPolicy.isPublicAddress(InetAddress.getByAddress(new byte[]{(byte) 169, (byte) 254, 1, 1})));
        assertTrue(LocalWebPolicy.isPublicAddress(InetAddress.getByAddress(new byte[]{8, 8, 8, 8})));
        assertFalse(LocalWebPolicy.isPublicAddress(null));
    }

    @Test public void begrenztUrlUndAusgabe() {
        String prefix = "https://example.com/";
        String limit = prefix + "a".repeat(LocalWebPolicy.MAX_URL_LENGTH - prefix.length());
        assertEquals(LocalWebPolicy.MAX_URL_LENGTH, LocalWebPolicy.validate(limit).toString().length());
        rejects(limit + "a");
        assertEquals(128 * 1024, LocalWebPolicy.MAX_BODY_BYTES);
        assertEquals(6000, LocalWebPolicy.MAX_TEXT_LENGTH);
    }

    @Test public void ausgeschaltetesWerkzeugBrichtVorJederAdresspruefungAb() {
        assertThrows(CancellationException.class, () -> LocalWebReader.read("https://example.com/", () -> false));
        assertThrows(CancellationException.class, () -> LocalWebReader.read("gar keine URL", () -> false));
        assertThrows(CancellationException.class, () -> LocalWebReader.read("https://example.com/", null));
    }

    @Test public void fehlermeldungWiederholtKeineZugangsdaten() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> LocalWebPolicy.validate("https://private:password@example.com/"));
        assertFalse(error.getMessage().contains("password"));
        assertFalse(error.getMessage().contains("private"));
    }

    @Test(timeout = 2000) public void htmlScannerBleibtBeiUnvollstaendigenTagsLinear() {
        assertEquals("", LocalWebReader.htmlText("<".repeat(100000), () -> true));
        assertEquals("", LocalWebReader.htmlText("<script".repeat(18000), () -> true));
        String fragments = "<script>" + "<script".repeat(15000) + "</script><p>Sichtbar</p>";
        assertEquals("Sichtbar", LocalWebReader.normalizeText(
                LocalWebReader.htmlText(fragments, () -> true), () -> true));
        assertEquals("", LocalWebReader.normalizeText(" ".repeat(100000), () -> true));
    }

    @Test public void htmlScannerEntferntKommentareUndInaktiveInhalte() {
        String html = "<p>Titel &amp; Text</p><!-- Kommentar -->"
                + "<SCRIPT type='a>b'>geheim <script> </SCRIPT>"
                + "<style>css</style><noscript>fallback</noscript><template>inaktiv</template>"
                + "<p title='1>0'>Ende<br>Zeile</p><script>unvollständig";
        String text = LocalWebReader.normalizeText(LocalWebReader.htmlText(html, () -> true), () -> true);
        assertEquals("Titel & Text\nEnde\nZeile", text);
        assertEquals("Anfang", LocalWebReader.normalizeText(
                LocalWebReader.htmlText("Anfang <!--" + "<".repeat(100000), () -> true), () -> true));
    }

    @Test public void htmlScannerUndNormalisierungLassenSichWaehrendDesScansAbbrechen() {
        AtomicInteger checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> LocalWebReader.htmlText(
                "<".repeat(100000), () -> checks.incrementAndGet() < 5));
        checks.set(0);
        assertThrows(CancellationException.class, () -> LocalWebReader.htmlText(
                "<script>" + "<script".repeat(15000), () -> checks.incrementAndGet() < 5));
        checks.set(0);
        assertThrows(CancellationException.class, () -> LocalWebReader.normalizeText(
                " ".repeat(100000), () -> checks.incrementAndGet() < 5));
    }

    private static void rejects(String... values) {
        for (String value : values) assertThrows(IllegalArgumentException.class, () -> LocalWebPolicy.validate(value));
    }
}
