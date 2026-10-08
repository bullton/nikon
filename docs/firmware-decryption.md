# Firmware decryption — how it works

Reverse-engineering notes for the Nikon Z30 firmware container
format. The scripts in `analysis/` implement all of this.

## Container format

The Z30 firmware file `Z_30_0120.bin` is **not** a flat binary.
It's a tiny container with one 16-byte header, three 32-byte
entries, and the actual sub-firmwares as opaque blobs.

```
offset  size  field
0x00    16    preamble (random-looking, ignore)
0x10     4    count (big-endian uint32) — number of sub-firmwares
0x14     4    headerlen (big-endian uint32)
0x18     4    padding
0x1C     4    padding

# Repeats `count` times:
        16    name (no NUL, fixed-width)
         4    start offset (big-endian uint32)
         4    length (big-endian uint32)
         8    padding

# Then:  sub-firmware #1 at `start`, length `len`
#        sub-firmware #2 at `start`, length `len`
#        sub-firmware #3 at `start`, length `len`
```

For `Z_30_0120.bin`:

```
ex2090_012000.bi    start=0x00000090   length=0x00080002   (524,290 B)
_tpj04_v10a5.bin    start=0x00080092   length=0x00008022   ( 32,802 B)
eg2090_0120a0.bi    start=0x000880B4   length=0x0205E03A   (32,939,514 B)
```

## XOR obfuscation

The entire file (from offset 0x20 onwards) is XOR-obfuscated with
a 3-byte rolling key. The key is built from three 256-byte
substitution tables indexed by the **byte position modulo**:

```
data[i+offset] ^ Xor_Ord1[i & 0xFF]
            ^ Xor_Ord2[(i >> 8) & 0xFF]
            ^ Xor_Ord3[(i >> 16) & 0xFF]
```

Where `offset = 0x20` for Z-series bodies (D-series uses 0).

The 3 tables are at [`analysis/nikon_decode.py`](../analysis/nikon_decode.py)
(top of the file) and were extracted from
[`simeonpilgrim/nikon-firmware-tools`](https://github.com/simeonpilgrim/nikon-firmware-tools)'s
`firmware decode/Firmware.cs` (which is itself the result of years
of community RE on Nikon firmware).

## Why this is not AES

The output has near-perfect 8.0 bits/byte entropy (uniform random)
which initially looks like AES encryption. But:

1. **No key file / signature**: the camera runs the same XOR
   regardless of who starts it. AES would require per-device key
   material.
2. **Output is byte-aligned pseudo-random, not block-ciphered**:
   you can decode any single byte without needing surrounding
   context (unlike AES-CBC where one wrong byte shifts the rest).
3. **XOR with deterministic keystream + high-entropy source** is
   trivially indistinguishable from random to entropy tests, but is
   *not* cryptographically secure.

So Nikon's "encryption" is **obfuscation**, not encryption. Its
purpose is to make casual reverse engineering harder, not to
prevent it.

## Decoding — step by step

```python
# From analysis/nikon_decode.py
def decode(data, offset=0x20):
    out = bytearray(data)
    end = len(data) - offset
    for i in range(end):
        b = data[i+offset] ^ Xor_Ord1[i & 0xFF] \
                        ^ Xor_Ord2[(i >> 8) & 0xFF] \
                        ^ (Xor_ORD3[(i >> 16) & 0xFF] & 0xFF)
        out[i] = b
    return bytes(out)
```

Decoding a 32.9 MB file takes ~10 seconds in pure Python. The inner
loop is the bottleneck; PyPy would cut that to ~2s.

## Why we don't decode the inner sub-firmwares

`eg2090_0120a0.bi` (the main application firmware) is **itself**
encrypted / compressed with a different scheme. The Z30's
`Updater` app on the camera does:

1. Decrypt outer container (XOR, this script handles it)
2. For each sub-firmware, decompress / further-decrypt using
   another (proprietary) algorithm

The inner algorithm is **not** publicly documented. The
`simeonpilgrim/nikon-firmware-tools` repo also stops here — its
output is the same 3 sub-firmware files we're at.

To go further, you'd need to either:
- Run the update inside a Nikon camera and dump RAM after decryption
  (using a JTAG on the sub-CPU or hot-attaching to a running camera)
- Reverse the `Updater` app (which is an ARM binary inside the
  `eg2090_0120a0.bi` blob — that's the whole reason for the
  embedded ARM code we found in our string scan)

For our purposes (finding hidden features, building tools), the
string-level analysis is plenty. We can read all the menu IDs,
version strings, copyright notices, error messages, and embedded
PNG/JPEG/WAV resources without further decryption.

## Verifying your work

After decoding, the first 16 bytes of the original file become
**garbage** (they were never obfuscated; they were just placeholder
filler). The first meaningful header appears at offset 0x20:

```
0x20: 00 00 00 03   count = 3 sub-firmwares
0x24: 00 00 00 90   headerlen = 0x90 (the total header is 144 bytes)
0x30: 65 78 32 30 39 30 5F 30 31 32 30 30 30 2E 62 69   "ex2090_012000.bi"
```

If your first 4 bytes at offset 0x20 aren't `00 00 00 03`, the
offset is wrong (try 0x0 instead — older models use that).

## What I tried that DIDN'T work

| Approach | Why it failed |
|---|---|
| AES-256 with key from camera | No key material in firmware; not AES |
| XOR with single-byte key (0xFF etc.) | Output still high-entropy; wrong key |
| LZMA decompression | Magic header not found |
| Plain bytes as JPEG / PNG | No headers |
| `binwalk` magic-byte scan | Only matched false positives in the encrypted bytes |
| gphoto2 / libgphoto2 | Doesn't know about the container format |

## What DID work

| Approach | Why it worked |
|---|---|
| Diff against known Z6 / Z7 decrypted firmware | Same XOR tables across Z series (community-verified) |
| Look at the format used by `simeonpilgrim/nikon-firmware-tools` | That repo's authors already did the hard work for Z7 |
| Spot-check by reading the output | The 3 sub-firmware names appear as plain ASCII once decoded, which is unambiguous |
| Look for `Ver.X.YY.aa` strings | The version tag appears at the start of the main firmware, easy to verify |
