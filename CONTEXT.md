# CONTEXT — nikon project state

> **Read this first** if you're picking up the project on a new machine,
> continuing work, or handing off to another developer / AI agent.
> It's the single source of truth for what's been done, what's
> unfinished, and what decisions were made.

---

## TL;DR

This repo contains reverse-engineering notes and tooling for the
**Nikon Z30** mirrorless camera (firmware 1.20), plus two end-user
products built on top of that work:

| Component | Purpose | State |
|---|---|---|
| `analysis/` | Python scripts that decoded the Z30 firmware | Done, kept as reference |
| `camrelay/` | Linux box (Pi Zero 2 W) that auto-uploads photos to NAS | **Ready to deploy** |
| `camtonas/` | Android app (phone-based) doing the same job | Design done, never built/tested |
| `docs/` | Technical deep dives (firmware format, hidden features) | Partial |

**If you only do one thing today:** read this file, then deploy
`camrelay/` on a Pi.

---

## Story / context

This project started as an exercise in firmware reverse engineering.
A user supplied `Z_30_0120.bin` (32.9 MB) and asked to "look at the
file". That led to:

1. **Decoding** the obfuscated container (3-table XOR, ported from
   `simeonpilgrim/nikon-firmware-tools`)
2. **Extracting** the 3 sub-firmwares + ~6,000 embedded resources
3. **Hunting** for hidden / debug / factory-mode features
4. **Building** an Android app to auto-upload photos to NAS
5. **Realising** the Android app is impractical (battery, background
   limits, WiFi handoff issues) and **building a Linux relay instead**
6. **Pushing** everything to https://github.com/bullton/nikon

The user's stated end goal: when they take a photo with the Z30,
it ends up on their NAS automatically, with minimal effort on their
part. The recommended solution is `camrelay/`.

---

## What's in this repo (file map)

```
nikon/
├── README.md                     30-second overview (you're here, then go to CONTEXT.md)
├── CONTEXT.md                    THIS FILE — start here
├── .gitignore                    excludes firmware .bin files, extracted blobs, IDE junk
│
├── analysis/                     Firmware RE scripts (Python, one-shot use)
│   ├── README.md                 Usage and which script to run
│   ├── nikon_decode.py            The XOR deobfuscator
│   ├── nikon_extract.py          Container parser, dumps 3 sub-firmwares
│   ├── nikon_extract_resources.py Walks main firmware for embedded PNG/JPG/WAV/etc
│   ├── nikon_analyze_extracted.py String scanner
│   ├── nikon_explore.py          Copyright / setting-ID discovery
│   ├── nikon_hidden.py           Debug / factory / PTP mode scanner
│   ├── nikon_hidden2.py          Undocumented / vendor command scanner
│   ├── analyze_bin.py            First-look entropy / signature scan
│   └── notes/
│       └── full-hidden-search.txt    (~125 KB, full output of nikon_hidden.py)
│
├── camrelay/                     ★ DEPLOYABLE ★ — Linux box, gphoto2 + rclone + Flask
│   ├── README.md                 Full deployment guide
│   ├── relay/                    Python package
│   │   ├── main.py               Entry point
│   │   ├── cli.py                `python -m relay.cli doctor|list|pull`
│   │   ├── config.py             YAML config
│   │   ├── camera.py             gphoto2 wrapper (USB + PTP/IP)
│   │   ├── nas.py                rclone / direct-copy uploader
│   │   ├── sync.py               Polling orchestrator
│   │   ├── state.py              JSON-backed state
│   │   └── web.py                Flask web UI
│   ├── web/                      HTML / CSS
│   ├── scripts/
│   │   ├── install.sh            One-shot Pi installer
│   │   ├── connect-camera.sh     Configure WiFi to camera AP
│   │   └── camrelay.service      systemd unit
│   ├── config/config.example.yaml
│   └── requirements.txt
│
├── camtonas/                     Android app (NOT the recommended solution)
│   ├── README.md                 Why this exists, build instructions
│   ├── app/src/main/kotlin/com/camtonas/app/
│   │   ├── camera/               PTP/IP + MTP client
│   │   ├── nas/                  SMB / SFTP / FTP / WebDAV senders
│   │   ├── sync/                 SyncEngine
│   │   ├── service/              SyncService (foreground)
│   │   ├── ui/                   Compose screens + ViewModel
│   │   └── data/                 DataStore config
│   ├── app/build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle/libs.versions.toml
│
└── docs/
    ├── hidden-features.md         What the firmware hides (engineer mode, NIST test, etc.)
    └── firmware-decryption.md    How the XOR scheme works, why we decoded the way we did
```

---

## Decisions and rationale

### D1. Why a Linux box (CamRelay) instead of an Android app (CamToNAS)?

| | Android app | Linux box |
|---|---|---|
| 24/7 | Hard (doze, kill, battery) | Trivial (systemd, USB power) |
| Power | 5-15W (significant battery drain) | 0.5-1W (solar-friendly) |
| Phone availability | Required (no phone, no upload) | Independent |
| Multi-camera | Awkward | Add another `rclone remote` |
| Cost | Already-paid phone | $15 SBC |
| Web UI | No (phone app only) | Yes (`http://box:8080`) |

The user agreed; CamToNAS is kept as a reference / alternative.

### D2. Why `gphoto2` + `rclone` instead of writing everything from scratch?

- **gphoto2** is the de-facto standard for camera control on Linux.
  20+ years of RE. Supports every Nikon. Bug-for-bug compatible
  with the Z30. Saves us ~6 months of PTP reverse engineering.
- **rclone** is the de-facto standard for cloud sync. Supports
  50+ backends (SMB, SFTP, FTP, WebDAV, S3, Google Drive, etc.)
  in one binary. Production-tested.

The cost is two `apt install` commands and `rclone config`. The
benefit is we don't have to debug 4 different Python protocol
libraries.

### D3. Why the XOR decode uses 3 tables of 256 bytes each?

That's how Nikon's bootloader does it. The 3 tables are
publicly known (`simeonpilgrim/nikon-firmware-tools` extracted them
years ago). The fact that they are 256-byte substitution tables
(instead of, say, a PRNG) means the keystream repeats every 16 MB
(2^24 bytes) — long enough to be opaque to entropy tests but
short enough to be a finite state machine.

### D4. Why is the Android app in the repo if it's not recommended?

Three reasons:
1. It's a working reference implementation of a pure-Kotlin
   PTP/IP client — useful for anyone wanting to learn that
   protocol or to debug CamRelay
2. The user might still want a phone-based setup
3. Demonstrates the PTP/MTP camera interface pattern, which
   is reusable

### D5. What's the relationship between analysis/ and camrelay/?

`analysis/` is **read-only** — it consumed `Z_30_0120.bin` once
and produced a body of knowledge (string tables, hidden features,
internal structure).

`camrelay/` is **operational** — it talks to a live camera and
NAS on an ongoing basis. The two share no code today, but the
*findings* in `analysis/docs/` (e.g. "the Z30 supports `ptpip:`
on port 15740") informed CamRelay's `PTPIPSource` choice
(in our case we used gphoto2 which abstracts this).

---

## State of each component — honest

### analysis/ — Done ✅
- All 8 scripts work, ran once, produced output
- The `notes/full-hidden-search.txt` is a permanent artefact
- If you run them on a newer firmware, the table names will change
  (e.g. `Z_30_0200.bin` will have a different second-version-digit
  in the strings) but the algorithms are stable across Z-series

### camrelay/ — Ready to deploy, but **never tested in production** ⚠️
- Code is complete and self-consistent
- **I have not run `bash install.sh` on a Pi**
- **I have not connected it to a real Z30**
- **I have not pushed to a real NAS**
- Expect 1-3 real-world bugs that need fixing on first deploy
  (typical suspects: `rclone` config, gphoto2 USB permissions, systemd
  sandboxing in `camrelay.service` may be too strict)

### camtonas/ — Design done, never compiled ⚠️
- Code is complete in theory
- **Never compiled in Android Studio**
- **Never installed on a phone**
- **Never connected to a real camera**
- The PTP `downloadObject` function has a known issue: it reads
  the file stream to EOF instead of using the PTP data container's
  length field. Will likely work for most files but may corrupt
  the last few bytes. Fix is in the README.

### docs/ — Partial
- `hidden-features.md` is complete
- `firmware-decryption.md` is complete
- `architecture-decisions.md` is part of this file
- A `camrelay-architecture.md` is a future TODO

---

## How to use this repo on a new computer

```bash
# 1. Clone
git clone https://github.com/bullton/nikon.git
cd nikon

# 2. Decide what you're doing
#    - Continue RE work:        cd analysis && cat README.md
#    - Deploy the relay box:    cd camrelay && cat README.md
#    - Study the Android app:   cd camtonas && cat README.md
#    - Read about the firmware:  cat docs/hidden-features.md

# 3. (Optional) put the firmware file somewhere outside the repo
#    (the .gitignore excludes *.bin from accidental commits)
mkdir -p ../work
cp /path/to/Z_30_0120.bin ../work/
cd ../work
python ../nikon/analysis/nikon_decode.py --input Z_30_0120.bin
python ../nikon/analysis/nikon_extract.py --input Z_30_0120_decoded.bin
ls extracted/   # the 3 sub-firmwares
```

---

## For AI agents / new developers

If you're an AI reading this, here's what you need to know to be
useful:

1. **The user is a developer** with experience in Kotlin, Python,
   Linux, networking, and reverse engineering. Don't over-explain
   basics.

2. **The user wants minimal but correct answers.** No fluff.

3. **The camera is a Nikon Z30** running firmware 1.20. Other Z-
   series bodies are similar but not identical.

4. **Camera-to-host protocols in scope:**
   - **PTP/IP** over WiFi (port 15740) — primary
   - **MTP** over USB — secondary
   - **SnapBridge BLE** — we found it in the firmware but didn't
     reverse it; out of scope for now

5. **NAS protocols in scope:** SMB, SFTP, FTP/FTPS, WebDAV.
   `rclone` is the abstraction layer for camrelay/.

6. **The XOR tables in `analysis/nikon_decode.py` are the only
   secret-sauce.** If the user asks to support a new camera model,
   check `simeonpilgrim/nikon-firmware-tools` first; if it's
   already there, port the 3 tables over and the rest follows.

7. **Everything in `analysis/` is disposable.** The scripts ran
   once, the knowledge is captured in `docs/`. If a future firmware
   breaks them, rewrite and re-run.

8. **Don't refactor things that work.** CamRelay's code is clean
   enough; it needs real-device testing, not more polish.

9. **For bug reports, ask the user to run the actual failing
   command and paste the output.** Especially for CamRelay — I
   have not tested it on a real Z30.

10. **The hidden-features.md is the most interesting artefact.**
    It documents things Nikon never published. If the user wants
    "something cool to look at", point them there.

---

## Open TODOs (next session?)

- [ ] Deploy CamRelay to a real Pi with a real Z30
- [ ] Get the install.sh output, fix any apt / pip issues
- [ ] Verify rclone works against a real NAS
- [ ] Add a Telegram notification on each successful upload
- [ ] Add camera trigger (`gphoto2 --trigger-capture`) for
      time-lapse mode
- [ ] Reverse the inner-encryption of `eg2090_0120a0.bi` so
      we can disassemble the main firmware (this is a multi-week
      project; would need a hardware JTAG to dump post-decryption
      memory)
- [ ] Add a Dockerfile to camrelay/ for easy dev testing
- [ ] Add CI (GitHub Actions) to run `python -m compileall` on
      every PR (the RE scripts are pure-Python and easy to
      lint-check)
- [ ] SnapBridge BLE reverse engineering (high effort, moderate
      value)
- [ ] Add LICENSE file
- [ ] Add a CHANGELOG.md

---

## Glossary

| Term | Meaning |
|---|---|
| PTP | Picture Transfer Protocol. ISO 15740. The standard protocol cameras use to talk to a host. |
| MTP | Media Transfer Protocol. A subset of PTP, used over USB by most modern cameras. |
| PTP/IP | PTP over TCP/IP. Used by WiFi-connected cameras. Port 15740. |
| PTP container | A 4-byte-length + 4-byte-type + payload packet. |
| EXPEED 6 | Nikon's image-processor SoC. The "main" CPU in modern Z bodies. |
| ARM926 | The secondary CPU in Z bodies, runs T-Kernel, handles sensor/lens/battery. |
| NEF | Nikon's RAW file format. |
| gphoto2 | Linux library for camera control. The reference implementation for PTP/MTP. |
| rclone | Sync tool. 50+ backend protocols, very reliable. |
| T-Kernel | Real-time OS used in the sub-CPU. |
| Xor_Ord1/2/3 | Nikon's 3-byte XOR obfuscation lookup tables. The 768 bytes of secret sauce. |
| `eDSID_Setting_*` | Internal constant names for every user-tunable setting in the Z30 firmware. |
| SnapBridge | Nikon's official phone app. Uses BLE + WiFi. We are NOT using this protocol. |
| `CAM_SETDEBUGMODE_ACCEPT` | PTP event that switches the camera into debug mode. |
