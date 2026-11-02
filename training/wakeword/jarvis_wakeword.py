#!/usr/bin/env python3
"""Trainiert das Wachwort „Jarvis“ für die App (openWakeWord-Format, offline auf dem Handy).

Die beiden Merkmals-Modelle (melspectrogram.onnx, embedding_model.onnx) bleiben unverändert –
trainiert wird nur der kleine Kopf, der aus 16 Merkmal-Frames (1,28 s) eine Wahrscheinlichkeit macht.
Ein- und Ausgabe sind identisch zu hey_jarvis_v0.1.onnx, der Java-Detektor bleibt also gleich.

Schritte (jeder liest/schreibt in --work):
  generate   Sprachbeispiele mit Piper-Stimmen erzeugen (positiv: „Jarvis“, negativ: ähnliche Wörter, Sätze)
  features   Beispiele verrauschen/verhallen, in 2-s-Fenster legen, Merkmale berechnen
  train      Kopf trainieren, bestes Modell nach Trefferquote/Fehlalarmen wählen, als ONNX exportieren
  evaluate   Neues und altes Modell mit Teststimmen messen, die im Training nie vorkamen (Streaming wie in der App)
"""
import argparse
import glob
import json
import math
import os
import random
import sys
import time
from multiprocessing import Pool

import numpy as np
from scipy.signal import butter, fftconvolve, resample_poly, sosfilt

SR = 16000
WIN = 32000            # 2 s → genau 16 Merkmal-Frames
FRAMES = 16
EMB = 96
CHUNK = 1280           # 80 ms, wie in der App
CONTEXT = CHUNK + 480

# Stimmen mit diesen Namensteilen kommen NIE ins Training – sie sind die unabhängige Prüfung.
HOLDOUT = ("kerstin", "pavoque", "alan", "amy")

# ---------------------------------------------------------------- Texte

# Aussprachen von „Jarvis“ (IPA wie espeak/Piper). Gewicht = wie oft gezogen.
JARVIS_PHONEMES = [
    ("dʒˈɑːɹvɪs", 3),   # amerikanisch
    ("dʒˈɑːvɪs", 3),    # britisch (Film-Jarvis)
    ("dʒˈaːvɪs", 4),    # deutsch ausgesprochen „Dschaawis“
    ("dʒˈaːɐvɪs", 3),   # deutsch mit vokalischem r
    ("dʒˈaɾvɪs", 2),    # kurzes a, getipptes r
    ("dʒˈaʁvɪs", 2),    # Zäpfchen-r
    ("dʒˈɑːʁvɪs", 1),
    ("dʒˈɑːɹvəs", 1),
    ("jˈaɾvɪs", 1),     # deutsche Lesart „Jarwis“
]
PREFIX = {
    "en": ["", "", "", "", "hey ", "hey, ", "okay ", "hi ", "so, "],
    "de": ["", "", "", "", "hey ", "hallo ", "okay ", "sag mal, ", "na, "],
}
END = ["", ".", "!", "?", ","]

# Ähnlich klingende Wörter und Phrasen – sollen NICHT auslösen.
NEAR_TEXT = {
    "de": [
        "Ja, was ist denn los?", "Ja, wieso nicht?", "Ja, wie's aussieht, klappt das.", "Na, was gibt's?",
        "Ja, wir sind gleich da.", "Ja, wirklich.", "Jawohl.", "Das Jahr ist vorbei.", "Jahreswechsel",
        "jedes Jahr wieder", "Der Service war gut.", "Schau ins Archiv.", "Ich war in Paris.", "Die Kiwis sind reif.",
        "Gewiss doch.", "Im Dschungel.", "Tschüss!", "Die Garage ist offen.", "Mit viel Charme.", "Meine Jacke.",
        "Jasmin kommt mit.", "Travis kommt später.", "Davis ist da.", "Harvey ist mein Nachbar.", "Elvis lebt.",
        "Marvin hat angerufen.", "Javier ist neu.", "Die Jeans ist blau.", "Der Joker.", "Ein neuer Job.",
        "Jazz im Radio.", "Das ist Java.", "Chaos pur.", "Ja, gut.", "Ja, warte mal.", "Na, wie ist's?",
        "Ja, weiß ich.", "Ja, wisst ihr was?", "Jahrgang", "Gnadenlos", "Charmant", "Jarno spielt.",
    ],
    "en": [
        "Travis", "Davis", "Mavis", "service", "nervous", "harvest", "Marvin", "Harvey", "Elvis", "Jeeves",
        "jar", "jars of jam", "Java", "jargon", "Garvey", "starfish", "car keys", "Paris", "carvings",
        "a jar of honey", "the service is down", "harvest season", "Jarrett", "cervix", "Jervaulx",
    ],
}
NEAR_PHONEMES = ["dʒˈɑːvə", "dʒˈɑː", "dʒˈɑːɹz", "tɹˈævɪs", "dˈeɪvɪs", "mˈeɪvɪs", "sˈɜːvɪs", "nˈɜːvəs",
                 "hˈɑːvɪst", "mˈɑːvɪn", "hˈɑːvi", "ˈɛlvɪs", "dʒˈiːvz", "ɡˈɑːvi", "jˈaː vas", "jˈaː viːs",
                 "dʒˈʊŋəl", "dʒˈɛt", "tʃˈʏs", "ʃˈaːf", "dʒˈɑːɹɡən", "kˈiːvɪs", "ɡəvˈɪs", "pˈaːʁiːs"]

SENTENCES = {
    "de": [
        "Wie spät ist es eigentlich?", "Kauf den zweiten Kämpfer und leg ihn auf die Bank.",
        "Ich gehe gleich mit dem Hund raus.", "Hast du die Garage abgeschlossen?", "Was gibt es heute zum Essen?",
        "Morgen soll es regnen, nimm eine Jacke mit.", "Die Trockenkammer muss heute noch gereinigt werden.",
        "Ich habe noch zwanzig Gold, soll ich hochleveln?", "Das Spiel lädt gerade, warte kurz.",
        "Ruf mich an, wenn du da bist.", "Der Bus kommt wieder zu spät.", "Kannst du mir das Salz geben?",
        "Wir treffen uns um acht vor dem Kino.", "Ich muss noch tanken fahren.", "Das war eine knappe Runde.",
        "Die Ziegel müssen noch trocknen.", "Hast du die Nachricht von Mama gelesen?", "Mach mal leiser, bitte.",
        "Ich glaube, ich nehme die Augmente mit Gold.", "Wir sind auf Platz drei, das reicht noch.",
        "Schreib mir das bitte auf.", "Wo ist denn die Fernbedienung?", "Heute Abend schaue ich Fußball.",
        "Der Kaffee ist schon wieder alle.", "Ich bin gleich zurück.", "Kannst du das Fenster aufmachen?",
        "Das kostet ungefähr fünfzig Euro.", "Wann fängt die Schicht morgen an?", "Ich habe keine Ahnung.",
        "Der Sand muss vom Boden der Kammer.", "Lass uns später darüber reden.", "Das Wetter ist schön heute.",
        "Ich rolle noch einmal für Dreisterne.", "Mein Handy ist fast leer.", "Wie war dein Tag?",
        "Ich fahre am Wochenende nach Hannover.", "Gib mir mal kurz das Ladekabel.", "Wer hat angerufen?",
        "Die Waschmaschine ist fertig.", "Sag ihm, dass ich später komme.", "Der Gegner hat sechs Assassinen.",
        "Gute Nacht, bis morgen.", "Ich habe Hunger, lass uns was bestellen.", "Wie viel Uhr haben wir?",
        "Das Paket kommt erst übermorgen.", "Wo habe ich nur meinen Schlüssel hingelegt?",
        "Kannst du die Musik lauter machen?", "Ich muss noch Wäsche aufhängen.", "Das Training war hart heute.",
        "Ich nehme den Champion mit drei Items.", "Verkauf den Kämpfer, der ist nutzlos.",
    ],
    "en": [
        "What time is it right now?", "I think we should leave in ten minutes.", "The weather is great today.",
        "Can you turn the music down a bit?", "Let's order some pizza tonight.", "Where did I put my keys?",
        "The meeting was moved to Thursday.", "I'm going to take the dog for a walk.", "That was a close game.",
        "Please send me the report by Friday.", "My phone battery is almost empty.", "We need more gold.",
        "Roll down for the three star carry.", "Level up to eight now.", "Have you seen my charger anywhere?",
        "I'll be back in a minute.", "The train is late again.", "Good night, see you tomorrow.",
    ],
}


def lang_of(voice_path):
    cfg = json.load(open(voice_path + ".json"))
    v = cfg.get("espeak", {}).get("voice", "en")
    # Andere Sprachen (z. B. Niederländisch) sprechen englische Sätze mit Akzent; „Jarvis“ nur per Lautschrift.
    lang = "de" if v.startswith("de") else ("en" if v.startswith("en") else "xx")
    return lang, int(cfg.get("num_speakers", 1) or 1), cfg


def weighted(rng, items):
    total = sum(w for _, w in items)
    r = rng.random() * total
    for item, w in items:
        r -= w
        if r <= 0:
            return item
    return items[-1][0]


def positive_text(rng, lang):
    prefix = rng.choice(PREFIX["de" if lang == "de" else "en"])
    end = rng.choice(END)
    if lang == "en" and rng.random() < 0.35:
        word = "Jarvis"
        return (prefix + word + end).strip()
    phon = weighted(rng, JARVIS_PHONEMES)
    return (prefix + "[[ " + phon + end + " ]]").strip()


def negative_text(rng, lang):
    lang = "de" if lang == "de" else "en"
    r = rng.random()
    if r < 0.35:
        return rng.choice(NEAR_TEXT[lang])
    if r < 0.55:
        p = rng.choice(NEAR_PHONEMES)
        return "[[ " + p + rng.choice(END) + " ]]"
    if r < 0.65:  # Nahe Wörter mitten im Satz
        return rng.choice(SENTENCES[lang]) + " " + rng.choice(NEAR_TEXT[lang])
    return rng.choice(SENTENCES[lang])


# ---------------------------------------------------------------- Erzeugen

_voice_cache = {}


def _load_voice(path):
    from piper import PiperVoice
    if path not in _voice_cache:
        _voice_cache.clear()
        _voice_cache[path] = PiperVoice.load(path)
    return _voice_cache[path]


def trim(a, keep_ms=30):
    """Stille vorne/hinten abschneiden, damit das Wortende genau bekannt ist."""
    if len(a) < 400:
        return a
    frame = 160
    n = len(a) // frame
    rms = np.sqrt(np.mean(a[:n * frame].astype(np.float32).reshape(n, frame) ** 2, axis=1) + 1e-9)
    thr = rms.max() * 0.04
    idx = np.where(rms > thr)[0]
    if len(idx) == 0:
        return a
    keep = int(keep_ms * SR / 1000)
    s = max(0, idx[0] * frame - keep)
    e = min(len(a), (idx[-1] + 1) * frame + keep)
    return a[s:e]


def _synth_task(task):
    from piper import SynthesisConfig
    path, items, seed = task
    rng = random.Random(seed)
    voice = _load_voice(path)
    sr = voice.config.sample_rate
    g = math.gcd(sr, SR)
    out = []
    for kind, text, speaker in items:
        cfg = SynthesisConfig(speaker_id=speaker,
                              length_scale=rng.uniform(0.75, 1.35),
                              noise_scale=rng.uniform(0.4, 0.9),
                              noise_w_scale=rng.uniform(0.5, 1.1))
        try:
            chunks = list(voice.synthesize(text, syn_config=cfg))
        except Exception:
            continue
        if not chunks:
            continue
        a = np.concatenate([c.audio_float_array for c in chunks]).astype(np.float32)
        if sr != SR:
            a = resample_poly(a, SR // g, sr // g)
        a = np.clip(a * 32767, -32768, 32767).astype(np.int16)
        if kind == "pos":
            a = trim(a)
        if len(a) < 1600:
            continue
        out.append((kind, a, os.path.basename(path), text))
    return out


def find_voices(voice_dirs, holdout_only=False):
    voices = []
    for d in voice_dirs:
        for p in sorted(glob.glob(os.path.join(d, "**", "*.onnx"), recursive=True)):
            if not os.path.exists(p + ".json"):
                continue
            name = os.path.basename(p).lower()
            held = any(h in name for h in HOLDOUT)
            if held == holdout_only:
                voices.append(p)
    return voices


def cmd_generate(args):
    rng = random.Random(args.seed)
    voices = find_voices(args.voices)
    if not voices:
        sys.exit("Keine Stimmen gefunden")
    infos = []
    for p in voices:
        lang, speakers, _ = lang_of(p)
        w = min(12.0, 1.0 + math.sqrt(speakers))
        infos.append((p, lang, speakers, w))
        print(f"Stimme {os.path.basename(p):40s} {lang} Sprecher={speakers:4d} Gewicht={w:.1f}")
    wsum = sum(i[3] for i in infos)

    tasks = []
    for p, lang, speakers, w in infos:
        for kind, total in (("pos", args.n_pos), ("neg", args.n_neg)):
            n = max(1, int(round(total * w / wsum)))
            items = []
            for _ in range(n):
                spk = rng.randrange(speakers) if speakers > 1 else None
                text = positive_text(rng, lang) if kind == "pos" else negative_text(rng, lang)
                items.append((kind, text, spk))
            for i in range(0, len(items), 200):
                tasks.append((p, items[i:i + 200], rng.randrange(1 << 30)))
    # Gleiche Stimme hintereinander → weniger Ladezeit pro Prozess.
    tasks.sort(key=lambda t: t[0])
    print(f"{len(tasks)} Aufgaben, {args.workers} Prozesse")

    store = {"pos": [], "neg": []}
    meta = {"pos": [], "neg": []}
    t0 = time.time()
    with Pool(args.workers) as pool:
        for k, res in enumerate(pool.imap_unordered(_synth_task, tasks, chunksize=1)):
            for kind, a, vname, text in res:
                store[kind].append(a)
                meta[kind].append((vname, text))
            if k % 10 == 0:
                print(f"  {k + 1}/{len(tasks)}  pos={len(store['pos'])} neg={len(store['neg'])}  {time.time() - t0:.0f}s",
                      flush=True)
    os.makedirs(args.work, exist_ok=True)
    for kind in ("pos", "neg"):
        save_clips(os.path.join(args.work, f"clips_{kind}.npz"), store[kind])
        with open(os.path.join(args.work, f"clips_{kind}.json"), "w") as f:
            json.dump(meta[kind], f, ensure_ascii=False)
    print(f"Fertig: {len(store['pos'])} positive, {len(store['neg'])} negative in {time.time() - t0:.0f}s")


def save_clips(path, clips):
    offsets = np.cumsum([0] + [len(c) for c in clips]).astype(np.int64)
    audio = np.concatenate(clips) if clips else np.zeros(0, np.int16)
    np.savez(path, audio=audio, offsets=offsets)


def load_clips(path):
    z = np.load(path)
    audio, off = z["audio"], z["offsets"]
    return [audio[off[i]:off[i + 1]] for i in range(len(off) - 1)]


# ---------------------------------------------------------------- Merkmale

class Features:
    """Mel-Spektrogramm + Sprach-Einbettung, gebündelt (gleiche Modelle wie in der App)."""

    def __init__(self, model_dir, threads=1):
        import onnxruntime as ort
        so = ort.SessionOptions()
        so.intra_op_num_threads = threads
        so.inter_op_num_threads = 1
        prov = ["CPUExecutionProvider"]
        self.mel = ort.InferenceSession(os.path.join(model_dir, "melspectrogram.onnx"), so, providers=prov)
        self.emb = ort.InferenceSession(os.path.join(model_dir, "embedding_model.onnx"), so, providers=prov)
        self.mel_in = self.mel.get_inputs()[0].name
        self.emb_in = self.emb.get_inputs()[0].name

    def __call__(self, x):
        x = np.asarray(x, dtype=np.float32)
        mel = self.mel.run(None, {self.mel_in: x})[0]
        mel = mel.reshape(x.shape[0], -1, 32) / 10.0 + 2.0
        starts = range(0, mel.shape[1] - 76 + 1, 8)
        win = np.stack([mel[:, s:s + 76] for s in starts], axis=1)
        n, nf = win.shape[:2]
        out = self.emb.run(None, {self.emb_in: win.reshape(-1, 76, 32, 1).astype(np.float32)})[0]
        return out.reshape(n, nf, EMB)[:, -FRAMES:]


def colored_noise(rng, n, kind):
    white = rng.standard_normal(n).astype(np.float32)
    if kind == "white":
        return white
    spec = np.fft.rfft(white)
    f = np.arange(len(spec), dtype=np.float32) + 1.0
    spec = spec / (np.sqrt(f) if kind == "pink" else f)
    return np.fft.irfft(spec, n).astype(np.float32)


def synth_music(rng, n):
    t = np.arange(n) / SR
    out = np.zeros(n, np.float32)
    beat = rng.uniform(0.3, 0.7)
    for _ in range(rng.integers(2, 6)):
        f0 = 110 * 2 ** (rng.integers(0, 36) / 12)
        start = rng.uniform(0, n / SR)
        env = np.clip((t - start) / 0.02, 0, 1) * np.exp(-np.clip(t - start, 0, None) / rng.uniform(0.2, 1.5))
        for h in range(1, 5):
            out += (env * np.sin(2 * np.pi * f0 * h * t) / h).astype(np.float32)
    kick = np.exp(-((t % beat) / 0.03)) * np.sin(2 * np.pi * 60 * t)
    return out + 0.5 * kick.astype(np.float32)


def rms(x):
    return float(np.sqrt(np.mean(x.astype(np.float32) ** 2) + 1e-9))


def synthetic_rir(rng):
    rt60 = rng.uniform(0.15, 0.8)
    n = int(rt60 * SR)
    t = np.arange(n) / SR
    h = rng.standard_normal(n) * np.exp(-6.9 * t / rt60)
    h[0] = rng.uniform(2.0, 8.0)
    return (h / np.sqrt(np.sum(h ** 2))).astype(np.float32)


class Augmenter:
    def __init__(self, seed, babble_pool, rirs=None, noises=None):
        self.rng = np.random.default_rng(seed)
        self.babble = babble_pool
        self.rirs = rirs or []
        self.noises = noises or []

    def background(self, n):
        rng = self.rng
        r = rng.random()
        if self.noises and r < 0.35:
            src = self.noises[rng.integers(len(self.noises))]
            if len(src) < n:
                src = np.tile(src, n // len(src) + 1)
            o = rng.integers(0, len(src) - n + 1)
            return src[o:o + n].astype(np.float32)
        if r < 0.65:
            out = np.zeros(n, np.float32)
            for _ in range(rng.integers(2, 7)):
                c = self.babble[rng.integers(len(self.babble))].astype(np.float32)
                o = rng.integers(-len(c) + 1, n)
                s, e = max(0, o), min(n, o + len(c))
                out[s:e] += c[s - o:e - o] * rng.uniform(0.3, 1.0)
            return out
        if r < 0.8:
            return colored_noise(rng, n, rng.choice(["white", "pink", "brown"]))
        if r < 0.92:
            return synth_music(rng, n)
        t = np.arange(n) / SR
        return sum(np.sin(2 * np.pi * 50 * k * t) / k for k in range(1, 6)).astype(np.float32)

    def colour(self, x, positive):
        rng = self.rng
        x = x.astype(np.float32)
        if rng.random() < 0.4:
            h = self.rirs[rng.integers(len(self.rirs))] if self.rirs and rng.random() < 0.5 else synthetic_rir(rng)
            y = fftconvolve(x, h)[:len(x)]
            x = y * (rms(x) / (rms(y) + 1e-6))
        if rng.random() < 0.75:
            noise = self.background(len(x))
            snr = rng.uniform(3, 25) if positive else rng.uniform(-5, 25)
            speech = rms(x[np.abs(x) > 0.05 * (np.abs(x).max() + 1e-6)]) if np.any(x) else 1000.0
            x = x + noise * (speech / (rms(noise) * 10 ** (snr / 20) + 1e-6))
        if rng.random() < 0.5:
            lo, hi = rng.uniform(70, 300), rng.uniform(3400, 7600)
            x = sosfilt(butter(2, [lo, hi], btype="band", fs=SR, output="sos"), x)
        peak = np.abs(x).max() + 1e-6
        x = x / peak * rng.uniform(0.03, 0.95) * 32767
        if rng.random() < 0.05:
            x = np.clip(x * rng.uniform(1.5, 3), -32767, 32767)
        return np.clip(x, -32768, 32767).astype(np.int16)

    def place_positive(self, clip):
        """Wortende liegt 0–250 ms vor dem Fensterende; oft folgt direkt weitere Sprache („Jarvis, was …“)."""
        rng = self.rng
        out = np.zeros(WIN, np.float32)
        end = WIN - int(rng.integers(0, int(0.25 * SR)))
        start = end - len(clip)
        c = clip.astype(np.float32)
        if start < 0:
            c, start = c[-end:], 0
        out[start:end] = c
        if rng.random() < 0.45 and end < WIN:  # direkt weitersprechen
            gap = int(rng.integers(0, int(0.15 * SR)))
            follow = self.babble[rng.integers(len(self.babble))].astype(np.float32)
            follow *= rms(c) / (rms(follow) + 1e-6) * rng.uniform(0.6, 1.3)
            s = min(WIN, end + gap)
            take = min(len(follow), WIN - s)
            out[s:s + take] += follow[:take]
        if rng.random() < 0.15 and start > 4000:  # vorher schon gesprochen
            prev = self.babble[rng.integers(len(self.babble))].astype(np.float32)
            prev *= rms(c) / (rms(prev) + 1e-6) * rng.uniform(0.6, 1.3)
            e = start - int(rng.integers(int(0.1 * SR), int(0.5 * SR)))
            if e > 0:
                take = min(len(prev), e)
                out[e - take:e] += prev[-take:]
        return out

    def place_negative(self, clip):
        rng = self.rng
        c = clip.astype(np.float32)
        if len(c) >= WIN:
            o = int(rng.integers(0, len(c) - WIN + 1))
            return c[o:o + WIN].copy()
        out = np.zeros(WIN, np.float32)
        o = int(rng.integers(0, WIN - len(c) + 1))
        out[o:o + len(c)] = c
        return out


_feat = None
_pools = None


def _init_features(babble, model_dir, rir_files, noise_files):
    global _feat, _pools
    _feat = Features(model_dir, threads=1)
    _pools = (babble, load_audio_list(rir_files), load_audio_list(noise_files))


def _feature_task(task):
    kind, clips, seed, copies = task
    babble, rirs, noises = _pools
    aug = Augmenter(seed, babble, rirs, noises)
    windows = []
    for c in clips:
        for _ in range(copies):
            if kind == "pos":
                w = aug.colour(aug.place_positive(c), True)
            elif kind == "noise":
                w = aug.colour(aug.background(WIN) * 0.3, False)
            else:
                w = aug.colour(aug.place_negative(c), False)
            windows.append(w)
    feats = []
    for i in range(0, len(windows), 64):
        feats.append(_feat(np.stack(windows[i:i + 64])))
    return kind, np.concatenate(feats).astype(np.float16)


_audio_cache = {}


def load_audio_list(files):
    if not files:
        return []
    key = tuple(files)
    if key not in _audio_cache:
        from scipy.io import wavfile
        out = []
        for f in files:
            try:
                sr, a = wavfile.read(f)
                if a.dtype == np.int16:
                    a = a.astype(np.float32)
                elif a.dtype == np.int32:
                    a = a.astype(np.float32) / 65536.0
                elif a.dtype.kind == "f":
                    a = a.astype(np.float32) * 32767.0
                else:
                    continue
                if a.ndim > 1:
                    a = a.mean(1)
                if sr != SR:
                    g = math.gcd(sr, SR)
                    a = resample_poly(a, SR // g, sr // g)
                if len(a) > 800:
                    out.append(a.astype(np.float32))
            except Exception:
                continue
        _audio_cache.clear()
        _audio_cache[key] = out
    return _audio_cache[key]


def cmd_features(args):
    pos = load_clips(os.path.join(args.work, "clips_pos.npz"))
    neg = load_clips(os.path.join(args.work, "clips_neg.npz"))
    rng = random.Random(args.seed)
    babble = [c for c in neg if len(c) > 8000]
    rng.shuffle(babble)
    babble = babble[:400]
    rir_files = sorted(glob.glob(os.path.join(args.rirs, "**", "*.wav"), recursive=True))[:400] if args.rirs else []
    noise_files = sorted(glob.glob(os.path.join(args.noise, "**", "*.wav"), recursive=True))[:300] if args.noise else []
    print(f"positiv={len(pos)} negativ={len(neg)} Hall={len(rir_files)} Geräusche={len(noise_files)}")

    tasks = []
    for kind, clips, copies in (("pos", pos, args.pos_copies), ("neg", neg, args.neg_copies)):
        idx = list(range(len(clips)))
        rng.shuffle(idx)
        for i in range(0, len(idx), 250):
            tasks.append((kind, [clips[j] for j in idx[i:i + 250]], rng.randrange(1 << 30), copies))
    n_noise = max(1, len(neg) // 10)
    for i in range(0, n_noise, 250):
        tasks.append(("noise", [None] * min(250, n_noise - i), rng.randrange(1 << 30), 1))

    out = {"pos": [], "neg": [], "noise": []}
    t0 = time.time()
    with Pool(args.workers, initializer=_init_features,
              initargs=(babble, args.models, rir_files, noise_files)) as pool:
        for k, (kind, f) in enumerate(pool.imap_unordered(_feature_task, tasks, chunksize=1)):
            out[kind].append(f)
            if k % 10 == 0:
                print(f"  {k + 1}/{len(tasks)}  {time.time() - t0:.0f}s", flush=True)
    pos_f = np.concatenate(out["pos"])
    neg_f = np.concatenate(out["neg"] + out["noise"])
    np.save(os.path.join(args.work, "feat_pos.npy"), pos_f)
    np.save(os.path.join(args.work, "feat_neg.npy"), neg_f)
    print(f"Merkmale: positiv {pos_f.shape}, negativ {neg_f.shape} in {time.time() - t0:.0f}s")


# ---------------------------------------------------------------- Training

def open_feature_file(path, max_rows=None):
    """Öffnet eine (N,16,96)-Merkmaldatei als memmap – auch wenn sie nur teilweise heruntergeladen ist."""
    fmt = np.lib.format
    with open(path, "rb") as f:
        version = fmt.read_magic(f)
        reader = fmt.read_array_header_1_0 if version == (1, 0) else fmt.read_array_header_2_0
        shape, fortran, dtype = reader(f)
        offset = f.tell()
    row = int(np.prod(shape[1:])) * np.dtype(dtype).itemsize
    avail = (os.path.getsize(path) - offset) // row
    n = min(shape[0], avail, max_rows or avail)
    return np.memmap(path, dtype=dtype, mode="r", offset=offset, shape=(n,) + tuple(shape[1:]))


def build_model(hidden):
    import torch.nn as nn
    return nn.Sequential(
        nn.Flatten(),
        nn.Linear(FRAMES * EMB, hidden), nn.LayerNorm(hidden), nn.ReLU(), nn.Dropout(0.1),
        nn.Linear(hidden, hidden), nn.LayerNorm(hidden), nn.ReLU(), nn.Dropout(0.1),
        nn.Linear(hidden, 1), nn.Sigmoid())


def count_activations(scores, thr, cooldown=19):
    """Zählt Auslösungen wie die App: über Schwelle, danach 1,5 s Sperre."""
    n, last = 0, -10 ** 9
    for i in np.where(scores >= thr)[0]:
        if i - last > cooldown:
            n += 1
            last = i
    return n


def cmd_train(args):
    import torch
    torch.manual_seed(args.seed)
    torch.set_num_threads(args.workers)
    rng = np.random.default_rng(args.seed)

    def load_all(name):
        files = sorted(glob.glob(os.path.join(args.work, "**", name), recursive=True))
        print(f"  {name}: {len(files)} Datei(en)")
        return np.concatenate([np.load(f) for f in files]).astype(np.float32)

    pos = load_all("feat_pos.npy")
    neg = load_all("feat_neg.npy")
    rng.shuffle(pos)
    rng.shuffle(neg)
    nvp, nvn = max(200, len(pos) // 20), max(500, len(neg) // 20)
    pos_val, pos_tr = pos[:nvp], pos[nvp:]
    neg_val, neg_tr = neg[:nvn], neg[nvn:]
    acav = open_feature_file(args.acav, args.acav_rows) if args.acav else None
    if acav is not None and acav.nbytes < 6e9:
        acav = np.ascontiguousarray(acav)
    val_stream = np.load(args.val_fp).astype(np.float32) if args.val_fp else None
    if val_stream is not None and val_stream.ndim == 3:
        # Falls schon in Fenster geschnitten: zurück zu einem durchgehenden Strom (letzter Frame je Fenster).
        val_stream = np.concatenate([val_stream[0], val_stream[1:, -1]])
    if val_stream is not None:
        print(f"  Validierungsstrom {val_stream.shape}")
    print(f"Training: pos {len(pos_tr)}  neg {len(neg_tr)}  ACAV {0 if acav is None else len(acav)}  "
          f"Validierung: pos {len(pos_val)} neg {len(neg_val)} "
          f"Strom {0 if val_stream is None else val_stream.shape[0] * 0.08 / 3600:.1f} h")

    val_hours = 0.0 if val_stream is None else val_stream.shape[0] * 0.08 / 3600
    tp, tn = torch.from_numpy(pos_val), torch.from_numpy(neg_val)
    bce = torch.nn.BCELoss(reduction="none")

    def stream_scores(model):
        """Bewertet den Validierungsstrom Fenster für Fenster (in Stücken, spart Speicher)."""
        model.eval()
        out = []
        with torch.no_grad():
            step_n = 20000
            for s in range(0, val_stream.shape[0] - FRAMES + 1, step_n):
                e = min(val_stream.shape[0] - FRAMES + 1, s + step_n)
                idx = np.arange(s, e)[:, None] + np.arange(FRAMES)[None]
                out.append(model(torch.from_numpy(val_stream[idx])).squeeze(1).numpy())
        model.train()
        return np.concatenate(out)

    def predict(model, x):
        model.eval()
        with torch.no_grad():
            out = torch.cat([model(x[i:i + 8192]) for i in range(0, len(x), 8192)]).squeeze(1).numpy()
        model.train()
        return out

    def run(max_neg_weight, attempt):
        model = build_model(args.hidden)
        opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-3)
        sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=args.lr, total_steps=args.steps, pct_start=0.2)
        best, best_key, best_entry, history = None, None, None, []
        t0 = time.time()
        for step in range(1, args.steps + 1):
            bp = pos_tr[rng.integers(0, len(pos_tr), args.batch_pos)]
            bn = neg_tr[rng.integers(0, len(neg_tr), args.batch_neg)]
            parts = [bp, bn]
            if acav is not None:
                rows = np.sort(rng.integers(0, len(acav), args.batch_acav))
                parts.append(np.asarray(acav[rows], dtype=np.float32))
            x = torch.from_numpy(np.concatenate(parts))
            y = torch.cat([torch.ones(len(bp)), torch.zeros(len(x) - len(bp))])
            p = model(x).squeeze(1)
            # Wie openWakeWord: nur schwierige Beispiele zählen, Negative mit wachsendem Gewicht
            # (bis max_neg_weight), gewichteter Mittelwert. So lernt das Modell vor allem, NICHT anzuspringen.
            w_neg = 1.0 + (max_neg_weight - 1.0) * min(1.0, step / (0.7 * args.steps))
            hard = ((y == 0) & (p >= 0.001)) | ((y == 1) & (p < 0.999))
            if hard.any():
                w = torch.where(y[hard] == 0, torch.full_like(y[hard], w_neg), torch.ones_like(y[hard]))
                loss = (bce(p[hard], y[hard]) * w).mean()
                opt.zero_grad()
                loss.backward()
                opt.step()
            sched.step()

            if step % args.eval_every == 0 or step == args.steps:
                sp, sn = predict(model, tp), predict(model, tn)
                rec = float(np.mean(sp >= 0.5))
                far = float(np.mean(sn >= 0.5))
                fph = None
                if val_stream is not None:
                    fph = count_activations(stream_scores(model), 0.5) / val_hours
                entry = {"attempt": attempt, "step": step, "recall": round(rec, 4), "neg_false_accept": round(far, 5),
                         "fp_per_hour": None if fph is None else round(fph, 3), "w_neg": round(w_neg, 1)}
                history.append(entry)
                print(f"  [{attempt}] Schritt {step:6d}  Treffer {rec:6.1%}  Fehlannahme {far:7.3%}  "
                      f"Fehlalarme/h {'-' if fph is None else f'{fph:.2f}'}  ({time.time() - t0:.0f}s)", flush=True)
                # Auswahl: erst Fehlalarm-Ziel, dann maximale Trefferquote.
                fp_metric = fph if fph is not None else far * 1000
                usable = rec >= args.recall_floor
                if usable and fp_metric <= args.max_fp:
                    key = (2, rec)            # Ziel erreicht: höchste Trefferquote gewinnt
                elif usable:
                    key = (1, -fp_metric)     # brauchbar, aber zu viele Fehlalarme: wenigste gewinnen
                else:
                    key = (0, rec)            # erkennt kaum etwas

                if best_key is None or key > best_key:
                    best_key = key
                    best = {k: v.clone() for k, v in model.state_dict().items()}
                    best_entry = entry
        model.load_state_dict(best)
        return model, best_key, best_entry, history

    weight = args.max_neg_weight
    history, chosen = [], None
    for attempt in range(1, args.attempts + 1):
        print(f"Durchgang {attempt}: Gewicht der Negativen bis {weight:.0f}")
        model, key, entry, hist = run(weight, attempt)
        history += hist
        if chosen is None or key > chosen[1]:
            chosen = (model, key, entry)
        if key[0] == 2 and entry["recall"] >= args.min_recall:
            break
        weight *= 2
    model, _, best_entry = chosen

    def predict_final(x):
        return predict(model, x)

    model.eval()
    os.makedirs(args.out, exist_ok=True)
    onnx_path = os.path.join(args.out, args.name + ".onnx")
    dummy = torch.zeros(1, FRAMES, EMB)
    torch.onnx.export(model, dummy, onnx_path, input_names=["input"], output_names=["output"],
                      opset_version=13, dynamic_axes={"input": {0: "batch"}, "output": {0: "batch"}},
                      dynamo=False)
    import onnxruntime as ort
    sess = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])
    check = pos_val[:64]
    diff = float(np.abs(sess.run(None, {"input": check})[0].reshape(-1) - predict_final(torch.from_numpy(check))).max())
    report = {"chosen": best_entry, "history": history, "onnx_vs_torch_maxdiff": diff,
              "train_sizes": {"pos": len(pos_tr), "neg": len(neg_tr), "acav": 0 if acav is None else len(acav)}}
    with open(os.path.join(args.out, args.name + "_training.json"), "w") as f:
        json.dump(report, f, indent=1)
    print(f"Gewählt: {best_entry}  ONNX-Abweichung {diff:.2e}  → {onnx_path}")


# ---------------------------------------------------------------- Prüfung (wie in der App)

class Streamer:
    """Exakte Nachbildung von WakeWordDetector.java (1280er-Blöcke, 480 Samples Überlappung)."""

    def __init__(self, model_dir, heads):
        import onnxruntime as ort
        so = ort.SessionOptions()
        so.intra_op_num_threads = 1
        p = ["CPUExecutionProvider"]
        self.mel = ort.InferenceSession(os.path.join(model_dir, "melspectrogram.onnx"), so, providers=p)
        self.emb = ort.InferenceSession(os.path.join(model_dir, "embedding_model.onnx"), so, providers=p)
        self.heads = {k: ort.InferenceSession(v, so, providers=p) for k, v in heads.items()}

    def scores(self, audio):
        a = np.concatenate([np.zeros(8000, np.int16), audio, np.zeros(12000, np.int16)]).astype(np.float32)
        raw = np.zeros(0, np.float32)
        mel = [np.ones(32, np.float32)] * 76
        feats = []
        out = {k: [] for k in self.heads}
        n = 0
        for i in range(0, len(a) - CHUNK + 1, CHUNK):
            raw = np.concatenate([raw, a[i:i + CHUNK]])[-CONTEXT:]
            m = self.mel.run(None, {self.mel.get_inputs()[0].name: raw[None]})[0].reshape(-1, 32) / 10 + 2
            mel = (mel + list(m))[-76:]
            e = self.emb.run(None, {self.emb.get_inputs()[0].name: np.array(mel, np.float32)[None, :, :, None]})[0]
            feats = (feats + [e.reshape(EMB)])[-FRAMES:]
            if len(feats) < FRAMES:
                continue
            n += 1
            x = np.array(feats, np.float32)[None]
            for k, s in self.heads.items():
                v = float(s.run(None, {s.get_inputs()[0].name: x})[0].reshape(-1)[0])
                out[k].append(0.0 if n <= 5 else v)
        return {k: np.array(v) for k, v in out.items()}


EVAL_POS = {
    "en": ["Jarvis.", "Jarvis?", "Hey Jarvis.", "Jarvis, what time is it?", "Okay Jarvis, buy the second unit."],
    "de": ["[[ dʒˈaːvɪs. ]]", "[[ dʒˈaːɐvɪs? ]]", "Hey [[ dʒˈɑːvɪs. ]]", "[[ dʒˈaːvɪs, ]] wie spät ist es?",
           "Okay [[ dʒˈaʁvɪs, ]] was soll ich kaufen?"],
}
EVAL_NEG = {
    "de": ["Ja, was ist denn los?", "Ja, wieso nicht?", "Der Service war heute wirklich gut.",
           "Das Jahr ist fast vorbei.", "Travis kommt später vorbei.", "Schau mal ins Archiv.",
           "Ich war gestern in Paris.", "Jawohl, das mache ich.", "Hast du die Garage abgeschlossen?",
           "Kauf den zweiten Kämpfer und leg ihn auf die Bank.", "Harvey ist mein Nachbar.",
           "Ja, wir sehen uns morgen wieder.", "Die Kiwis sind noch nicht reif.", "Na, was machst du so?"],
    "en": ["Travis, can you pass me the salt?", "The service is down again.", "Pass me the jar, please.",
           "Davis and Mavis went to Paris.", "Harvest season starts in autumn.", "I'm a bit nervous."],
}


def cmd_evaluate(args):
    from piper import PiperVoice, SynthesisConfig
    heads = {"neu": args.model}
    if args.old:
        heads["alt"] = args.old
    st = Streamer(args.models, heads)
    voices = find_voices(args.voices, holdout_only=not args.all_voices)
    rng = np.random.default_rng(7)
    babble = []
    results = {k: {"pos": [], "neg": []} for k in heads}
    conditions = ["sauber", "Stimmen 10 dB", "Rauschen 15 dB"]
    rows = []
    for vp in voices:
        lang, speakers, _ = lang_of(vp)
        v = PiperVoice.load(vp)
        sr = v.config.sample_rate
        g = math.gcd(sr, SR)
        ev = "de" if lang == "de" else "en"
        for kind, texts in (("pos", EVAL_POS[ev]), ("neg", EVAL_NEG[ev] + EVAL_NEG["en"][:2])):
            for t in texts:
                for ls in (0.85, 1.0, 1.15):
                    cfg = SynthesisConfig(length_scale=ls, speaker_id=0 if speakers > 1 else None)
                    a = np.concatenate([c.audio_float_array for c in v.synthesize(t, syn_config=cfg)])
                    if sr != SR:
                        a = resample_poly(a, SR // g, sr // g)
                    a = (a * 20000).astype(np.float32)
                    babble.append(a)
                    for cond in conditions:
                        x = a.copy()
                        if cond != "sauber" and len(babble) > 4:
                            if cond.startswith("Stimmen"):
                                nz = sum(np.resize(babble[i], len(x)) for i in rng.integers(0, len(babble) - 1, 3))
                                snr = 10
                            else:
                                nz = colored_noise(rng, len(x), "pink")
                                snr = 15
                            x = x + nz * (rms(x) / (rms(nz) * 10 ** (snr / 20) + 1e-6))
                        x = np.clip(x, -32768, 32767).astype(np.int16)
                        sc = st.scores(x)
                        for k in heads:
                            results[k][kind].append(float(sc[k].max()))
                        rows.append((os.path.basename(vp), kind, t, ls, cond,
                                     {k: round(float(sc[k].max()), 3) for k in heads}))
        print(f"  {os.path.basename(vp)} geprüft", flush=True)

    summary = {}
    print(f"\nTeststimmen (nie im Training): {[os.path.basename(v) for v in voices]}")
    for k in heads:
        p, n = np.array(results[k]["pos"]), np.array(results[k]["neg"])
        summary[k] = {}
        print(f"\nModell {k}: {len(p)} Jarvis-Aufnahmen, {len(n)} Gegenbeispiele")
        for sens in (3, 4, 5, 6, 7):
            thr = 0.9 - sens * 0.08
            summary[k][sens] = {"schwelle": round(thr, 2), "erkannt": round(float(np.mean(p >= thr)), 3),
                                "fehlalarme": int(np.sum(n >= thr))}
            print(f"  Empfindlichkeit {sens} (Schwelle {thr:.2f}): erkannt {np.mean(p >= thr):6.1%}   "
                  f"Fehlalarme {int(np.sum(n >= thr))}/{len(n)}")
    print("\nAufschlüsselung (Empfindlichkeit 5, Schwelle 0,50):")
    groups = {}
    for r in rows:
        lang = "de" if "de" in r[0][:5].lower() else "en"
        for key in ((lang, r[1], r[4]), (lang, r[1], "alle"), ("Text " + r[2][:28], r[1], "alle")):
            groups.setdefault(key, []).append(r[5])
    for key in sorted(groups):
        vals = groups[key]
        line = "  ".join(f"{k}: {np.mean([v[k] >= 0.5 for v in vals]):5.0%}" for k in heads)
        print(f"  {key[0]:<36s} {key[1]:3s} {key[2]:<15s} n={len(vals):3d}  {line}")
        summary.setdefault("gruppen", {})["|".join(key)] = {k: round(float(np.mean([v[k] >= 0.5 for v in vals])), 3) for k in heads}
    misses = sorted([r for r in rows if r[1] == "pos"], key=lambda r: r[5]["neu"])[:12]
    fps = sorted([r for r in rows if r[1] == "neg"], key=lambda r: -r[5]["neu"])[:8]
    print("\nSchwächste Jarvis-Fälle:")
    for r in misses:
        print("  ", r)
    print("Stärkste Gegenbeispiele:")
    for r in fps:
        print("  ", r)
    if args.report:
        with open(args.report, "w") as f:
            json.dump({"summary": summary, "rows": rows}, f, ensure_ascii=False, indent=1)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    g = sub.add_parser("generate")
    g.add_argument("--voices", nargs="+", required=True)
    g.add_argument("--work", required=True)
    g.add_argument("--n-pos", type=int, default=20000)
    g.add_argument("--n-neg", type=int, default=20000)
    g.add_argument("--workers", type=int, default=os.cpu_count())
    g.add_argument("--seed", type=int, default=1)

    f = sub.add_parser("features")
    f.add_argument("--work", required=True)
    f.add_argument("--models", required=True, help="Ordner mit melspectrogram.onnx und embedding_model.onnx")
    f.add_argument("--rirs", default=None, help="Ordner mit Raumimpulsantworten (WAV), optional")
    f.add_argument("--noise", default=None, help="Ordner mit Hintergrundgeräuschen (WAV), optional")
    f.add_argument("--pos-copies", type=int, default=3)
    f.add_argument("--neg-copies", type=int, default=2)
    f.add_argument("--workers", type=int, default=os.cpu_count())
    f.add_argument("--seed", type=int, default=2)

    t = sub.add_parser("train")
    t.add_argument("--work", required=True)
    t.add_argument("--out", required=True)
    t.add_argument("--name", default="jarvis")
    t.add_argument("--acav", default=None, help="openwakeword_features_ACAV100M_2000_hrs_16bit.npy (auch teilweise)")
    t.add_argument("--acav-rows", type=int, default=None)
    t.add_argument("--val-fp", default=None, help="validation_set_features.npy")
    t.add_argument("--steps", type=int, default=30000)
    t.add_argument("--batch-pos", type=int, default=64)
    t.add_argument("--batch-neg", type=int, default=128)
    t.add_argument("--batch-acav", type=int, default=1024)
    t.add_argument("--hidden", type=int, default=64)
    t.add_argument("--lr", type=float, default=2e-4)
    t.add_argument("--max-neg-weight", type=float, default=1500.0)
    t.add_argument("--max-fp", type=float, default=0.5, help="Ziel: Fehlalarme pro Stunde")
    t.add_argument("--min-recall", type=float, default=0.85, help="sonst neuer Durchgang mit mehr Gewicht")
    t.add_argument("--attempts", type=int, default=2)
    t.add_argument("--recall-floor", type=float, default=0.3, help="darunter gilt ein Stand als unbrauchbar")
    t.add_argument("--eval-every", type=int, default=1000)
    t.add_argument("--workers", type=int, default=os.cpu_count())
    t.add_argument("--seed", type=int, default=3)

    e = sub.add_parser("evaluate")
    e.add_argument("--model", required=True)
    e.add_argument("--old", default=None)
    e.add_argument("--models", required=True)
    e.add_argument("--voices", nargs="+", required=True)
    e.add_argument("--all-voices", action="store_true")
    e.add_argument("--report", default=None)

    args = ap.parse_args()
    {"generate": cmd_generate, "features": cmd_features, "train": cmd_train, "evaluate": cmd_evaluate}[args.cmd](args)


if __name__ == "__main__":
    main()
