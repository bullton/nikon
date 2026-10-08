# analysis — Nikon Z30 firmware reverse engineering scripts

Python scripts I used to reverse-engineer the Nikon Z30 firmware
`Z_30_0120.bin` (version 1.20, 32.9 MB). Kept here as a reference
for when the next firmware version drops and I (or someone else)
wants to do this again.

## The full decode flow

```
Z_30_0120.bin                       raw encrypted firmware (32.9 MB)
       │
       ▼  analyze_bin.py            entropy / signature scan
       │
       ▼  nikon_decode.py           XOR-deobfuscate (3-table, offset 0x20)
       │
       ▼  Z_30_0120_decoded.bin    decoded container (still encrypted inner blobs)
       │
       ▼  nikon_extract.py          parse container, split into 3 sub-firmwares
       │
       ├── ex2090_012000.bi        exception/bootloader (524 KB)
       ├── _tpj04_v10a5.bin        lens module (32 KB)
       └── eg2090_0120a0.bi        main application firmware (32.4 MB)
                                   │
                                   ▼  nikon_extract_resources.py
                                   │
                                   ├── 5,424 PNG icons
                                   ├── 644 JPEG images
                                   ├── 2 WAV audio (60s theme + 11s)
                                   ├── 2 BZIP2 blobs
                                   └── 8 XMP metadata blocks
       │
       ▼  nikon_explore.py         string scan, copyright / setting IDs
       │
       ▼  nikon_hidden.py          debug / factory / service-mode scan
       │
       ▼  nikon_hidden2.py         undocumented / PTP command scan
       │
       ▼  notes/full-hidden-search.txt
                                   full report
```

## Scripts

| File | What it does |
|---|---|
| `analyze_bin.py` | First-look: file size, SHA1, entropy, byte distribution, known-signature scan, printable-ASCII run extraction |
| `nikon_decode.py` | Implements the 3-table XOR used by Nikon's `Z` series containers, applied from offset `0x20`. Produces `Z_30_0120_decoded.bin` |
| `nikon_extract.py` | Parses the decoded container's 16-byte header + 3 entries and dumps each sub-firmware to disk |
| `nikon_extract_resources.py` | Walks the main firmware for PNG/JPEG/RIFF-WAV/BZIP2/XMP signatures and dumps them to `resources/` |
| `nikon_analyze_extracted.py` | ASCII string extraction, copyright & version-string discovery, formatting of large runs |
| `nikon_explore.py` | Targeted searches: copyright, debug, test paths, eDSID_Setting_*, audio file metadata, JPEG dimensions |
| `nikon_hidden.py` | Debug/PTP/engineer-mode/hidden-feature hunt |
| `nikon_hidden2.py` | Categorised eDSID_Setting_*, WiFi/USB vendor code search, firmware-signing trace |

## How to run

Drop the firmware file in this directory as `Z_30_0101.bin`
(or any name; pass it via `--input`).

```bash
# Decode and extract
python nikon_decode.py --input Z_30_0101.bin --offset 0x20
python nikon_extract.py --input Z_30_0101_decoded.bin
python nikon_extract_resources.py --input extracted/eg2090_0101a0.bi

# Hunt for hidden features
python nikon_hidden.py  --input extracted/eg2090_0101a0.bi
python nikon_hidden2.py --input extracted/eg2090_0101a0.bi
```

Each script writes its own output (e.g. `notes/full-hidden-search.txt`).

## Key findings from 1.20

The most interesting things in the firmware are documented in
[`docs/hidden-features.md`](../docs/hidden-features.md). Top picks:

- **`CAM_SETDEBUGMODE_ACCEPT`** — a PTP operation code that switches
  the camera to debug mode. Accepts commands over USB/WiFi.
- **`NIST Display Test Pattern`** — calibration target never exposed
  in the user menu.
- **20+ creative Picture Controls** baked in (`Toy`, `Pop`, `Sunday`,
  `Silence`, `Pure`, `Red`, `Melancholic`, `Pink`, `Morning`,
  `Bleach`, `Charcoal`, `Denim`, `Carbon`, `Binary`, `Drama`,
  `Graphite`, `Blue`).
- **`NIST Display Test Pattern`** string at offset 0x675C33.
- **Factory test entries** in the bootloader (`BatCmCy1..CyF`,
  `McMngFlg`, `PowMgCyc`, `WatchDogCyc`).
- **Full custom WiFi SSID prefix** + `A:\DEBUG.BIN` debug-log path.

## Where to extend

| Goal | Look at |
|---|---|
| Diff with a different firmware version | `nikon_decode.py` → `nikon_extract.py` → `nikon_explore.py`, then `diff -u` the text outputs |
| Add a new resource type (e.g. PDF, MP3) | Add to the `sigs` dict in `nikon_extract_resources.py` |
| Search for new "hidden" commands | Add patterns to `patterns[]` in `nikon_hidden.py` |
| Parse a different Nikon model | `Xor_Ord1/2/3` in `nikon_decode.py` are the same across Z series; for older models (D series) use `firmware decode/` from `simeonpilgrim/nikon-firmware-tools` |

## Provenance

The XOR deobfuscation algorithm is ported from
[`simeonpilgrim/nikon-firmware-tools`](https://github.com/simeonpilgrim/nikon-firmware-tools)
(`firmware decode/Firmware.cs`). The 3 lookup tables and the
`data[i+offset] ^ Xor_Ord1[i&0xFF] ^ Xor_Ord2[(i>>8)&0xFF] ^ Xor_Ord3[(i>>16)&0xFF]`
form are unchanged; only the language changed (C# → Python).
