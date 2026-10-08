#!/usr/bin/env bash
# CamRelay installer. Tested on Raspberry Pi OS Lite (Debian 12).
# Run as root: sudo bash install.sh
set -euo pipefail

INSTALL_USER="${INSTALL_USER:-camrelay}"
INSTALL_DIR="/opt/camrelay"
CONFIG_DIR="/etc/camrelay"
STATE_DIR="/var/lib/camrelay"

echo "==> CamRelay installer"
echo "    user:       $INSTALL_USER"
echo "    install:    $INSTALL_DIR"
echo "    config:     $CONFIG_DIR"
echo "    state:      $STATE_DIR"

if [ "$(id -u)" -ne 0 ]; then
  echo "ERROR: must be root" >&2; exit 1
fi

# 1. System packages
echo "==> apt: gphoto2 rclone python3-pip cifs-utils"
apt-get update
apt-get install -y --no-install-recommends \
  gphoto2 rclone python3 python3-pip python3-yaml python3-flask \
  cifs-utils ca-certificates

# 2. User
if ! id "$INSTALL_USER" &>/dev/null; then
  useradd --system --shell /usr/sbin/nologin --home "$STATE_DIR" "$INSTALL_USER"
fi

# 3. Copy code
echo "==> copy code to $INSTALL_DIR"
mkdir -p "$INSTALL_DIR"
cp -r . "$INSTALL_DIR/"
chown -R "$INSTALL_USER:$INSTALL_USER" "$INSTALL_DIR"

# 4. pip dependencies (only if system packages don't have them)
echo "==> pip install"
python3 -m pip install --break-system-packages --quiet -r "$INSTALL_DIR/requirements.txt" 2>/dev/null || \
python3 -m pip install --quiet -r "$INSTALL_DIR/requirements.txt"

# 5. Config
echo "==> config: $CONFIG_DIR"
mkdir -p "$CONFIG_DIR"
if [ ! -f "$CONFIG_DIR/config.yaml" ]; then
  cp "$INSTALL_DIR/config/config.example.yaml" "$CONFIG_DIR/config.yaml"
  echo "    Edit $CONFIG_DIR/config.yaml before starting the service."
fi

# 6. State dir
echo "==> state: $STATE_DIR"
mkdir -p "$STATE_DIR/staging"
chown -R "$INSTALL_USER:$INSTALL_USER" "$STATE_DIR"

# 7. udev rule so non-root can use USB cameras (optional)
cat > /etc/udev/rules.d/99-camrelay.rules <<'EOF'
# Allow camrelay user to access USB PTP cameras without root.
SUBSYSTEM=="usb", ATTR{idVendor}=="04b0", MODE="0660", GROUP="camrelay"
EOF
udevadm control --reload 2>/dev/null || true

# 8. systemd
echo "==> systemd"
cp "$INSTALL_DIR/scripts/camrelay.service" /etc/systemd/system/camrelay.service
systemctl daemon-reload
systemctl enable camrelay.service
echo "    sudo systemctl start camrelay"
echo "    sudo journalctl -u camrelay -f"

echo
echo "Done. Next steps:"
echo "  1. Edit /etc/camrelay/config.yaml"
echo "  2. rclone config      (if using rclone_remote)"
echo "  3. sudo systemctl start camrelay"
echo "  4. http://<pi-ip>:8080  to see status"
