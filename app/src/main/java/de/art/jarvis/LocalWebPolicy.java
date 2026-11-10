package de.art.jarvis;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/** Strikte, ohne DNS prüfbare URL-Regeln für das ausdrücklich angeforderte Lesewerkzeug. */
public final class LocalWebPolicy {
    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_BODY_BYTES = 128 * 1024;
    public static final int MAX_TEXT_LENGTH = 6000;
    private LocalWebPolicy() {}

    public static URI validate(String input) {
        try {
            if (input == null || input.length() > MAX_URL_LENGTH || input.isEmpty()) throw invalid();
            if (java.util.regex.Pattern.compile("(?i)%(?:0[0-9a-f]|1[0-9a-f]|7f)").matcher(input).find()) throw invalid();
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                if (c <= 32 || c >= 127 || c == '\\') throw invalid();
            }
            URI uri = new URI(input);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque()
                    || uri.getRawUserInfo() != null || host == null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getRawAuthority().contains("%")) throw invalid();
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("[") || host.contains(":")) {
                String literal = host.replace("[", "").replace("]", "");
                if (!isPublicAddress(InetAddress.getByName(literal))) throw invalid();
            } else if (host.matches("[0-9.]+")) {
                String[] parts = host.split("\\.", -1);
                if (parts.length != 4) throw invalid();
                byte[] bytes = new byte[4];
                for (int i = 0; i < 4; i++) {
                    int n = Integer.parseInt(parts[i]);
                    if (n > 255 || !parts[i].equals(Integer.toString(n))) throw invalid();
                    bytes[i] = (byte) n;
                }
                if (!isPublicAddress(InetAddress.getByAddress(bytes))) throw invalid();
            } else {
                if (host.length() > 253 || !host.contains(".")) throw invalid();
                String[] labels = host.split("\\.", -1);
                for (String label : labels) {
                    if (!label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) throw invalid();
                }
                String last = labels[labels.length - 1];
                if (!last.matches("[a-z]{2,63}|xn--[a-z0-9-]+")
                        || last.matches("localhost|local|internal|lan|home|onion|invalid|test")) throw invalid();
            }
            // Fragmente werden nie an den Server übertragen und nicht als Quelle ausgegeben.
            return new URI(input.split("#", 2)[0]);
        } catch (Exception e) {
            throw invalid(); // Keine möglicherweise vertrauliche Eingabe in Fehlermeldungen.
        }
    }

    /** Auch JEDE DNS-Antwort prüfen, bevor irgendeine Verbindung aufgebaut wird. */
    public static boolean isPublicAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return false;
        byte[] b = address.getAddress();
        int a = b[0] & 255, c = b[1] & 255;
        if (b.length == 4) {
            return !(a == 0 || a == 10 || a == 127 || a >= 224
                    || (a == 100 && c >= 64 && c <= 127)
                    || (a == 169 && c == 254) || (a == 172 && c >= 16 && c <= 31)
                    || (a == 192 && (c == 0 || c == 168 || (c == 88 && (b[2] & 255) == 99)))
                    || (a == 198 && (c == 18 || c == 19 || (c == 51 && (b[2] & 255) == 100)))
                    || (a == 203 && c == 0 && (b[2] & 255) == 113));
        }
        // Nur globales IPv6-Unicast; keine lokalen, gemappten oder Übergangs-Adressen.
        return b.length == 16 && (a & 0xe0) == 0x20
                && !(a == 0x20 && c == 0x01 && (b[2] & 255) < 2)
                && !(a == 0x20 && c == 0x02)
                && !(a == 0x20 && c == 0x01 && (b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8)
                && !(a == 0x3f && c == 0xff && (b[2] & 0xf0) == 0);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Nur öffentliche HTTPS-Adressen ohne Zugangsdaten auf Port 443 sind erlaubt.");
    }
}
