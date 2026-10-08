import os, re, struct, collections

extracted_dir = r"D:\Code\Nikon\extracted"
with open(os.path.join(extracted_dir, "eg2090_0120a0.bi"), "rb") as f:
    main = f.read()

# 1. Find all eDSID_Setting_* (settings) and compare against the
#    known menu vs undocumented ones. Public camera settings are
#    things like ISO, WB, AF, Bracketing, PictureControl, etc.
print("=" * 70)
print("UNDOCUMENTED / SUSPICIOUS SETTINGS")
print("=" * 70)
settings = sorted(set(re.findall(rb'eDSID_Setting_[A-Za-z0-9_]+', main)))
settings = [s.decode() for s in settings]

# Group by topic
cats = collections.defaultdict(list)
for s in settings:
    parts = s.split('_')
    if len(parts) >= 3:
        cat = parts[2]
        if cat not in ('Movie', 'Still'):
            cats[cat].append(s)
        else:
            # subcategorize by what comes after
            if len(parts) >= 4:
                sub = parts[3]
                cats[f"{cat}_{sub}"].append(s)

# Look for categories with multiple entries (might be hidden)
print(f"Total: {len(settings)} unique settings")
print("\nCategories with most settings (top 30):")
for cat, lst in sorted(cats.items(), key=lambda x: -len(x[1]))[:30]:
    print(f"  {cat:50s} {len(lst):3d} settings")
    # Show first 3 examples
    for s in lst[:3]:
        print(f"    {s}")

# Look for the unusual ones
print("\nSettings with unusual names (might be hidden):")
unusual_keywords = ['Lvf', 'lens', 'Lens', 'Disp', 'Aging', 'BurnIn', 'Burn',
                    'Engineer', 'Adjust', 'Factory', 'Calib', 'Tuning', 'Prohibit',
                    'Demo', 'Sample', 'Test', 'Internal', 'Qa', 'qa', 'QA', 'Mmt',
                    'Cmd', 'Cmd_', 'CmdDial', 'SubCmd', 'Rec', 'Playback',
                    'Setting_', 'Firmware', 'Firm', 'Usb', 'HDMI', 'CEC',
                    'SnapBridge', 'BT', 'Wifi', 'WIFI', 'Auth', 'Log',
                    'MovieQuality', 'MovieQualityFine', 'MovieQualityNormal',
                    'MovieQualityHigh', 'MovieQualityLow', 'MovieQualityStar',
                    'Vr', 'VR', 'EVR', 'Icr', 'ICR', 'Hdr', 'HDR',
                    'Reset', 'Reset_', 'Default']
seen = set()
for kw in unusual_keywords:
    for s in settings:
        if kw in s and s not in seen:
            seen.add(s)
            print(f"  {s}")
            if len(seen) > 60: break
    if len(seen) > 60: break

# 2. Find all eDSID_Command* (commands) — these are PTP operations
print("\n" + "=" * 70)
print("eDSID_Command* (PTP operation codes)")
print("=" * 70)
cmds = sorted(set(re.findall(rb'eDSID_(Command|CommandProp|CommandID|Op|Opcode|Operation)[A-Za-z0-9_]*', main)))
for c in cmds[:60]:
    print(f"  {c.decode()}")

# 3. Sysc commands
print("\n" + "=" * 70)
print("eSysc* (system controller commands)")
print("=" * 70)
syscs = sorted(set(re.findall(rb'eSysc[A-Za-z0-9_]+', main)))
for s in syscs[:60]:
    print(f"  {s.decode()}")

# 4. Look for WiFi / network "hidden" stuff
print("\n" + "=" * 70)
print("WiFi/BT hidden service / SSID patterns")
print("=" * 70)
for p in [b'Nikon', b'Z_30', b'Z 30', b'NIKON', b'COOLPIX', b'COOLSCAN', b'nikon']:
    for off in range(len(main) - 30):
        idx = main.find(p, off)
        if idx < 0: break
        # Look for ASCII context
        s = max(0, idx-20)
        e = min(len(main), idx + 60)
        run = b""
        for b in main[s:e]:
            if 0x20 <= b < 0x7F:
                run += bytes([b])
            else:
                if len(run) >= 4 and p in run:
                    print(f"  0x{idx:08X}: {run.decode('latin-1', 'replace').encode('ascii','replace').decode('ascii')[:80]}")
                run = b""
        off = idx + 1
        if off >= len(main): break

# 5. Find reference to firmware signing / verification
print("\n" + "=" * 70)
print("FIRMWARE SIGNING / VERIFICATION")
print("=" * 70)
for p in [b'signature', b'Signature', b'SIGNATURE', b'verify', b'Verify', b'VERIFY',
          b'checksum', b'Checksum', b'CRC', b'crc32', b'SHA', b'sha256',
          b'rsa', b'RSA', b'public key', b'PubKey', b'privKey', b'sign',
          b'authenticity', b'authent', b'Authent']:
    for off, ctx in [(m.start(), main[max(0,m.start()-30):m.start()+100]) for m in re.finditer(p, main)][:3]:
        s = max(0, off - 30)
        e = min(len(main), off + 100)
        runs = []
        cur = b""
        start = 0
        for i in range(s, e):
            b = main[i]
            if 0x20 <= b < 0x7F:
                if not cur: start = i
                cur += bytes([b])
            else:
                if len(cur) >= 4: runs.append((start, cur))
                cur = b""
        if len(cur) >= 4: runs.append((start, cur))
        for o, r in runs:
            if p in r or p.lower() in r.lower():
                txt = r.decode('latin-1', 'replace').encode('ascii', 'replace').decode('ascii')
                if len(txt) > 4:
                    print(f"  0x{off:08X}: {txt[:120]}")
                break

# 6. Look for "secret menu" / service manual references
print("\n" + "=" * 70)
print("SERVICE MANUAL / SECRET MENU references")
print("=" * 70)
for p in [b'service manual', b'ServiceManual', b'secret menu', b'SecretMenu',
          b'HiddenMenu', b'FACTORY_MENU', b'FactoryMenu', b'ENG_MENU',
          b'EngMode', b'EngineerMenu', b'FMenu', b'servicemenu',
          b'calibration', b'CalibrationMode', b'AdjData', b'Adjustment',
          b'WriteAdjust', b'CalWrite', b'CalRead', b'CalData',
          b'shop', b'repair', b'InspectionMode',
          b'NikonAuthorized', b'authorized']:
    for m in re.finditer(p, main):
        s = max(0, m.start() - 30)
        e = min(len(main), m.start() + 100)
        runs = []
        cur = b""
        start = 0
        for i in range(s, e):
            b = main[i]
            if 0x20 <= b < 0x7F:
                if not cur: start = i
                cur += bytes([b])
            else:
                if len(cur) >= 4: runs.append((start, cur))
                cur = b""
        if len(cur) >= 4: runs.append((start, cur))
        for o, r in runs:
            if p in r or p.lower() in r.lower():
                txt = r.decode('latin-1', 'replace').encode('ascii', 'replace').decode('ascii')
                if len(txt) > 4:
                    print(f"  0x{m.start():08X}: {txt[:120]}")
                break

# 7. Look for Self-diagnostic / aging / burn-in
print("\n" + "=" * 70)
print("AGING / BURN-IN / SELF-DIAGNOSTIC")
print("=" * 70)
for p in [b'AgingTest', b'BURNIN', b'BurnIn', b'burnin', b'BURN_IN',
          b'BURN-IN', b'self-diag', b'SelfDiag', b'SelfCheck',
          b'Diagnostic', b'FactoryTest', b'productionTest',
          b'run-in', b'long-run', b'Stress', b'stress']:
    for m in re.finditer(p, main, re.IGNORECASE):
        s = max(0, m.start() - 30)
        e = min(len(main), m.start() + 100)
        runs = []
        cur = b""
        start = 0
        for i in range(s, e):
            b = main[i]
            if 0x20 <= b < 0x7F:
                if not cur: start = i
                cur += bytes([b])
            else:
                if len(cur) >= 4: runs.append((start, cur))
                cur = b""
        if len(cur) >= 4: runs.append((start, cur))
        for o, r in runs:
            if p.lower() in r.lower():
                txt = r.decode('latin-1', 'replace').encode('ascii', 'replace').decode('ascii')
                if len(txt) > 4:
                    print(f"  0x{m.start():08X}: {txt[:120]}")
                break

# 8. Check the bootloader specifically for any test commands
print("\n" + "=" * 70)
print("BOOTLOADER: Test / Production commands")
print("=" * 70)
with open(os.path.join(extracted_dir, "ex2090_012000.bi"), "rb") as f:
    boot = f.read()
# Look at all function-like strings (mixed case, ends with 'F' or 'E' or 'Cmd')
all_strs = re.findall(rb'[A-Z][A-Za-z]{2,20}', boot)
counter = collections.Counter([s.decode() for s in all_strs if len(s) >= 4 and len(s) <= 24])
# Get the most common identifiers
print("Most common identifiers in bootloader:")
for ident, cnt in counter.most_common(50):
    if cnt >= 2 and not ident.isupper() and 'Svc' in ident or 'Cmd' in ident or 'Test' in ident or 'Mng' in ident or 'Ctl' in ident or 'Init' in ident:
        print(f"  {ident}: {cnt}")
