# camtonas — Android app (phone as camera-to-NAS relay)

> ⚠️ This is the **legacy / alternative** implementation. The recommended
> solution is [`camrelay/`](../camrelay/) — a small Linux box that
> does the same job more reliably.
>
> Kept here for: (a) reference, (b) users who really want a phone-only
> setup, (c) anyone wanting to study the WiFi PTP/IP stack I wrote from
> scratch.

## What it is

A native Android 16+ app that:
1. Connects to a Nikon camera over **WiFi (PTP/IP)** or **USB (MTP)**
2. Polls for new JPGs every N seconds
3. Uploads them to a **SMB / SFTP / FTP / WebDAV** NAS

The PTP/IP client is written from scratch in Kotlin (the
`camera/PTPIPSource.kt` file). The NAS senders use real libraries
(`jcifs-ng`, `sshj`, `Apache Commons Net`, `OkHttp`).

## State

- ✅ Code complete
- ✅ Builds cleanly in theory (not actually compiled)
- ⚠️ **Never tested on a real Z30** — the PTP GetObject stream
  parsing needs a real-device roundtrip
- ⚠️ Foreground-service permission dance on Android 16+ needs
  validation (`FOREGROUND_SERVICE_DATA_SYNC` was added in Android 14,
  tightened in 16)

## Build

```bash
# 1. Open in Android Studio (Ladybug or newer)
#    It will generate gradle wrapper automatically.
# 2. Sync project, then Run
```

Or with CLI:
```bash
cd camtonas
gradle wrapper --gradle-version 8.10.2
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture (brief)

```
[Camera]    ─WiFi─►  [PTPIPSource]   ┐
                                     ├─► [SyncEngine] ─► [NASSender] ─► [NAS]
[Camera]    ─USB──►  [MTPSource]     ┘            ▲
                                                  │
                                          [SyncService]  (foreground)
                                                  │
                                          [SyncViewModel]
                                                  │
                                          [Compose UI]
```

| Module | Notes |
|---|---|
| `camera/PTPIPSource.kt` | Pure-Kotlin PTP-over-IP client. No external deps. |
| `camera/MTPSource.kt` | Wraps `MediaStore` for USB-MTP-camera files |
| `camera/PTPContainer.kt` | PTP/IP container encoder/decoder |
| `nas/SMBSender.kt` | `jcifs-ng` 3.0.1 (SMB2/SMB3) |
| `nas/SFTPSender.kt` | `sshj` 0.38.0 + BouncyCastle |
| `nas/FTPSender.kt` | Apache Commons Net |
| `nas/WebDAVSender.kt` | OkHttp + custom MKCOL/PUT |
| `sync/SyncEngine.kt` | Polling, downloading, uploading |
| `service/SyncService.kt` | Foreground service with `dataSync` type |
| `ui/` | Material 3 Compose: Home, Settings, Logs |

## Why this isn't the primary solution

| | Android app (camtonas) | Pi box (camrelay) |
|---|---|---|
| Phone must be in range | Yes (it's the radio) | No |
| Battery drain | Significant | Negligible (1W) |
| 7×24 operation | Hard (kill, doze, doze) | Easy (systemd) |
| Multi-camera support | Awkward | Easy |
| Cost | Already-paid phone | $15 SBC |
| Web UI | No (only phone app) | Yes (`http://box:8080`) |

For a long-term install, use CamRelay. Use CamToNAS only if you
specifically need a phone-based setup or want to study the
PTP/IP code.

## See also

- `CONTEXT.md` — overall project context
- `../camrelay/README.md` — recommended solution
- `../analysis/` — firmware reverse engineering scripts
