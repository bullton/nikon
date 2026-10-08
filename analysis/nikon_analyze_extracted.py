import os, struct, math, collections, hashlib, zlib, re

def entropy(data):
    if not data: return 0
    freq = collections.Counter(data)
    n = len(data)
    return -sum((c/n) * math.log2(c/n) for c in freq.values())

extracted_dir = r"D:\Code\Nikon\extracted"

# Analyze each extracted file in detail
files = sorted(os.listdir(extracted_dir))
for fname in files:
    path = os.path.join(extracted_dir, fname)
    if not os.path.isfile(path):
        continue
    with open(path, "rb") as f:
        data = f.read()
    print(f"\n{'='*70}\n{fname}: {len(data):,} bytes, entropy={entropy(data):.4f}\n{'='*70}")

    # Find ASCII strings of length >= 6
    runs = []
    cur = b""
    start = 0
    for i, b in enumerate(data):
        if 0x20 <= b < 0x7F:
            if not cur: start = i
            cur += bytes([b])
        else:
            if len(cur) >= 6: runs.append((start, cur))
            cur = b""
    if len(cur) >= 6: runs.append((start, cur))
    runs.sort(key=lambda x: -len(x[1]))

    print(f"\nTop 30 ASCII strings (len>=6):")
    for s, r in runs[:30]:
        try:
            text = r.decode('latin-1')
        except: text = repr(r)
        print(f"  0x{s:08X} len={len(r):4d}  {text[:120]}")

    # Find interesting data (RIFF, PNG, JPEG, ZIP, GZIP)
    sigs = {
        b"RIFF": ("RIFF", 4),     # need 4 more bytes for size, then 4 for type
        b"\x89PNG\r\n\x1a\n": ("PNG", 0),
        b"\xFF\xD8\xFF": ("JPEG", 0),
        b"PK\x03\x04": ("ZIP", 0),
        b"%PDF": ("PDF", 0),
        b"7z\xBC\xAF\x27\x1C": ("7Z", 0),
        b"\x1F\x8B\x08": ("GZIP", 0),
        b"BZh": ("BZIP2", 0),
        b"\xFD7zXZ\x00": ("XZ", 0),
        b"Rar!": ("RAR", 0),
        b"ustar": ("TAR", 0),
        b"\x7FELF": ("ELF", 0),
        b"MZ": ("PE", 0),
        b"fLaC": ("FLAC", 0),
        b"OggS": ("OGG", 0),
        b"ID3": ("MP3-ID3", 0),
        b"\xFF\xFB": ("MP3", 0),
        b"BM": ("BMP", 0),
        b"<?xml": ("XML", 0),
        b"<html": ("HTML", 0),
        b"<!DOCTYPE": ("HTML5", 0),
        b"<?xpacket": ("XMP", 0),
        b"WAVE": ("WAVE", 0),  # inside RIFF
        b"ftyp": ("MP4-ftyp", 0),
        b"CrAU": ("AppleAU", 0),
        b"\x00\x00\x01\x00": ("ICO/CUR", 0),
        b"TTF": ("TrueType", 0),  # at offset 0
    }
    found = collections.defaultdict(list)
    for sig, (name, extra) in sigs.items():
        pos = 0
        while True:
            idx = data.find(sig, pos)
            if idx < 0: break
            found[name].append(idx)
            pos = idx + 1
            if len(found[name]) > 6: break
    if found:
        print(f"\nSignatures inside:")
        for name, locs in sorted(found.items()):
            print(f"  {name:10s}: {len(locs)} match(es) at {[hex(x) for x in locs[:6]]}")

    # Look for the "Ver." version marker
    if b"Ver." in data:
        for m in re.finditer(rb'Ver\.\d+\.\d+\.\w+', data):
            print(f"  Version marker: {m.group().decode('latin-1')!r} at 0x{m.start():08X}")

    # Check if the data has ARM code (looking for E prefix in 4-byte little-endian = 0xE...)
    # Count 0xE? thumb2 instructions or 0xExxx ARM data processing
    if len(data) > 1000:
        sample = data[:10000]
        e_count = sum(1 for b in sample if (b & 0xF0) == 0xE0)
        print(f"  ARM-like 0xExxx frequency in first 10KB: {e_count}/{len(sample)} = {e_count/len(sample)*100:.1f}%")
        b_count = sum(1 for b in sample if b == 0x47)  # "BX LR"
        print(f"  BX LR (0x47) frequency: {b_count}")
        e9_count = sum(1 for b in sample if b == 0xE9)
        print(f"  0xE9 (prefetch abort/BLX) frequency: {e9_count}")
