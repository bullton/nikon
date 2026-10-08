#!/usr/bin/env bash
# Connect the box to the camera's WiFi AP.
# Usage:  sudo bash connect-camera.sh "NIKON_Z30_ABCDEF" "MyPassword"
#   or:   sudo bash connect-camera.sh            # interactive prompt

set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "ERROR: must be root" >&2; exit 1
fi

SSID="${1:-}"
PSK="${2:-}"

if [ -z "$SSID" ]; then
  read -r -p "Camera SSID: " SSID
fi
if [ -z "$PSK" ]; then
  read -r -s -p "Camera password: " PSK; echo
fi

if [ -z "$SSID" ] || [ -z "$PSK" ]; then
  echo "ERROR: SSID and password required" >&2; exit 1
fi

WPA="/etc/wpa_supplicant/wpa_supplicant.conf"

# Back up
cp "$WPA" "${WPA}.bak-$(date +%Y%m%d-%H%M%S)" 2>/dev/null || true

# Add a network block for the camera at the end of the file.
# We use a special id_str so we can identify and replace it.
cat >> "$WPA" <<EOF

network={
    ssid="$SSID"
    psk="$PSK"
    priority=10
    id_str="camrelay-camera"
}
EOF

wpa_cli -i wlan0 reconfigure
echo "==> Reconfigured. Verifying connection..."
sleep 5
iwgetid -r 2>/dev/null || iwconfig wlan0 | grep ESSID || true
ip -4 addr show wlan0 | grep -oP 'inet \K[\d.]+' | head -1

# Try PTP discovery
echo "==> Trying PTP/IP on common addresses..."
for ip in 192.168.1.1 192.168.0.1; do
  if timeout 2 bash -c "echo > /dev/tcp/$ip/15740" 2>/dev/null; then
    echo "    Camera at $ip:15740 (PTP/IP) — accessible!"
  fi
done
echo "Now you can set the camera IP in /etc/camrelay/config.yaml."
