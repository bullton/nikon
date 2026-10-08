import os, math, re, hashlib, collections, struct, zlib

path = r"D:\Code\Nikon\Z_30_0120.bin"
size = os.path.getsize(path)
print(f"File: {os.path.basename(path)}")
print(f"Size: {size} bytes ({size/1024/1024:.2f} MB)  hex=0x{size:X}")
print(f"SHA1: {hashlib.sha1(open(path,'rb').read()).hexdigest()}")

with open(path, "rb") as f:
    data = f.read()

# Header bytes
print("\n=== Header (first 256 bytes hex) ===")
for i in range(0, 256, 16):
    chunk = data[i:i+16]
    hexs = " ".join(f"{b:02X}" for b in chunk)
    ascii = "".join(chr(b) if 32 <= b < 127 else "." for b in chunk)
    print(f"{i:08X}  {hexs:<48}  {ascii}")

# Tail bytes
print("\n=== Tail (last 256 bytes hex) ===")
for i in range(size-256, size, 16):
    chunk = data[i:i+16]
    hexs = " ".join(f"{b:02X}" for b in chunk)
    ascii = "".join(chr(b) if 32 <= b < 127 else "." for b in chunk)
    print(f"{i:08X}  {hexs:<48}  {ascii}")

# Entropy calculation
print("\n=== Entropy (sampled) ===")
for off in [0, size//4, size//2, 3*size//4, size-65536]:
    sample = data[off:off+65536]
    freq = collections.Counter(sample)
    ent = -sum((c/len(sample)) * math.log2(c/len(sample)) for c in freq.values())
    print(f"  offset 0x{off:08X}: entropy={ent:.4f} bits/byte, unique bytes={len(freq)}/256")

# Search for printable ASCII strings of length >= 8
print("\n=== Printable ASCII runs (length >= 8) ===")
runs = []
current = b""
start = 0
for i, b in enumerate(data):
    if 0x20 <= b < 0x7F:
        if not current:
            start = i
        current += bytes([b])
    else:
        if len(current) >= 8:
            runs.append((start, current))
        current = b""
        start = i+1
if len(current) >= 8:
    runs.append((start, current))

# Limit to top N longest
runs.sort(key=lambda x: -len(x[1]))
print(f"Total runs found: {len(runs)}")
print("Top 30 longest:")
for s, r in runs[:30]:
    print(f"  0x{s:08X}  len={len(r):4d}  {r[:120].decode('latin-1', errors='replace')}{'...' if len(r)>120 else ''}")

# Search for UTF-16 strings
print("\n=== UTF-16LE ASCII runs (length >= 8) ===")
utf16_runs = []
i = 0
while i < len(data)-1:
    if 0x20 <= data[i] < 0x7F and data[i+1] == 0:
        j = i
        while j < len(data)-1 and 0x20 <= data[j] < 0x7F and data[j+1] == 0:
            j += 2
        runlen = (j-i)//2
        if runlen >= 8:
            utf16_runs.append((i, runlen, data[i:j].decode('utf-16le', errors='replace')))
        i = j
    else:
        i += 1
print(f"Found: {len(utf16_runs)}")
for s, l, t in utf16_runs[:20]:
    print(f"  0x{s:08X}  len={l:4d}  {t[:80]}")

# Check for known file signatures in body
print("\n=== Known signatures in file body ===")
sigs = {
    b"\x89PNG\r\n\x1a\n": "PNG",
    b"\xFF\xD8\xFF": "JPEG",
    b"GIF8": "GIF",
    b"PK\x03\x04": "ZIP/DOCX/JAR",
    b"%PDF": "PDF",
    b"7z\xBC\xAF\x27\x1C": "7-Zip",
    b"\x1F\x8B": "GZIP",
    b"BZh": "BZIP2",
    b"\xFD7zXZ\x00": "XZ",
    b"Rar!": "RAR",
    b"ustar": "TAR",
    b"-----BEGIN": "PEM cert",
    b"\x7FELF": "ELF",
    b"MZ": "PE/DOS EXE",
    b"\xCA\xFE\xBA\xBE": "Java class",
    b"CrAU": "Apple software update",
    b"Plist": "Apple plist (ASCII)",
    b"bplist": "Apple binary plist",
    b"NANDC": "Nikon NAND container?",
    b"\x00\x00\x00\x14ftyp": "MP4/MOV (ftyp box)",
    b"fLaC": "FLAC",
    b"RIFF": "RIFF/WAV/AVI",
    b"OggS": "OGG",
    b"ID3": "MP3 ID3",
    b"BM": "BMP",
    b"\x00\x00\x00\x0Cftyp": "MP4/MOV",
}
for sig, name in sigs.items():
    pos = 0
    found = []
    while True:
        idx = data.find(sig, pos)
        if idx < 0: break
        found.append(idx)
        pos = idx + 1
        if len(found) > 5: break
    if found:
        print(f"  {name:20s} ({sig.hex()}): {len(found)} match(es) at {[hex(p) for p in found[:5]]}")

# Try to interpret first 64 bytes as a header
print("\n=== Possible header interpretations ===")
print(f"  Magic bytes: {' '.join(f'{b:02X}' for b in data[:8])}")
# 16-bit big endian
try:
    print(f"  16-bit BE at 0: 0x{struct.unpack('>H', data[:2])[0]:04X}")
    print(f"  16-bit LE at 0: 0x{struct.unpack('<H', data[:2])[0]:04X}")
    print(f"  32-bit BE at 0: 0x{struct.unpack('>I', data[:4])[0]:08X}")
    print(f"  32-bit LE at 0: 0x{struct.unpack('<I', data[:4])[0]:08X}")
    print(f"  16-bit BE at 0x40: 0x{struct.unpack('>H', data[0x40:0x42])[0]:04X}")
except Exception as e:
    print(f"  err: {e}")

# Byte frequency / distribution sanity check
print("\n=== Byte distribution (whole file) ===")
freq = collections.Counter(data)
print(f"  Unique byte values used: {len(freq)}/256")
min_b, max_b = min(freq.values()), max(freq.values())
print(f"  Min count: {min_b}  Max count: {max_b}")
print(f"  Ideal uniform count: {size/256:.1f}")
print(f"  Chi-square-ish: max/min ratio = {max_b/min_b:.2f}")
