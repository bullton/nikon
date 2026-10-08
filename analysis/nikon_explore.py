import os, re, struct, hashlib, collections

extracted_dir = r"D:\Code\Nikon\extracted"
out = r"D:\Code\Nikon\analysis"
os.makedirs(out, exist_ok=True)

# Load both main firmware files
with open(os.path.join(extracted_dir, "eg2090_0120a0.bi"), "rb") as f:
    main = f.read()
with open(os.path.join(extracted_dir, "ex2090_012000.bi"), "rb") as f:
    boot = f.read()

def find_strings(data, min_len=6, limit=2000):
    """Find all printable ASCII runs of length >= min_len"""
    runs = []
    cur = b""
    start = 0
    for i, b in enumerate(data):
        if 0x20 <= b < 0x7F or b in (0x09, 0x0A, 0x0D):
            if not cur: start = i
            cur += bytes([b])
        else:
            if len(cur) >= min_len:
                runs.append((start, cur))
            cur = b""
    if len(cur) >= min_len: runs.append((start, cur))
    return runs

# ============ 1. Copyrights and ownership ============
print("=" * 70)
print("1. COPYRIGHT & LICENSE STRINGS")
print("=" * 70)
all_strs = find_strings(main, 8) + find_strings(boot, 8)
copyrights = []
for off, s in all_strs:
    text = s.decode('latin-1', errors='replace')
    if re.search(r'Copyright|©|All rights reserved|License|MIT|GPL|BSD|Apache', text, re.I):
        copyrights.append((off, text))
for off, t in copyrights[:50]:
    print(f"  0x{off:08X}: {t[:140]}")

# ============ 2. Debug / internal / hidden strings ============
print("\n" + "=" * 70)
print("2. DEBUG / FACTORY / SERVICE-MODE STRINGS")
print("=" * 70)
patterns = [
    r'\bdebug\b', r'\btest_?\w*', r'\bfactory\b', r'\bservice\b', r'\bdev\b',
    r'\bhidden\b', r'\bsecret\b', r'\bbackdoor\b', r'\bcheat\b',
    r'\bprototype\b', r'\bbypass\b', r'\bunlock\b', r'\broot\b',
    r'serial', r'password', r'admin',
]
for pat in patterns:
    rgx = re.compile(pat.encode(), re.I)
    matches = []
    for m in rgx.finditer(main):
        # Find surrounding context
        s = max(0, m.start() - 30)
        e = min(len(main), m.end() + 60)
        ctx = main[s:e]
        # Find the printable run containing this match
        runs = find_strings(ctx, 4)
        for off2, r in runs:
            if m.group() in r:
                matches.append((main.find(r), r.decode('latin-1', 'replace')))
                break
    if matches:
        print(f"\n  Pattern '{pat}': {len(matches)} matches (showing up to 8)")
        for off, t in matches[:8]:
            print(f"    0x{off:08X}: {t[:120]}")

# ============ 3. Camera settings / menu text ============
print("\n" + "=" * 70)
print("3. CAMERA SETTINGS (likely menu text)")
print("=" * 70)
known_settings = [
    'AF-S', 'AF-C', 'AF-A', 'AF-F', 'MF',
    'ISO', 'WB', 'White Balance', 'Picture Control',
    'BKT', 'HDR', 'EXPEED', 'Active D-Lighting',
    'NEF', 'RAW', 'JPEG', 'TIFF', 'MOV', 'MP4',
    'Bluetooth', 'Wi-Fi', 'SnapBridge', 'NFC',
    'EXPEED 6', 'SnapBridge',
    'firmware', 'version', 'copyright',
    'Bluetooth', 'WiFi',
    'movie', 'video', 'audio',
    'metering', 'focus', 'exposure',
    'bulb', 'timer', 'interval',
    'remote', 'MC-N10', 'ML-L7',
    'EN-EL25', 'battery',
    'USB', 'HDMI',
    '4K', '1080p', '120p', '60p', '30p',
    'Z 30', 'Z 50', 'Z fc', 'Z 5', 'Z 6', 'Z 7', 'Z 8', 'Z 9',
    'f/1.8', 'f/2.8', 'f/3.5', 'f/4',
]
unique = set()
for kw in known_settings:
    pat = re.compile(re.escape(kw).encode(), re.I)
    for m in pat.finditer(main):
        ctx = main[max(0,m.start()-20):min(len(main), m.end()+50)]
        runs = find_strings(ctx, 3)
        for off2, r in runs:
            txt = r.decode('latin-1', 'replace')
            if kw.lower() in txt.lower() and len(txt) > 5 and txt not in unique:
                unique.add(txt)
                print(f"    0x{main.find(r):08X}: {txt[:100]}")

# ============ 4. Look at WAV files ============
print("\n" + "=" * 70)
print("4. AUDIO FILES (RIFF/WAV)")
print("=" * 70)
res_dir = r"D:\Code\Nikon\resources"
for fn in sorted(os.listdir(res_dir)):
    if fn.endswith('.wav'):
        path = os.path.join(res_dir, fn)
        size = os.path.getsize(path)
        with open(path, 'rb') as f:
            hdr = f.read(64)
        # Parse RIFF header
        if hdr[:4] == b'RIFF' and hdr[8:12] == b'WAVE':
            # Find 'fmt ' chunk
            fmt_idx = hdr.find(b'fmt ')
            if fmt_idx >= 0 and fmt_idx + 12 < len(hdr):
                audio_fmt = struct.unpack('<H', hdr[fmt_idx+8:fmt_idx+10])[0]
                channels = struct.unpack('<H', hdr[fmt_idx+10:fmt_idx+12])[0]
                sample_rate = struct.unpack('<I', hdr[fmt_idx+12:fmt_idx+16])[0]
                byte_rate = struct.unpack('<I', hdr[fmt_idx+16:fmt_idx+20])[0]
                block_align = struct.unpack('<H', hdr[fmt_idx+20:fmt_idx+22])[0]
                bits = struct.unpack('<H', hdr[fmt_idx+22:fmt_idx+24])[0]
                duration = size / byte_rate if byte_rate else 0
                fmt_name = {1: 'PCM', 3: 'IEEE float', 6: 'A-law', 7: 'mu-law', 17: 'IMA-ADPCM', 0x55: 'MP3'}.get(audio_fmt, f'fmt={audio_fmt}')
                print(f"  {fn}: {size:,} bytes")
                print(f"    Format: {fmt_name}, channels={channels}, rate={sample_rate}Hz, bits={bits}, duration={duration:.1f}s")

# ============ 5. Image dimensions of largest images ============
print("\n" + "=" * 70)
print("5. LARGEST EXTRACTED IMAGES")
print("=" * 70)
big = []
for fn in os.listdir(res_dir):
    if fn.endswith(('.png', '.jpg', '.bmp')):
        path = os.path.join(res_dir, fn)
        big.append((os.path.getsize(path), fn))
big.sort(reverse=True)
for sz, fn in big[:15]:
    print(f"  {sz:>12,} bytes  {fn}")

# PNG dimensions
print("\n  PNG dimensions (largest 5):")
for sz, fn in big[:5]:
    if not fn.endswith('.png'): continue
    path = os.path.join(res_dir, fn)
    with open(path, 'rb') as f:
        d = f.read(24)
    if d[:8] == b'\x89PNG\r\n\x1a\n':
        w, h = struct.unpack('>II', d[16:24])
        print(f"    {fn}: {w}x{h} ({w*h:,} pixels)")

# JPEG dimensions (parse SOF marker)
print("\n  JPEG dimensions (largest 5 jpg):")
jpg_big = [(sz, fn) for sz, fn in big if fn.endswith('.jpg')][:5]
for sz, fn in jpg_big:
    path = os.path.join(res_dir, fn)
    with open(path, 'rb') as f:
        d = f.read(200000)
    # Find SOF0/SOF2 marker (0xFFC0 or 0xFFC2)
    i = 0
    while i < len(d) - 9:
        if d[i] == 0xFF and d[i+1] in (0xC0, 0xC1, 0xC2, 0xC3):
            h = struct.unpack('>H', d[i+5:i+7])[0]
            w = struct.unpack('>H', d[i+7:i+9])[0]
            print(f"    {fn}: {w}x{h} ({w*h:,} pixels)")
            break
        i += 1

# ============ 6. Cross-firmware: model identifiers in bootloader ============
print("\n" + "=" * 70)
print("6. BOOTLOADER (ex2090_012000.bi) - Key strings")
print("=" * 70)
boot_strs = find_strings(boot, 8)
# Print all strings that look meaningful
seen = set()
for off, s in boot_strs:
    txt = s.decode('latin-1', 'replace')
    if len(txt) >= 8 and txt not in seen:
        seen.add(txt)
        if re.search(r'[a-zA-Z]{3,}', txt) and not re.match(r'^[!-/]+$', txt) and not re.match(r'^[0-9 .-]+$', txt):
            print(f"  0x{off:05X}: {txt[:120]}")
