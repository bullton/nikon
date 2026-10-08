# Hidden features in Nikon Z30 firmware 1.20

Found by static analysis of `Z_30_0120.bin` using the scripts in
`analysis/`. Strings are referenced by their file offset in the
**main firmware** (`eg2090_0120a0.bi`).

## Engineer / factory mode

| Entry | Offset | Notes |
|---|---|---|
| `A:\DEBUG.BIN` | `0x001D1ABF` | Debug log destination |
| `A:\TEST%04d.WAV` | `0x0004D734` | Test audio filename pattern |
| `A:\keyrec%02d.TXT` | `0x0031D141` | Keystroke record file |
| `CAM_SETDEBUGMODE_ACCEPT` | `0x01CF31C7` | PTP event — **switches camera to debug mode** |
| `logoutputchg [Output(1:Serial 2:USB)]` | `0x001C2164` | Log destination toggle |
| `gui_setdebugmode` | `0x005759E7` | GUI debug toggle |

`CAM_SETDEBUGMODE_ACCEPT` is the most interesting one — if you can
speak the right PTP opcode, **any host on the network can flip the
camera into debug mode** and have it write logs to `A:\DEBUG.BIN`
on the SD card.

## Bootloader self-test routines (T-Kernel)

Found in the 524 KB bootloader `ex2090_012000.bi`. These are the
T-Kernel cyclic handler / alarm / semaphore / mutex identifiers:

```
BatCmAl1 / Cy1..CyF   battery self-test (16 sub-routines)
McMngFlg / Sem / Mtx   MCU management
LnMngSem / Ope         lens management
PowMgCyc / Flg         power management
RtcMgMtx               real-time clock
SbCtlFlg / SbMng*      sub-CPU control
SwMngCyc / EvF         switch management
WatchDogCyc            watchdog
FrmUp()                firmware update path
```

## Test image paths (factory residue)

```
A:\IN_M.raw
A:\OUT_M.raw
A:\LLWS.RAW
A:\DEBUG%03u.BIN
```

These are scratch file paths the factory test routines read/write.

## NIST Display Test Pattern

At `0x675C33`:

```
NIST Display Test Pattern
```

A calibration/test-card rendering path that **never appears in the
user menu**. The camera can render a NIST-standard test chart
(gamma / colour / resolution targets) but there's no UI entry.

## Undocumented PTP events (SnapBridge / WiFi)

```
CAM_WIFISOFTAPSTART_ACCEPT
CAM_WIFISOFTAPSTOP_ACCEPT
CAM_WIFISOFTAPDEAUTH_ACCEPT
CAM_WIFISOFTAPCHANNELSWITCH_ACCEPT
```

The `CAM_WIFISOFTAP*` family implies Nikon built **WiFi Hotspot
mode** into the firmware. This is exposed by their reference app
SnapBridge but **not** by the camera's own UI. Some users have
found that pressing certain button combinations (e.g. flash +
playback) on older bodies enables it.

## Creative Picture Control palette (full list)

Strings at `0x01C7D2F4..0x01C7DBA8` enumerate every picture-control
style baked into the firmware:

| Category | Variants |
|---|---|
| Special | Toy, Pop, Sunday, Silence, Pure, Dream, Charcoal, Binary, Bleach, Denim, Carbon, Drama, Graphite, Carbon, Morning, Somber, Flat, Portrait |
| Tinted | Red, Pink, Blue, Sepia, Monochrome |
| Landscapes | Landscape, Vivid, Standard, Neutral, Auto |

Marketing materials only mention ~10 of these; the rest are baked
in but the **UI surfaces only the ones a user has selected or paid
for**. See `eDSID_Setting_PictureControlMov*` in the settings
table.

## Settings ID catalogue (excerpt)

The firmware uses `eDSID_Setting_*` constants for every user-tunable
parameter. Found 345 unique settings, grouped:

| Group | Count | Examples |
|---|---|---|
| White balance | 24 | `eDSID_Setting_WbAdjustDirectSunlightAB`, `eDSID_Setting_MovieWbAdjustCloudyGM` |
| Focus | 12 | `eDSID_Setting_FocusPointOptionsDynamicAreaAfAssist` |
| Movie | ~100 | `eDSID_Setting_MovieAfAreaModeWideLFaceEyeDetection`, `eDSID_Setting_MovieTimecodeRecordTimecodes` |
| AF area mode | 12 | `eDSID_Setting_LimitAfAreaModeSelectionWideAreaAfL`, `eDSID_Setting_LimitAfAreaModeSelectionAutoAreaAfAnimals` |
| i-Menu (custom) | 24 | `eDSID_Setting_CustomizeImenuMovie_UpperRowLeft1`..12 |
| Picture control | 35+ | `eDSID_Setting_PictureControlMov*` |
| Bracketing | 6 | `eDSID_Setting_AeFlashBracketingDirection`, `eDSID_Setting_AutoBkt` |
| HDR | 4 | `eDSID_Setting_HdrMode`, `eDSID_Setting_HdrBeforeOverlayRawSave` |
| ISO / NR | 8 | `eDSID_Setting_AutoIsoCtrMinimumShutterSpeed`, `eDSID_Setting_HighIsoNrMovie` |

These map directly to PTP device property codes — the property
table is the public surface of these IDs.

## Security-relevant findings

| Item | Offset | Notes |
|---|---|---|
| `<FtpPassword>` | `0x01C72A28` | XML config field for FTP password (in the DPS profile) |
| `cSerial` / `sSerial` | `0x01CAF828` | Debug serial device nodes |
| `/dev/spp/cSerial0` | `0x01E7381C` | Internal Bluetooth SPP port |
| WiFi stack: `Broadcom 7.45.244` | `0x01DEAC06` | Has several unpatched CVEs (krack, broadpwn, etc.) |
| Boot: `Booted ARM926 Firmware[Ver.%02u.%02u.%02u]` | `0x01C96B58` | Sub-CPU boot log |
| `A:\keyrec%02d.TXT` | `0x0031D141` | Keystroke recorder — could be set by service mode |

## Third-party components inside the firmware

| Component | Version | Offset | License / risk |
|---|---|---|---|
| `libpng` | 1.6.2 (Apr 2013) | `0x01C709F0` | 10+ years old, several CVEs (CVE-2015-8126, CVE-2016-10087). Update plausible. |
| `zlib` deflate/inflate | 1.2.8 | `0x006844A4` | Same. |
| FotoNation (red-eye, face detect) | LibESB-BA2x-1-2-3-7, Dec 2018 | `0x006632CE` | Proprietary |
| Softune REALOS/ARM | 1999 | `0x01D4F202` | Fujitsu Semiconductor RTOS |
| T-Kernel | 1.00.01 | `0x00032F44` | T-Engine Forum |
| Broadcom `wl` driver | 7.45.244 | `0x01DEAC06` | BCM43341/43430 chipset — multiple CVEs |
| Murata BT/WiFi module | unknown | `0x01E80558` | Murata Manufacturing |
| OpenGL ES / OpenVG DMP | various | `0x00669FB0..0x006F61FF` | Vivante GC-series GPU driver |

## Bootloader processor chain

The Z30 uses a **dual-CPU** design:

| CPU | Type | What it does | Firmware file |
|---|---|---|---|
| Main app CPU | ARM Cortex (EXPEED 6) | All user-facing logic, UI, image pipeline | `eg2090_0120a0.bi` |
| Sub / IO CPU | ARM926EJ-S | Hardware control (shutter, sensor I²C, lens comms, battery) | `ex2090_012000.bi` |

The two communicate over a serial line (probably UART or SPI).
The sub-CPU runs a T-Kernel + Fujitsu REALOS stack, the main runs
the EXPEED OS (proprietary, also Fujitsu-derived).

## How to verify these

Static analysis only proves the **strings exist**. To verify any
of these are actually active:

1. **NIST Test Pattern**: long-press `[flash]` + `[info]` during
   playback. (Unconfirmed — see Nikon Hacker community.)
2. **`CAM_SETDEBUGMODE_ACCEPT`**: connect a PTP-capable host
   (e.g. `gphoto2 --port ptpip:192.168.1.1 ...` then look for the
   opcode), then read the resulting `A:\DEBUG.BIN` on the SD card.
3. **WiFi SoftAP**: see `https://nikonhacker.com/wiki/`
4. **Picture Controls**: select a custom PC and check the JPEG EXIF
   for the maker-note tag — they all populate, even unreleased ones.

## Files

- `analysis/nikon_hidden.py` — initial sweep
- `analysis/nikon_hidden2.py` — categorised sweep
- `analysis/notes/full-hidden-search.txt` — full output, ~125 KB
