package de.art.jarvis;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
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

    /** Adresse der Release-Abfrage. Leerer Zweig bei einer Test-App = lokal gebaut → keine Prüfung. */
    static String releaseUrl(boolean testApp, String branch) {
        if (!testApp) return API + "latest";
        if (branch == null || branch.trim().isEmpty()) return null;
        try {
            return API + "tags/" + URLEncoder.encode("test-" + branch.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return null;
        }
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
            org.json.JSONObject json = new org.json.JSONObject(readAll(conn.getInputStream()));
            int build = buildNumber(json.optString("name", ""), json.optString("tag_name", ""));
            if (build <= installedBuild) return null;
            org.json.JSONArray assets = json.optJSONArray("assets");
            String url = null;
            if (assets != null) {
                for (int i = 0; i < assets.length() && url == null; i++) {
                    org.json.JSONObject a = assets.optJSONObject(i);
                    if (a != null && a.optString("name", "").endsWith(".apk")) {
                        url = a.optString("browser_download_url", null);
                    }
                }
            }
            if (url == null) url = json.optString("html_url", null);
            return url == null ? null : new Result(build, url);
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
