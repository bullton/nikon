import os, re, struct, collections, hashlib

extracted_dir = r"D:\Code\Nikon\extracted"
out = r"D:\Code\Nikon\hidden"
os.makedirs(out, exist_ok=True)

with open(os.path.join(extracted_dir, "eg2090_0120a0.bi"), "rb") as f:
    main = f.read()
with open(os.path.join(extracted_dir, "ex2090_012000.bi"), "rb") as f:
    boot = f.read()
with open(os.path.join(extracted_dir, "_tpj04_v10a5.bin"), "rb") as f:
    tpj = f.read()

def find_all(data, pat, ctx=0, min_len=4):
    """Find all byte pattern matches with context"""
    if isinstance(pat, str): pat = pat.encode()
    out = []
    i = 0
    while True:
        idx = data.find(pat, i)
        if idx < 0: break
        s = max(0, idx - ctx)
        e = min(len(data), idx + len(pat) + ctx)
        out.append((idx, data[s:e]))
        i = idx + 1
    return out

def find_strings(data, min_len=4):
    runs = []
    cur = b""
    start = 0
    for i, b in enumerate(data):
        if 0x20 <= b < 0x7F or b in (0x09, 0x0A, 0x0D):
            if not cur: start = i
            cur += bytes([b])
        else:
            if len(cur) >= min_len: runs.append((start, cur))
            cur = b""
    if len(cur) >= min_len: runs.append((start, cur))
    return runs

def print_section(title):
    print("\n" + "=" * 70)
    print(title)
    print("=" * 70)

# ============================================================
# 1. DEBUG/ENGINEER COMMANDS
# ============================================================
print_section("1. DEBUG / ENGINEER COMMANDS & TRACES")
patterns = [
    b"debug", b"DEBUG", b"Debug",
    b"engineer", b"factory", b"Factory", b"FACTORY",
    b"service mode", b"ServiceMode", b"ServiceMode",
    b"hidden", b"secret", b"private",
    b"reserved", b"RESERVED", b"prohibit",
    b"developer", b"diagnostic", b"Diagnostic",
    b"production", b"prototype", b"PROTO",
    b"NIKON_LAB", b"nikon_lab", b"LAB",
    b"FP1", b"FP2",  # Nikon "Field Production"?
    b"TestMode", b"test mode", b"TEST_MODE",
    b"maintenance", b"serviceman",
]
for p in patterns:
    for off, ctx in find_all(main, p, ctx=60):
        s = max(0, off - 50)
        e = min(len(main), off + 100)
        # Find printable run
        runs = find_strings(main[s:e], 4)
        for o, r in runs:
            if p in r or p.lower() in r.lower():
                print(f"  0x{off:08X} {r.decode('latin-1', 'replace').encode('ascii','replace').decode('ascii')[:120]}")
                break
        else:
            # No printable run, just show bytes
            txt = main[s:e].decode('latin-1', 'replace').encode('ascii','replace').decode('ascii').encode('ascii', 'replace').decode('ascii')
            print(f"  0x{off:08X} (raw) {txt[:120]}")

# ============================================================
# 2. PTP / MTP HIDDEN COMMANDS
# ============================================================
print_section("2. PTP / MTP COMMANDS (camera-to-PC protocol)")
# PTP operation codes are 16-bit. Known codes include:
# 0x9001 GetDeviceInfo, 0x1001 GetDevicePropDesc, etc.
# Nikon proprietary: 0x90XX, 0x91XX, 0x92XX, 0x9400-0x9500 etc.
ptp_codes = [
    b"PTP", b"ptp_", b"MTP", b"MTP_", b"PtpUsb", b"PtpMovie",
    b"VendorExt", b"VendorExtension", b"NikonExt",
    b"GetDeviceInfo", b"GetObjectInfo", b"GetObject",
    b"SendObjectInfo", b"SendObject",
    b"GetStorageIDs", b"GetStorageInfo",
    b"GetDevicePropDesc", b"GetDevicePropValue",
    b"SetDevicePropValue", b"GetObjectHandles",
    b"OpenSession", b"CloseSession",
    b"Initialize", b"Reset", b"Terminate",
]
for p in ptp_codes:
    matches = find_all(main, p, ctx=30)
    if matches:
        print(f"  Pattern '{p.decode()}': {len(matches)} matches")
        for off, ctx in matches[:5]:
            print(f"    0x{off:08X}: {ctx.decode('latin-1', 'replace').encode('ascii','replace').decode('ascii')[:100]}")

# Look for hex PTP operation codes
# Nikon typically uses 0x90xx, 0x91xx for vendor ops
ptp_opcode_strings = re.findall(rb'PTP_OP_[A-Z_]+|PtpOp[A-Za-z]+', main)
ptp_opcode_set = set(ptp_opcode_strings)
if ptp_opcode_strings:
    print(f"\n  Found {len(ptp_opcode_set)} unique PTP opcode references")
    for s in sorted(ptp_opcode_set)[:40]:
        idx = main.find(s)
        print(f"    0x{idx:08X}: {s.decode('latin-1').encode('ascii','replace').decode('ascii')}")

# ============================================================
# 3. ENGINEERING MENU ENTRIES
# ============================================================
print_section("3. ENGINEERING MENU / DEBUG SETTINGS")
# eDSID_Setting_* are the standard ones. Look for variations.
eng_settings = re.findall(rb'eDSID_Setting_[A-Za-z0-9_]+', main)
unique_settings = sorted(set(s.decode().encode('ascii','replace').decode('ascii') for s in eng_settings))
print(f"  Total unique eDSID_Setting_*: {len(unique_settings)}")
# Group by category (first part after 'Setting_')
cats = collections.Counter()
for s in unique_settings:
    parts = s.split('_')
    if len(parts) >= 3:
        cat = parts[2]
        cats[cat] += 1
print(f"  Top 30 categories:")
for cat, cnt in cats.most_common(30):
    print(f"    {cat:30s} {cnt:4d}")

# Look for unusual / non-standard settings
print(f"\n  Settings starting with unusual prefixes:")
for s in unique_settings:
    if any(x in s for x in ('Debug', 'Engineer', 'Factory', 'Service', 'Test', 'Hidden', 'Reserved', 'Prohibit', 'Internal', 'Developer', 'Calibrat', 'Adjust', 'Lab', 'Tuning', 'Proto')):
        print(f"    {s}")

# ============================================================
# 4. STRINGS WITH "PROHIBIT" / "RESERVED"
# ============================================================
print_section("4. PROHIBITED / RESERVED / DISABLED MENTIONS")
for pat in [b'Prohibit', b'Reserved', b'Disabled', b'Enable', b'Disable',
            b'NOT_SUPPORTED', b'SUPPORTED', b'Unsupported',
            b'NOT_AVAILABLE', b'hidden', b'restricted', b'forbidden']:
    for off, ctx in find_all(main, pat, ctx=50):
        s = max(0, off - 50)
        e = min(len(main), off + 100)
        runs = find_strings(main[s:e], 4)
        for o, r in runs:
            if pat in r or pat.lower() in r.lower():
                txt = r.decode('latin-1', 'replace').encode('ascii','replace').decode('ascii').encode('ascii','replace').decode('ascii')
                if len(txt) > 4:
                    print(f"  0x{off:08X}: {txt[:120]}")
                break
                break

# ============================================================
# 5. PTP VENDOR COMMANDS BY NIKON (HEX CODES)
# ============================================================
print_section("5. NIKON PTP VENDOR CODES (looking for 0x90xx, 0x91xx, 0x92xx constants)")
# Find all 16-bit values that look like Nikon vendor codes
# In big-endian (network byte order) or little-endian
# Look for context with 'Get' / 'Set' / 'Vendor'
import re as _re
hits = []
for m in _re.finditer(rb'(Get|Set|Send|Read|Write|Start|Stop|Reset|Enable|Disable)[A-Z][a-zA-Z]+Vendor', main):
    s = max(0, m.start() - 30)
    e = min(len(main), m.end() + 60)
    ctx = main[s:e].decode('latin-1', 'replace').encode('ascii','replace').decode('ascii')
    hits.append((m.start(), ctx))
print(f"  Found {len(hits)} vendor operation references")
for off, ctx in hits[:30]:
    print(f"  0x{off:08X}: {ctx[:140]}")

# ============================================================
# 6. PASSWORD / KEY STRINGS
# ============================================================
print_section("6. PASSWORD / KEY / AUTH STRINGS")
for pat in [b'Password', b'password', b'Passwd', b'passwd', b'PIN', b'pin',
            b'key', b'Key', b'Secret', b'Auth', b'auth', b'Login', b'Credential',
            b'Token', b'Session', b'OAuth', b'SSL', b'TLS',
            b'crypt', b'cipher', b'AES', b'DES', b'RSA', b'SHA',
            b'salt', b'hash', b'HMAC', b'KDF',
            b'backdoor', b'magic', b'Magic', b'cheat', b'unlock']:
    matches = find_all(main, pat, ctx=40)
    if matches:
        for off, ctx in matches[:3]:
            s = max(0, off - 30)
            e = min(len(main), off + 100)
            runs = find_strings(main[s:e], 4)
            for o, r in runs:
                if pat in r or pat.lower() in r.lower():
                    txt = r.decode('latin-1', 'replace').encode('ascii','replace').decode('ascii').encode('ascii','replace').decode('ascii')
                    if len(txt) > 4:
                        print(f"  0x{off:08X}: {txt[:120]}")
                    break
                else:
                    break

# ============================================================
# 7. URL / DOMAIN / C2 STRINGS
# ============================================================
print_section("7. URLs / DOMAINS / NETWORK ENDPOINTS")
urls = set()
for m in re.finditer(rb'https?://[A-Za-z0-9._/-]+', main):
    urls.add(m.group().decode('latin-1', 'replace').encode('ascii','replace').decode('ascii'))
for m in re.finditer(rb'[a-zA-Z0-9_-]+\.(com|net|org|io|jp|cn|us|de|uk|fr|ru|co|in|br)\b', main):
    urls.add(m.group().decode('latin-1', 'replace').encode('ascii','replace').decode('ascii'))
for u in sorted(urls)[:50]:
    print(f"  {u}")

# ============================================================
# 8. COMMAND TABLE ENTRIES (likely menu system)
# ============================================================
print_section("8. PTP / DEBUG COMMAND TABLE PATTERNS")
# Look for command IDs and their string labels
# Common: NUM_ID, CMD_NAME format
# Or: 0xCODE Name
cmd_pat = re.findall(rb'0x[0-9A-Fa-f]{4,8}\s+[A-Z][A-Za-z0-9_]{4,}', main)
print(f"  0xXXXX-name style: {len(cmd_pat)} matches")
for c in cmd_pat[:30]:
    idx = main.find(c)
    print(f"    0x{idx:08X}: {c.decode('latin-1', 'replace')[:100]}")

# ============================================================
# 9. EGG / JOKES / TROLL STRINGS
# ============================================================
print_section("9. EASTER EGGS / TROLL STRINGS")
for pat in [b'Lorem', b'ipsum', b'Hack', b'HACK', b'Hello', b'Hello World',
            b'TODO', b'FIXME', b'XXX', b'\\\\x', b'//debug', b'//hack',
            b'@author', b'@version', b'@license',
            b'developed by', b'created by', b'written by',
            b'TEAM', b'Kamran', b'Cheers', b'Thank you',
            b'magic value', b'no comment', b'hack',
            b'cheat', b'Easter', b'easter egg', b'Konami',
            b'magic_string', b'MAGIC_STRING', b'_MAGIC_',
            b'OpenSesame', b'ABRACADABRA', b'xyzzy', b'plugh']:
    for off, ctx in find_all(main, pat, ctx=50):
        s = max(0, off - 30)
        e = min(len(main), off + 100)
        runs = find_strings(main[s:e], 4)
        for o, r in runs:
            if pat in r or pat.lower() in r.lower():
                txt = r.decode('latin-1', 'replace').encode('ascii','replace').decode('ascii').encode('ascii','replace').decode('ascii')
                if len(txt) > 4:
                    print(f"  0x{off:08X}: {txt[:120]}")
                break
            else:
                break

# ============================================================
# 10. BOOTLOADER-SPECIFIC HIDDEN COMMANDS
# ============================================================
print_section("10. BOOTLOADER (ex2090_012000.bi) - Service / Test entries")
all_boot_strs = find_strings(boot, 5)
test_like = []
for off, s in all_boot_strs:
    txt = s.decode('latin-1', 'replace').encode('ascii','replace').decode('ascii')
    if any(p in txt for p in ('Bat', 'Pow', 'Swt', 'Mng', 'Srv', 'Ctl', 'Cm_', 'Mtx', 'Sem', 'Flag', 'Evt', 'Dbg', 'Test', 'Adj', 'Cal', 'Eng', 'Fac', 'Tst')):
        if len(txt) >= 4 and not re.match(r'^[!-/]+$', txt) and not re.match(r'^[0-9 .-]+$', txt):
            test_like.append((off, txt))
test_like.sort()
print(f"  {len(test_like)} test/service-like strings found")
for off, t in test_like[:80]:
    print(f"  0x{off:05X}: {t[:80]}")
