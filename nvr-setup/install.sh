#!/usr/bin/env bash
# ============================================================
# Mini NVR — Installer
#
# Usage: sudo ./install.sh
# The recordings disk must already be mounted (see Step 36 of reports/pi_services_guide.md).
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF_DIR="/etc/nvr"
CONF_FILE="$CONF_DIR/nvr.conf"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

log()  { echo -e "${GREEN}[NVR]${NC} $*"; }
warn() { echo -e "${YELLOW}[NVR]${NC} $*"; }
die()  { echo "[NVR] $*" >&2; exit 1; }

# --- Root check ------------------------------------------------------
[[ $EUID -eq 0 ]] || die "This script must run as root (sudo)."

echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║     📹  Mini NVR — Install                   ║${NC}"
echo -e "${BOLD}╚══════════════════════════════════════════════╝${NC}"
echo ""

# --- Dependencies ----------------------------------------------------
log "Checking dependencies..."

if ! command -v ffmpeg &>/dev/null; then
    log "Installing ffmpeg..."
    apt-get update -qq
    apt-get install -y -qq ffmpeg
fi

command -v ffprobe &>/dev/null || warn "ffprobe not found (it should come with ffmpeg)"

log "ffmpeg: $(ffmpeg -version | head -1)"

# --- Users and groups ------------------------------------------------
# nvr writes the recordings, smb_viewer members read them through Samba.
if ! id -u nvr &>/dev/null; then
    log "Creating system user 'nvr'..."
    useradd --system --no-create-home --shell /usr/sbin/nologin nvr
fi

if ! getent group smb_viewer &>/dev/null; then
    log "Creating group 'smb_viewer'..."
    groupadd smb_viewer
fi

# --- Configuration ---------------------------------------------------
# It holds the camera credentials: readable by root and nvr only.
mkdir -p "$CONF_DIR"
if [[ ! -f "$CONF_FILE" ]]; then
    log "Installing the default configuration..."
    install -m 640 -o root -g nvr "$SCRIPT_DIR/nvr.conf" "$CONF_FILE"
else
    warn "Configuration already exists, keeping: $CONF_FILE"
    warn "New version saved as: $CONF_FILE.new"
    install -m 640 -o root -g nvr "$SCRIPT_DIR/nvr.conf" "$CONF_FILE.new"
fi

source "$CONF_FILE"

# --- Storage ---------------------------------------------------------
# Never set up the empty mount point on the SD card.
mountpoint -q "$STORAGE_DIR" || die "$STORAGE_DIR is not mounted. Mount the recordings disk first."

log "Setting up $STORAGE_DIR..."
# setgid: new directories inherit the smb_viewer group.
chown nvr:smb_viewer "$STORAGE_DIR"
chmod 2750 "$STORAGE_DIR"

# --- Scripts ---------------------------------------------------------
log "Installing scripts..."

install -m 755 "$SCRIPT_DIR/bin/nvr-record"  /usr/local/bin/nvr-record
install -m 755 "$SCRIPT_DIR/bin/nvr-cleanup" /usr/local/bin/nvr-cleanup
install -m 755 "$SCRIPT_DIR/bin/nvr-status"  /usr/local/bin/nvr-status

# --- systemd units ---------------------------------------------------
log "Installing systemd units..."

install -m 644 "$SCRIPT_DIR/service/nvr@.service"        /etc/systemd/system/
install -m 644 "$SCRIPT_DIR/service/nvr-cleanup.service" /etc/systemd/system/
install -m 644 "$SCRIPT_DIR/service/nvr-cleanup.timer"   /etc/systemd/system/
install -D -m 644 "$SCRIPT_DIR/service/smbd.service.d/override.conf" \
    /etc/systemd/system/smbd.service.d/override.conf

systemctl daemon-reload

# --- Daily cleanup ---------------------------------------------------
log "Enabling the daily cleanup..."
systemctl enable --now nvr-cleanup.timer

# --- Cameras ---------------------------------------------------------
units=()
for cam in $CAMERAS; do
    log "Enabling the service for $cam..."
    systemctl enable "nvr@${cam}.service"
    units+=("nvr@${cam}")
done

# --- Result ----------------------------------------------------------
first_cam="${CAMERAS%% *}"

echo ""
echo -e "${BOLD}✅  Install complete!${NC}"
echo ""
echo "Next steps:"
echo ""
echo "  1. Fill in the camera credentials:"
echo "     sudo nano $CONF_FILE"
echo ""
echo "  2. Start recording:"
echo "     sudo systemctl start ${units[*]}"
echo ""
echo "  3. Check the status:"
echo "     sudo nvr-status"
echo ""
echo "  4. Live logs:"
echo "     journalctl -u nvr@${first_cam} -f"
echo ""
