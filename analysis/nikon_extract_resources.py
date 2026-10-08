import os, struct, hashlib, re

extracted_dir = r"D:\Code\Nikon\extracted"
out_dir = r"D:\Code\Nikon\resources"
os.makedirs(out_dir, exist_ok=True)

# Load main firmware
with open(os.path.join(extracted_dir, "eg2090_0120a0.bi"), "rb") as f:
    fw = f.read()

# Extract PNG files (well-defined format)
def extract_png(data, start, base_name, idx):
    # PNG ends with IEND chunk: 00 00 00 00 49 45 4E 44 AE 42 60 82
    end = data.find(b"IEND", start)
    if end < 0: return None
    end += 8  # IEND + CRC
    # Some tools say PNG should end at end+CRC
    end += 4
    return data[start:end]

# Extract JPEG
def extract_jpeg(data, start):
    # JPEG ends with FFD9
    end = data.find(b"\xFF\xD9", start)
    if end < 0: return None
    return data[start:end+2]

# Extract RIFF/WAV
def extract_riff(data, start):
    # RIFF has size at offset +4 (LE 32)
    if start + 8 > len(data): return None
    size = struct.unpack("<I", data[start+4:start+8])[0]
    end = start + 8 + size
    if end > len(data): end = len(data)
    return data[start:end]

# Extract MP3
def extract_mp3(data, start):
    # MP3 frame: 0xFFE? 0xFFFB? - look for next frame
    # For simplicity, try to find sync loss
    # Actually, scan for next valid frame header
    return data[start:start+200000]  # heuristic

# Extract ZIP
def extract_zip(data, start):
    end = data.find(b"PK\x05\x06", start)
    if end < 0: return None
    # Find the EOCD (end of central directory) which has its own size
    eocd_off = end
    if eocd_off + 22 > len(data): return None
    eocd_size = 22
    comment_len = struct.unpack("<H", data[eocd_off+20:eocd_off+22])[0]
    eocd_size += comment_len
    return data[start:eocd_off+eocd_size]

# Extract GZIP
def extract_gzip(data, start):
    # GZIP ends with 8-byte trailer; find a later one
    end = data.find(b"\x1F\x8B\x08", start+2)
    if end < 0:
        return data[start:]
    return data[start:end]

# Extract BZIP2
def extract_bz2(data, start):
    end = data.find(b"BZh", start+10)
    if end < 0: return data[start:start+2000000]
    return data[start:end]

# Extract XMP packet (XML)
def extract_xmp(data, start):
    end = data.find(b"<?xpacket end", start)
    if end < 0: return None
    # Find the end of <?xpacket end="..."?>
    end_close = data.find(b"?>", end)
    if end_close < 0: return None
    return data[start:end_close+2]

# Find and extract all
seen_hashes = set()
extracted = []

def save(name, blob, kind):
    if not blob or len(blob) < 8: return
    h = hashlib.sha1(blob).hexdigest()[:8]
    if h in seen_hashes: return
    seen_hashes.add(h)
    path = os.path.join(out_dir, f"{name}_{h}.{kind}")
    with open(path, "wb") as f:
        f.write(blob)
    extracted.append((path, len(blob), kind))
    print(f"  + {os.path.basename(path)}: {len(blob):,} bytes")
    return path

print("=== Extracting PNG images ===")
for i, idx in enumerate(re.finditer(rb'\x89PNG\r\n\x1a\n', fw)):
    blob = extract_png(fw, idx.start(), "png", i)
    save(f"png_{i:04d}", blob, "png")

print("\n=== Extracting JPEG images ===")
for i, idx in enumerate(re.finditer(rb'\xFF\xD8\xFF', fw)):
    blob = extract_jpeg(fw, idx.start())
    if blob and len(blob) > 1000:  # skip false positives
        save(f"jpg_{i:04d}", blob, "jpg")

print("\n=== Extracting RIFF/WAV ===")
for i, idx in enumerate(re.finditer(rb'RIFF', fw)):
    blob = extract_riff(fw, idx.start())
    if blob and len(blob) > 1000 and b'WAVE' in blob[:20]:
        save(f"wav_{i:04d}", blob, "wav")

print("\n=== Extracting BZIP2 ===")
for i, idx in enumerate(re.finditer(rb'BZh', fw)):
    blob = extract_bz2(fw, idx.start())
    if blob and len(blob) > 1000:
        save(f"bz2_{i:04d}", blob, "bz2")

print("\n=== Extracting XMP metadata ===")
for i, idx in enumerate(re.finditer(rb'<\?xpacket begin', fw)):
    blob = extract_xmp(fw, idx.start())
    if blob and len(blob) > 100:
        save(f"xmp_{i:04d}", blob, "xml")

print(f"\n=== Summary: extracted {len(extracted)} resources ===")
for p, size, kind in extracted[:50]:
    print(f"  {os.path.basename(p):40s} {size:>12,} bytes  {kind}")
