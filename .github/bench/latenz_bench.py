#!/usr/bin/env python3
"""Latenz-Messung gegen die echte Claude-API (Wunsch #43 „Schnellere Antworten“).

Vergleicht Nachdenk-Einstellungen mit der echten Grundanweisung aus Persona.java (über PromptDump)
und einem echten TFT-Bild. Gemessen wird per Streaming:
  erster Text    – Zeit bis zum ersten Text-Stück
  erster Satz    – Zeit bis zum ersten Text, der vorgelesen würde (Zeilen in [eckigen Klammern] zählen nicht)
  gesamt         – bis zum Ende der Antwort
Nur Python-Standardbibliothek. Schlüssel aus ANTHROPIC_API_KEY.
"""
import base64, json, os, statistics, sys, time, urllib.request

KEY = os.environ["ANTHROPIC_API_KEY"]
MODEL = os.environ.get("BENCH_MODEL", "claude-sonnet-5-5")
REPS = int(os.environ.get("BENCH_REPS", "2"))
D = sys.argv[1]  # Ordner mit den Prompt-Dateien
IMG = sys.argv[2]

read = lambda n: open(os.path.join(D, n), encoding="utf-8").read()
SYS = {"spiel": read("s_spiel.txt"), "alltag": read("s_alltag.txt")}
CTX = {"spiel": read("k_spiel.txt"), "alltag": read("k_alltag.txt")}
IMG_B64 = base64.b64encode(open(IMG, "rb").read()).decode()

QUESTIONS = [
    ("spiel", "S1", "Was soll ich jetzt machen?"),
    ("spiel", "S2", "Soll ich leveln oder rollen?"),
    ("spiel", "S3", "Welche Einheit soll ich als Nächstes kaufen?"),
    ("alltag", "A1", "Wie viel ist siebzehn mal drei?"),
    ("alltag", "A2", "Wenn es bei uns 14 Uhr ist, wie spät ist es dann in New York?"),
]
CONFIGS = [("adaptiv/low (heute)", "adaptive"), ("between_tools/low", "between_tools")]


def body(mode, question, thinking, max_tokens=4096):
    content = []
    if mode == "spiel":
        content.append({"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": IMG_B64}})
    content.append({"type": "text", "text": CTX[mode]})
    content.append({"type": "text", "text": question})
    return {
        "model": MODEL, "max_tokens": max_tokens, "stream": True,
        "thinking": {"type": thinking}, "output_config": {"effort": "low"},
        "system": [{"type": "text", "text": SYS[mode], "cache_control": {"type": "ephemeral"}}],
        "messages": [{"role": "user", "content": content}],
    }


def spoken(text):
    """Was Jarvis vorlesen würde: vollständige Zeilen in eckigen Klammern fallen weg, eine noch offene auch."""
    out = []
    lines = text.split("\n")
    for i, line in enumerate(lines):
        s = line.strip()
        last = i == len(lines) - 1
        if s.startswith("["):
            continue  # Steuerzeile (fertig oder noch im Entstehen)
        out.append(line)
    return "".join(out).strip()


def run(b):
    req = urllib.request.Request("https://api.anthropic.com/v1/messages", data=json.dumps(b).encode(),
                                 headers={"x-api-key": KEY, "anthropic-version": "2023-06-01",
                                          "content-type": "application/json"})
    t0 = time.monotonic()
    first_text = first_spoken = None
    text, thinking_chars, usage = "", 0, {}
    with urllib.request.urlopen(req, timeout=120) as r:
        for raw in r:
            line = raw.decode("utf-8").strip()
            if not line.startswith("data:"):
                continue
            ev = json.loads(line[5:])
            t = ev.get("type")
            if t == "message_start":
                usage.update(ev["message"].get("usage", {}))
            elif t == "content_block_delta":
                d = ev["delta"]
                if d.get("type") == "text_delta":
                    if first_text is None:
                        first_text = time.monotonic() - t0
                    text += d["text"]
                    if first_spoken is None and spoken(text):
                        first_spoken = time.monotonic() - t0
                elif d.get("type") == "thinking_delta":
                    thinking_chars += len(d.get("thinking", ""))
            elif t == "message_delta":
                usage.update(ev.get("usage", {}))
            elif t == "error":
                raise RuntimeError(ev)
    return {"erster_text": first_text, "erster_satz": first_spoken, "gesamt": time.monotonic() - t0,
            "denk_zeichen": thinking_chars, "ausgabe_tokens": usage.get("output_tokens"),
            "cache_gelesen": usage.get("cache_read_input_tokens"), "text": text}


def main():
    # Cache für beide Grundanweisungen vorwärmen, damit kein Lauf das Schreiben bezahlt.
    for mode in SYS:
        run(body(mode, "Kurzer Test, antworte nur mit OK.", "between_tools", 64))
    results = []
    for rep in range(REPS):
        for mode, qid, q in QUESTIONS:
            order = CONFIGS if (rep % 2 == 0) else CONFIGS[::-1]  # Reihenfolge abwechseln
            for name, thinking in order:
                try:
                    res = run(body(mode, q, thinking))
                except Exception as e:  # noqa: BLE001 – Messung soll weiterlaufen
                    res = {"fehler": str(e)[:300]}
                res.update({"frage": qid, "modus": mode, "einstellung": name, "lauf": rep + 1})
                results.append(res)
                print(json.dumps(res, ensure_ascii=False), flush=True)

    rows = ["| Einstellung | Modus | erster Satz (Median) | gesamt (Median) | Denk-Zeichen (Mittel) | Ausgabe-Tokens (Mittel) |",
            "|---|---|---|---|---|---|"]
    for name, _ in CONFIGS:
        for mode in ("spiel", "alltag"):
            rs = [r for r in results if r["einstellung"] == name and r["modus"] == mode and "fehler" not in r]
            if not rs:
                continue
            fs = [r["erster_satz"] for r in rs if r["erster_satz"] is not None]
            rows.append("| {} | {} | {:.2f} s | {:.2f} s | {:.0f} | {:.0f} |".format(
                name, mode, statistics.median(fs) if fs else float("nan"),
                statistics.median(r["gesamt"] for r in rs),
                statistics.mean(r["denk_zeichen"] for r in rs),
                statistics.mean((r["ausgabe_tokens"] or 0) for r in rs)))
    rows.append("")
    rows.append("Antworten:")
    for r in results:
        if "fehler" in r:
            rows.append(f"- {r['einstellung']} {r['frage']} #{r['lauf']}: FEHLER {r['fehler']}")
        else:
            rows.append(f"- {r['einstellung']} {r['frage']} #{r['lauf']} ({r['erster_satz'] or 0:.2f} s): "
                        + r["text"].replace("\n", " ⏎ ")[:220])
    summary = "\n".join(rows)
    print("\n" + summary)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8").write("## Latenz-Messung\n\n" + summary + "\n")
    json.dump(results, open("latenz_ergebnis.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
