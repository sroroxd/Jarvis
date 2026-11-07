package de.art.jarvis;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prüft, ob auf GitHub eine neuere Version liegt als die installierte.
 *
 * Anlass: Art hat mehrfach eine veraltete Test-App getestet, weil der Download-Link gleich bleibt
 * und nichts verrät, welche Version gerade drauf ist. Jetzt meldet die App das selbst.
 *
 * Test-App: Vorab-Version „test-<Zweig>“, deren Name die Build-Nummer trägt („… Build 245)“).
 * Normale App: neueste Version „v1.0.<Build>“. Die Build-Nummer ist der versionCode.
 */
final class UpdateCheck {
    private static final String API = "https://api.github.com/repos/sroroxd/Jarvis/releases/";
    private static final Pattern BUILD_IN_NAME = Pattern.compile("\\bBuild\\s+(\\d+)\\b");
    private static final Pattern BUILD_IN_TAG = Pattern.compile("^v1\\.0\\.(\\d+)$");

    static final class Result {
        final int build;
        final String url;

        Result(int build, String url) {
            this.build = build;
            this.url = url;
        }
    }

    private UpdateCheck() {}

    /** Build-Nummer aus Release-Name oder -Tag; 0, wenn keine zu finden ist. */
    static int buildNumber(String name, String tag) {
        if (name != null) {
            Matcher m = BUILD_IN_NAME.matcher(name);
            if (m.find()) return parse(m.group(1));
        }
        if (tag != null) {
            Matcher m = BUILD_IN_TAG.matcher(tag.trim());
            if (m.find()) return parse(m.group(1));
        }
        return 0;
    }

    /** Liste der letzten Releases (für die Test-App). */
    private static final String LIST = "https://api.github.com/repos/sroroxd/Jarvis/releases?per_page=30";

    /**
     * Adresse der Release-Abfrage. Leerer Zweig bei einer Test-App = lokal gebaut → keine Prüfung.
     * Die Test-App schaut über ALLE Zweige: Alle Test-Apps teilen eine Paket-ID, und die höchste
     * Build-Nummer ist der neueste geprüfte Stand – egal aus welchem Zweig. Vorher sah eine Test-App
     * nur ihren eigenen Zweig und bemerkte neue Zweige nie.
     */
    static String releaseUrl(boolean testApp, String branch) {
        if (!testApp) return API + "latest";
        if (branch == null || branch.trim().isEmpty()) return null;
        return LIST;
    }

    /** Ein Release mit APK als Ergebnis, sonst null. */
    static Result fromRelease(org.json.JSONObject json) {
        if (json == null) return null;
        int build = buildNumber(json.optString("name", ""), json.optString("tag_name", ""));
        if (build <= 0) return null;
        org.json.JSONArray assets = json.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                org.json.JSONObject a = assets.optJSONObject(i);
                if (a != null && a.optString("name", "").endsWith(".apk")) {
                    String url = a.optString("browser_download_url", "");
                    if (!url.isEmpty()) return new Result(build, url);
                }
            }
        }
        String page = json.optString("html_url", "");
        return page.isEmpty() ? null : new Result(build, page);
    }

    /** Die Test-App mit der höchsten Build-Nummer (Vorab-Versionen „test-…“ mit APK), sonst null. */
    static Result newestTestApp(org.json.JSONArray releases) {
        Result best = null;
        if (releases == null) return null;
        for (int i = 0; i < releases.length(); i++) {
            org.json.JSONObject rel = releases.optJSONObject(i);
            if (rel == null || !rel.optString("tag_name", "").startsWith("test-")) continue;
            Result r = fromRelease(rel);
            if (r == null || !r.url.endsWith(".apk")) continue;
            if (best == null || r.build > best.build) best = r;
        }
        return best;
    }

    /**
     * Fragt GitHub (blockierend – nur aus einem Hintergrund-Thread aufrufen).
     * @return das Release, wenn es neuer ist als {@code installedBuild}; sonst null (auch bei jedem Fehler).
     */
    static Result fetchNewer(boolean testApp, String branch, int installedBuild) {
        String address = releaseUrl(testApp, branch);
        if (address == null) return null;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("accept", "application/vnd.github+json");
            conn.setRequestProperty("user-agent", "Jarvis-App"); // GitHub verlangt einen User-Agent
            if (conn.getResponseCode() != 200) return null;
            String body = readAll(conn.getInputStream());
            Result r = testApp
                    ? newestTestApp(new org.json.JSONArray(body))
                    : fromRelease(new org.json.JSONObject(body));
            return r != null && r.build > installedBuild ? r : null;
        } catch (Exception e) {
            return null; // kein Netz o. Ä. – dann eben beim nächsten Start
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static int parse(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
