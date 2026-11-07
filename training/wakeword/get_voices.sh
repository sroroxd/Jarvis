#!/usr/bin/env bash
# Lädt Piper-Stimmen für das Wachwort-Training.
#   get_voices.sh ZIEL train   → Trainingsstimmen (Englisch, Deutsch, Niederländisch; viele Sprecher)
#   get_voices.sh ZIEL test    → nur Teststimmen (kerstin, pavoque, alan, amy) – kommen nie ins Training
# Fehlende Stimmen werden übersprungen, der Lauf bricht deshalb nicht ab.
set -u
dest=$1
mode=${2:-train}
mkdir -p "$dest"

gh_voice() {
  mkdir -p "$dest/$1"
  if curl -sfSL "https://github.com/rhasspy/piper/releases/download/v0.0.2/voice-$1.tar.gz" | tar -xz -C "$dest/$1"; then
    echo "ok     $1"
  else
    echo "fehlt  $1"; rm -rf "${dest:?}/$1"
  fi
}

hf_voice() {
  local f
  f=$(basename "$1")
  mkdir -p "$dest/$f"
  local base="https://huggingface.co/rhasspy/piper-voices/resolve/main/$1"
  if curl -sfSL -o "$dest/$f/$f.onnx" "$base.onnx" && curl -sfSL -o "$dest/$f/$f.onnx.json" "$base.onnx.json"; then
    echo "ok     $f"
  else
    echo "fehlt  $f"; rm -rf "${dest:?}/$f"
  fi
}

if [ "$mode" = test ]; then
  for v in de-kerstin-low de-pavoque-low en-gb-alan-low en-us-amy-low; do gh_voice "$v"; done
  for v in de/de_DE/kerstin/low/de_DE-kerstin-low de/de_DE/pavoque/low/de_DE-pavoque-low \
           en/en_GB/alan/medium/en_GB-alan-medium en/en_US/amy/medium/en_US-amy-medium; do hf_voice "$v"; done
  exit 0
fi

for v in de-thorsten-low de-eva_k-x-low de-karlsson-low de-ramona-low \
         en-us-libritts-high en-gb-southern_english_female-low en-us-danny-low en-us-kathleen-low \
         en-us-ryan-high en-us-lessac-medium nl-nathalie-x-low; do gh_voice "$v"; done

for v in en/en_US/libritts_r/medium/en_US-libritts_r-medium \
         en/en_US/l2arctic/medium/en_US-l2arctic-medium \
         en/en_US/arctic/medium/en_US-arctic-medium \
         en/en_GB/vctk/medium/en_GB-vctk-medium \
         en/en_GB/semaine/medium/en_GB-semaine-medium \
         en/en_GB/northern_english_male/medium/en_GB-northern_english_male-medium \
         en/en_US/joe/medium/en_US-joe-medium \
         en/en_US/hfc_male/medium/en_US-hfc_male-medium \
         de/de_DE/mls/medium/de_DE-mls-medium \
         de/de_DE/thorsten_emotional/medium/de_DE-thorsten_emotional-medium \
         de/de_DE/thorsten/high/de_DE-thorsten-high \
         nl/nl_NL/mls/medium/nl_NL-mls-medium; do hf_voice "$v"; done
