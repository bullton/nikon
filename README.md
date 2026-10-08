# nikon — Nikon Z30 reverse engineering + tools

This repository contains reverse-engineering notes and tooling for the
**Nikon Z30 mirrorless camera** (firmware 1.20), plus two end-user
products built on top of that work:

| Path | What it is | State |
|---|---|---|
| [`analysis/`](analysis/) | Python scripts that decoded the firmware and extracted resources | ✅ used once, kept as reference |
| [`camrelay/`](camrelay/) | Small Linux box (Pi Zero 2 W) that auto-uploads photos to NAS | ✅ ready to deploy |
| [`camtonas/`](camtonas/) | Android app (same purpose, phone-based) | ⚠️ design done, untested |
| [`CONTEXT.md`](CONTEXT.md) | **Project context — read this first** when picking up the project on a new machine |

If you only have 5 minutes, read [`CONTEXT.md`](CONTEXT.md).
If you only have 30 seconds, read the [TL;DR](CONTEXT.md#tldr).

---

## Quick links

- [How to deploy CamRelay on a Pi](camrelay/README.md) (~5 min)
- [How to rebuild the Android app](camtonas/README.md) (if I add one)
- [Firmware decryption walk-through](analysis/README.md) (if I add one)
- [Hidden features found in 1.20](docs/hidden-features.md) (if I add one)

---

## Repository layout

```
nikon/
├── README.md                    ← you are here
├── CONTEXT.md                   ← read first
├── .gitignore
├── analysis/                    ← firmware RE scripts (Python)
│   ├── README.md
│   ├── nikon_decode.py          ← XOR-obfuscation stripper
│   ├── nikon_extract.py         ← container parser
│   ├── nikon_extract_resources.py
│   ├── nikon_analyze_extracted.py
│   ├── nikon_explore.py
│   ├── nikon_hidden.py
│   ├── nikon_hidden2.py
│   ├── analyze_bin.py
│   └── notes/
├── camrelay/                    ← deployable Linux relay
│   ├── README.md
│   ├── relay/
│   ├── web/
│   ├── scripts/
│   ├── config/
│   └── requirements.txt
├── camtonas/                    ← Android app source
│   ├── README.md (TBD)
│   ├── app/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
└── docs/                        ← deep dives (TBD)
```

## License

MIT — see [LICENSE](LICENSE) (TBD).
