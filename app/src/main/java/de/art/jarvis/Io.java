package de.art.jarvis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Gemeinsame Kleinigkeiten fürs Netz – vorher stand {@link #readAll} viermal fast gleich im Code. */
final class Io {
    private Io() {}

    /** Liest einen Datenstrom ganz als UTF-8-Text und schließt ihn; {@code null} ergibt einen leeren Text. */
    static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
