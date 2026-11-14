package de.art.jarvis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Ein expliziter Seitenabruf. Keine Suche, Cookies, Anmeldung oder Ausführung von Seiteninhalt. */
public final class LocalWebReader {
    private LocalWebReader() {}

    public static String read(String url, BooleanSupplier allowed) throws Exception {
        try {
            return readAllowed(url, allowed);
        } catch (Exception e) {
            check(allowed);
            throw e;
        }
    }

    private static String readAllowed(String url, BooleanSupplier allowed) throws Exception {
        check(allowed);
        URI target = LocalWebPolicy.validate(url);
        for (int redirect = 0; redirect <= 2; redirect++) {
            check(allowed);
            String host = target.getHost().replace("[", "").replace("]", "");
            InetAddress[] addresses = resolve(host, allowed);
            for (InetAddress address : addresses) {
                if (!LocalWebPolicy.isPublicAddress(address)) throw new IOException("Private Netzwerkziele sind gesperrt.");
            }
            if (addresses.length == 0) throw new IOException("Keine öffentliche Serveradresse gefunden.");
            // Direkt an die geprüfte IP binden: kein zweiter DNS-Lookup durch den HTTP-Client.
            try (Socket tcp = new Socket()) {
                check(allowed);
                tcp.connect(new InetSocketAddress(addresses[0], 443), 6000);
                tcp.setSoTimeout(6000);
                check(allowed);
                try (SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(tcp, host, 443, true)) {
                    tls.setSoTimeout(6000);
                    SSLParameters params = tls.getSSLParameters();
                    params.setEndpointIdentificationAlgorithm("HTTPS");
                    if (!host.contains(":") && !host.matches("[0-9.]+")) {
                        params.setServerNames(Collections.singletonList(new SNIHostName(host)));
                    }
                    tls.setSSLParameters(params);
                    check(allowed);
                    tls.startHandshake();
                    check(allowed);
                    String path = target.getRawPath();
                    if (path == null || path.isEmpty()) path = "/";
                    if (target.getRawQuery() != null) path += "?" + target.getRawQuery();
                    String request = "GET " + path + " HTTP/1.1\r\nHost: " + target.getHost()
                            + "\r\nUser-Agent: Jarvis-LocalReader/1.0\r\nAccept: text/html, text/plain, application/json"
                            + "\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n";
                    check(allowed);
                    tls.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                    check(allowed);
                    tls.getOutputStream().flush();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
                    InputStream in = guarded(tls, allowed, deadline);
                    String status = line(in);
                    if (!status.matches("HTTP/1\\.[01] [0-9]{3}(?: .*)?")) throw new IOException("Ungültige HTTP-Antwort.");
                    int code = Integer.parseInt(status.substring(9, 12));
                    Map<String, String> headers = new HashMap<>();
                    int headerBytes = status.length();
                    for (String row; !(row = line(in)).isEmpty();) {
                        headerBytes += row.length() + 2;
                        int colon = row.indexOf(':');
                        if (headerBytes > 16384 || colon < 1) throw new IOException("Ungültige oder zu große HTTP-Header.");
                        String key = row.substring(0, colon).toLowerCase(Locale.ROOT);
                        if (headers.put(key, row.substring(colon + 1).trim()) != null
                                && (key.equals("content-length") || key.equals("transfer-encoding"))) {
                            throw new IOException("Mehrdeutige HTTP-Antwort.");
                        }
                    }
                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        if (redirect == 2 || !headers.containsKey("location")) throw new IOException("Zu viele oder ungültige Weiterleitungen.");
                        try {
                            target = LocalWebPolicy.validate(target.resolve(headers.get("location")).toString());
                        } catch (IllegalArgumentException e) {
                            throw new IOException("Unsichere oder ungültige Weiterleitung.");
                        }
                        continue;
                    }
                    if (code != 200) throw new IOException("Webseite antwortet mit HTTP " + code + ".");
                    String type = headers.getOrDefault("content-type", "");
                    String mime = type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                    if (!(mime.equals("text/html") || mime.equals("text/plain")
                            || mime.equals("application/json") || mime.equals("application/xhtml+xml"))) {
                        throw new IOException("Nur HTML, Klartext oder JSON können gelesen werden.");
                    }
                    if (!headers.getOrDefault("content-encoding", "identity").equalsIgnoreCase("identity")) {
                        throw new IOException("Komprimierte Antworten werden nicht gelesen.");
                    }
                    byte[] body = body(in, headers, allowed);
                    check(allowed);
                    Charset charset = StandardCharsets.UTF_8;
                    java.util.regex.Matcher match = java.util.regex.Pattern.compile(
                            "(?i)charset\\s*=\\s*[\"']?([a-z0-9._-]+)").matcher(type);
                    if (match.find()) {
                        try { charset = Charset.forName(match.group(1)); } catch (Exception ignored) { }
                    }
                    String text = new String(body, charset);
                    if (mime.contains("html")) text = htmlText(text, allowed);
                    text = normalizeText(text, allowed);
                    String prefix = "Quelle: " + target + "\nUngeprüfter Webseiteninhalt:\n";
                    int room = LocalWebPolicy.MAX_TEXT_LENGTH - prefix.length();
                    if (text.length() > room) text = text.substring(0, room - 15) + "\n[Text gekürzt]";
                    check(allowed);
                    return prefix + text;
                }
            }
        }
        throw new IOException("Zu viele Weiterleitungen.");
    }

    private static InetAddress[] resolve(String host, BooleanSupplier allowed) throws Exception {
        check(allowed);
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Jarvis-Web-DNS");
            thread.setDaemon(true);
            return thread;
        });
        Future<InetAddress[]> pending = worker.submit(() -> {
            check(allowed);
            return InetAddress.getAllByName(host);
        });
        try {
            InetAddress[] result = pending.get(6, TimeUnit.SECONDS);
            check(allowed);
            return result;
        } catch (CancellationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Webseitenabruf wurde beendet.");
        } catch (Exception e) {
            check(allowed);
            throw new IOException("DNS-Auflösung fehlgeschlagen oder Zeitlimit erreicht.");
        } finally {
            pending.cancel(true);
            worker.shutdownNow();
        }
    }

    private static InputStream guarded(Socket socket, BooleanSupplier allowed, long deadline) throws IOException {
        InputStream source = socket.getInputStream();
        return new InputStream() {
            private void before() throws IOException {
                check(allowed);
                long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (left <= 0) throw new IOException("Zeitlimit beim Lesen der Webseite erreicht.");
                socket.setSoTimeout((int) Math.min(6000, Math.max(1, left)));
            }
            @Override public int read() throws IOException {
                before();
                int value = source.read();
                check(allowed);
                return value;
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                before();
                int count = source.read(b, off, len);
                check(allowed);
                return count;
            }
        };
    }

    private static String line(InputStream in) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int c; (c = in.read()) != -1;) {
            if (c == '\n') {
                if (result.length() == 0 || result.charAt(result.length() - 1) != '\r') break;
                return result.substring(0, result.length() - 1);
            }
            if (result.length() >= 4096) throw new IOException("HTTP-Zeile zu lang.");
            result.append((char) c);
        }
        throw new IOException("Unvollständige HTTP-Antwort.");
    }

    private static byte[] body(InputStream in, Map<String, String> headers, BooleanSupplier allowed) throws IOException {
        boolean chunked = headers.containsKey("transfer-encoding");
        if (chunked && (!headers.get("transfer-encoding").equalsIgnoreCase("chunked")
                || headers.containsKey("content-length"))) throw new IOException("Nicht unterstützte Übertragung.");
        long length = -1;
        try {
            if (headers.containsKey("content-length")) {
                String value = headers.get("content-length");
                if (!value.matches("[0-9]{1,12}")) throw new NumberFormatException();
                length = Long.parseLong(value);
            }
        } catch (NumberFormatException e) { throw new IOException("Ungültige Antwortgröße."); }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        long remaining = length;
        while (true) {
            check(allowed);
            if (chunked) {
                String size = line(in).split(";", 2)[0];
                if (!size.matches("[0-9a-fA-F]{1,8}")) throw new IOException("Ungültige Datenblöcke.");
                remaining = Long.parseLong(size, 16);
                if (remaining == 0) break;
            }
            while (remaining != 0 && out.size() < LocalWebPolicy.MAX_BODY_BYTES) {
                int want = Math.min(buffer.length, LocalWebPolicy.MAX_BODY_BYTES - out.size());
                if (remaining > 0) want = (int) Math.min(want, remaining);
                int n = in.read(buffer, 0, want);
                if (n < 0) {
                    if (remaining > 0) throw new IOException("Unvollständiger Seiteninhalt.");
                    return out.toByteArray();
                }
                out.write(buffer, 0, n);
                if (remaining > 0) remaining -= n;
            }
            if (out.size() == LocalWebPolicy.MAX_BODY_BYTES || !chunked) break;
            if (!line(in).isEmpty()) throw new IOException("Ungültiger Datenblockabschluss.");
        }
        return out.toByteArray();
    }

    /** Reine Testschnittstelle: jeder Eingabeabschnitt wird höchstens konstant oft gelesen. */
    static String htmlText(String text, BooleanSupplier allowed) {
        check(allowed);
        StringBuilder out = new StringBuilder();
        String hidden = null;
        int limit = Math.min(text.length(), LocalWebPolicy.MAX_BODY_BYTES);
        int nextCheck = 0;
        for (int i = 0; i < limit;) {
            if (i >= nextCheck) {
                check(allowed);
                nextCheck = i + 256;
            }
            if (hidden != null && !(text.charAt(i) == '<'
                    && text.regionMatches(true, i, "</" + hidden, 0, hidden.length() + 2)
                    && i + hidden.length() + 2 < limit
                    && tagBoundary(text.charAt(i + hidden.length() + 2)))) {
                i++;
                continue;
            }
            if (text.charAt(i) != '<') {
                out.append(text.charAt(i++));
                continue;
            }
            if (text.startsWith("<!--", i)) {
                i += 4;
                while (i < limit && !text.startsWith("-->", i)) {
                    scanCheck(i, allowed);
                    i++;
                }
                i = Math.min(limit, i + 3);
                out.append(' ');
                continue;
            }
            i++;
            boolean closing = i < limit && text.charAt(i) == '/';
            if (closing) i++;
            int start = i;
            while (i < limit && asciiName(text.charAt(i))) {
                scanCheck(i, allowed);
                i++;
            }
            // Namen sind nur für wenige feste Tags relevant; keine riesigen Teilstrings anlegen.
            String name = i - start <= 16 ? text.substring(start, i).toLowerCase(Locale.ROOT) : "";
            boolean boundary = i < limit && tagBoundary(text.charAt(i));
            char quote = 0;
            boolean finished = false;
            while (i < limit) {
                scanCheck(i, allowed);
                char c = text.charAt(i++);
                if (quote != 0) {
                    if (c == quote) quote = 0;
                } else if (c == '\'' || c == '"') {
                    quote = c;
                } else if (c == '>') {
                    finished = true;
                    break;
                }
            }
            if (!finished) break; // Auch unvollständige Tags am gekappten Body-Ende auslassen.
            if (hidden != null) {
                if (closing && boundary && hidden.equals(name)) hidden = null;
            } else if (!closing && boundary && (name.equals("script") || name.equals("style")
                    || name.equals("noscript") || name.equals("template"))) {
                hidden = name;
            }
            out.append(name.equals("br") || (closing && (name.equals("p") || name.equals("div")
                    || name.equals("li") || (name.length() == 2 && name.charAt(0) == 'h'
                    && name.charAt(1) >= '1' && name.charAt(1) <= '6'))) ? '\n' : ' ');
        }
        check(allowed);
        // Feste literale Ersetzungen, keine Suche mit variabler Rückverfolgung.
        return out.toString().replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }

    private static boolean asciiName(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
    }

    private static boolean tagBoundary(char c) {
        return c == '>' || c == '/' || c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f';
    }

    private static void scanCheck(int position, BooleanSupplier allowed) {
        if ((position & 255) == 0) check(allowed);
    }

    /** Auch lange Folgen von Leerzeichen ohne Zeilenumbruch bleiben linear. */
    static String normalizeText(String text, BooleanSupplier allowed) {
        check(allowed);
        StringBuilder out = new StringBuilder();
        boolean space = false;
        int newlines = 0;
        for (int i = 0; i < text.length(); i++) {
            scanCheck(i, allowed);
            char c = text.charAt(i);
            if (c == '\n') {
                space = false;
                newlines = Math.min(2, newlines + 1);
            } else if (c <= 32 || c == 127) {
                space = true;
            } else {
                if (out.length() > 0) {
                    if (newlines > 0) for (int n = 0; n < newlines; n++) out.append('\n');
                    else if (space) out.append(' ');
                }
                out.append(c);
                space = false;
                newlines = 0;
            }
        }
        check(allowed);
        return out.toString();
    }

    private static void check(BooleanSupplier allowed) {
        if (Thread.currentThread().isInterrupted() || allowed == null || !allowed.getAsBoolean()) {
            throw new CancellationException("Internetzugriff wurde beendet oder ist ausgeschaltet.");
        }
    }
}
