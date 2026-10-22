# Raspberry Pi services for the camera VPN: MediaMTX, recordings and Samba

The second half of [infra_guide.md](infra_guide.md). That guide builds the router, the VPN and the Pi's firewall (Parts 0–9). This one installs what runs on the Pi: the recordings disk, **MediaMTX** (live video for the app), the **recorder** (24/7 recording to the disk) and **Samba** (read-only access to the recordings for admins).

- **Numbering:** it continues the first guide, from Part 10 and Step 35.
- **Same working rules:** [0.5 Working rules](infra_guide.md#05-working-rules) still apply (placeholders like `<THIS>`, comments in pasted zsh commands).
- **How to use it:** each step is small and ends with a test. Do not move on until the test passes.

---

## Contents

- [Before you start](#before-you-start)
- [Part 10 — Recordings disk](#part-10--recordings-disk) (steps 35–36)
- [Part 11 — MediaMTX](#part-11--mediamtx) (steps 37–39)
- [Part 12 — Recorder (NVR)](#part-12--recorder-nvr) (steps 40–42)
- [Part 13 — Samba](#part-13--samba) (steps 43–45)
- [Day-to-day operations](#day-to-day-operations)

---

## Before you start

### What runs on the Pi

```
 cameras (CAM, 192.168.90.10-.13)
   │ RTSP over TCP 554 (Step 12)
   ├──────────────────────────────┐
   │ on demand                    │ always
┌──┴─────────────┐        ┌───────┴────────┐       ┌──────────────────┐
│ MediaMTX       │        │ recorder       │ MP4   │ USB disk (ext4)  │
│ 8889/TCP WHEP  │        │ nvr@cam_10..13 ├──────►│ /mnt/backup_cams │
│ 8189/UDP ICE   │        │ (ffmpeg)       │       └────────┬─────────┘
│ 8888/TCP HLS   │        └────────────────┘                │ read only
└──┬─────────────┘                                  ┌───────┴────────┐
   │ WebRTC: every VPN peer (the app)               │ Samba 445/TCP  │
   │ HLS: VPN admins (browser)                      │ VPN admins     │
   ▼                                                └────────────────┘
```

| Piece | Where | Listens on | Who reaches it |
|---|---|---|---|
| MediaMTX | `/opt/mediamtx`, user `mediamtx` | 8889/TCP, 8189/UDP, 8888/TCP | Every VPN peer on 8889 and 8189, admins on 8888 |
| Recorder | `/usr/local/bin/nvr-*`, `/etc/nvr`, user `nvr` | nothing | — |
| Recordings disk | `/mnt/backup_cams`, owned by `nvr:smb_viewer` | — | — |
| Samba | `smbd` | 445/TCP | VPN admins, read only, with a password |

These are exactly the ports that the router (Step 19) and ufw (Step 32) already open. Nothing in this guide changes the firewalls.

The recorder pulls RTSP straight from each camera, all the time. MediaMTX pulls only while someone watches. A camera therefore serves **two RTSP sessions** while it is on screen in the app. Most IP cameras allow several; check yours if a stream fails only while it is being recorded.

### What you need

- Parts 0–9 of [infra_guide.md](infra_guide.md) complete, and an **admin** peer working on a computer (the "admin computer"). The Pi is reached only through the VPN: `ssh -i ~/.ssh/cameras_pi <user>@fd00:20::cafe`.
- A USB hard disk for the recordings, with nothing on it that must be kept: Step 35 erases it. A 3.5" disk needs its **own power supply**: the Pi's USB ports cannot power it.
- An account on each camera for the streams, ideally one that can only view. MediaMTX and the recorder use the same one.
- Each camera's main stream in **H.264**. WebRTC on most phones does not play H.265, and the recorder tags its files as H.264. `ffprobe` from Step 13 shows the codec (`codec_name=h264`).

### Copy the files to the Pi

Every configuration file comes from the `nvr-setup/` folder of the repository. The values to fill in are placeholders (`<user>`, `<pass>`). Fill them in **on the Pi**, never in the repository.

```bash
# On the admin computer, from the repository root
scp -i ~/.ssh/cameras_pi -r nvr-setup '<user>@[fd00:20::cafe]:~/'
```

| File | Used in |
|---|---|
| `mediamtx.yml`, `mediamtx.service` | Steps 38–39 |
| `install.sh`, `nvr.conf`, `bin/`, `service/` | Step 40 |
| `smb.conf` | Step 43 |

---

## Part 10 — Recordings disk

The disk is **ext4**. It only holds recordings and is never plugged into another computer (Samba covers that), so it does not need a filesystem that macOS or Windows can read. ext4 has a journal, which matters for a disk that is written 24/7 and may lose power, real owners and permissions, and a check at boot.

### Step 35 — Format the disk as ext4

**a) Find the disk.** Plug in the USB disk and list the block devices:

```bash
# On the Pi
lsblk -o NAME,TYPE,SIZE,FSTYPE,LABEL,MOUNTPOINT,MODEL
```

The output looks like this (names, sizes and models vary):

```
NAME        TYPE  SIZE FSTYPE LABEL   MOUNTPOINT     MODEL
sda         disk  3.6T                               Expansion HDD
└─sda1      part  3.6T ntfs   Seagate /media/pi/...
mmcblk0     disk 29.7G
├─mmcblk0p1 part  512M vfat   bootfs  /boot/firmware
└─mmcblk0p2 part 29.2G ext4   rootfs  /
```

How to read it:

- Each line with `TYPE` **`disk`** is a whole device. The lines below it, drawn with `├─` and `└─`, are its partitions (`TYPE` **`part`**). `sda1` is the first partition **of** `sda`; it is not a folder inside it.
- `mmcblk0` is the SD card the Pi boots from (`/` and `/boot/firmware`). **Never use it.**
- The USB disk is the other `disk`, usually `sda`. Confirm it by its `SIZE` and `MODEL`: unplug it, run `lsblk` again, and it is the line that disappears.

**b) Set `DISK` to the whole disk**, not to a partition: the name from the `disk` line, with `/dev/` in front. In the example above:

```bash
# On the Pi
DISK=/dev/sda                            # the whole disk: not /dev/sda1, not /dev/sda/sda1
lsblk -dno NAME,TYPE,SIZE,MODEL "$DISK"  # sda disk 3.6T Expansion HDD
```

The check must print `disk` and the size and model of the USB disk. If it prints `part`, `DISK` points to a partition: remove the number at the end. The commands below erase the whole disk and create its new partition themselves, which is why they need the disk and not an old partition. They refer to that new partition as `${DISK}1` (`/dev/sda1`), here and in Step 36.

**c) Unmount it if needed.** If a partition of the disk shows a `MOUNTPOINT` (the Pi desktop mounts USB disks automatically under `/media`), unmount it first, for example `sudo umount /dev/sda1`. **Everything on the disk is erased** in the next commands.

**d) Create a single partition and the filesystem:**

```bash
# On the Pi
sudo apt-get install -y parted
sudo wipefs -a "$DISK"?* 2>/dev/null
sudo wipefs -a "$DISK"
sudo parted -s "$DISK" mklabel gpt mkpart backup_cams ext4 0% 100%
sudo udevadm settle
sudo mkfs.ext4 -L backup_cams -m 0 "${DISK}1"
```

| Option | Reason |
|---|---|
| `wipefs -a` | First on each old partition, then on the disk: removes the old filesystem signatures and the partition table, so nothing from a previous filesystem is detected later |
| `udevadm settle` | Waits until the kernel has created the new partition's device (`<disk>1`) before `mkfs` uses it |
| `gpt`, one partition | GPT handles disks over 2 TB. One partition, because the disk has one purpose |
| `-L backup_cams` | A label makes the disk easy to recognize in `lsblk` |
| `-m 0` | ext4 reserves 5% for root by default, which on a recordings disk is only wasted space. The recorder keeps its own free-space margin (`MIN_FREE_SPACE_MB`, Step 41) |

**Test**

```bash
# On the Pi
lsblk -f "$DISK"
# <disk>1  ext4  backup_cams  <uuid>
```

### Step 36 — Mount at boot and protect the mount point

**a) Protect the empty mount point.** While the disk is not mounted, `/mnt/backup_cams` is an ordinary folder on the SD card. Anything written there would fill the SD card, and Samba would share an empty folder. Make that folder immutable while it is **not** mounted. The flag belongs to the folder on the SD card, so it has no effect on the disk once it is mounted on top:

```bash
# On the Pi
sudo mkdir -p /mnt/backup_cams
mountpoint /mnt/backup_cams              # "is not a mountpoint"
ls -A /mnt/backup_cams                   # empty
sudo chattr +i /mnt/backup_cams
```

**b) Add the disk to `/etc/fstab`.** The disk needs one line, identified by the UUID of its partition:

```
UUID=<disk-uuid>  /mnt/backup_cams  ext4  defaults,nofail,noatime,nosuid,nodev,noexec  0  2
```

The commands below read the UUID of the new partition (`DISK` is the one set in Step 35) and append the line. The other lines of `/etc/fstab` (`/proc`, `/boot/firmware` and `/`) stay as they are. Run it **once**: each run appends another line.

```bash
# On the Pi
UUID=$(sudo blkid -s UUID -o value "${DISK}1")
sudo cp /etc/fstab /etc/fstab.bak
echo "UUID=$UUID  /mnt/backup_cams  ext4  defaults,nofail,noatime,nosuid,nodev,noexec  0  2" \
    | sudo tee -a /etc/fstab
sudo findmnt --verify
sudo systemctl daemon-reload
sudo mount /mnt/backup_cams
```

| Option | Reason |
|---|---|
| `nofail` | The Pi boots even without the disk. Without it, a missing disk stops the boot in emergency mode, with no network: no SSH through the VPN and no cameras in the app until someone connects a keyboard and a screen. What must not run without the disk (recorder and Samba) declares that itself (`RequiresMountsFor`, Steps 40 and 43) |
| `noatime` | Reads do not write an access time. Less wear and less I/O on a disk that is busy writing |
| `nosuid,nodev,noexec` | The disk only holds data: nothing on it may run as a program, become a device or gain privileges |
| `0 2` | No `dump`; `fsck` checks the disk at boot, after the root filesystem (`1`) |

The owner and the permissions are set by the installer in Step 40, because the `nvr` user and the `smb_viewer` group do not exist yet.

**Test**

```bash
# On the Pi
grep -c /mnt/backup_cams /etc/fstab      # 1
findmnt /mnt/backup_cams
# /mnt/backup_cams  /dev/<disk>1  ext4  rw,nosuid,nodev,noexec,noatime
sudo umount /mnt/backup_cams
sudo touch /mnt/backup_cams/test         # Operation not permitted (even as root)
sudo mount /mnt/backup_cams
sudo reboot
```

After the reboot, reconnect over SSH and run `findmnt /mnt/backup_cams` again: the disk is mounted.

---

## Part 11 — MediaMTX

MediaMTX pulls RTSP from the cameras and serves it as WebRTC (WHEP) to the app, and as HLS to admins in a browser.

### Step 37 — Install the binary and its user

MediaMTX is a single binary, published on [GitHub](https://github.com/bluenviron/mediamtx/releases). The Pi finds the latest stable release by itself: GitHub redirects `releases/latest` to the page of that release, and the tag is the last part of its address. Pre-releases are never `latest`.

```bash
# On the Pi
VER=$(curl -fsSLI -o /dev/null -w '%{url_effective}' \
    https://github.com/bluenviron/mediamtx/releases/latest)
VER=${VER##*/}                           # keep only the tag
echo "$VER"                              # v1.x.y
```

If `echo` prints `latest` or nothing, the Pi did not reach GitHub: check its internet access (`Rpi -> Internet`, Step 12) and try again.

Pick the archive that matches the Pi's system and download it with its checksums:

```bash
# On the Pi
case "$(uname -m)" in
    aarch64) ARCH=linux_arm64 ;;         # 64-bit system
    armv7l)  ARCH=linux_armv7 ;;         # 32-bit system
    *)       echo "unsupported: $(uname -m)" ;;
esac
echo "$ARCH"
cd /tmp
curl -fLO "https://github.com/bluenviron/mediamtx/releases/download/${VER}/mediamtx_${VER}_${ARCH}.tar.gz"
curl -fLO "https://github.com/bluenviron/mediamtx/releases/download/${VER}/checksums.sha256"
grep "mediamtx_${VER}_${ARCH}.tar.gz" checksums.sha256 | sha256sum -c -
```

`sha256sum -c` must print `OK`. If a download fails with `404`, the release changed its file names: open the release page (`https://github.com/bluenviron/mediamtx/releases/tag/$VER`), and use the names listed there.

Install only the binary. The example configuration in the archive is replaced in Step 38:

```bash
# On the Pi
sudo mkdir -p /opt/mediamtx
sudo tar -xzf "/tmp/mediamtx_${VER}_${ARCH}.tar.gz" -C /opt/mediamtx mediamtx
sudo chown root:root /opt/mediamtx/mediamtx
sudo chmod 755 /opt/mediamtx/mediamtx
sudo useradd --system --no-create-home --shell /usr/sbin/nologin mediamtx
```

| Choice | Reason |
|---|---|
| Binary owned by `root` | The service runs as `mediamtx`, which can run the binary but not change it |
| User `mediamtx` | MediaMTX is the one service that every VPN peer reaches. If it is ever exploited, the attacker gets an account with no shell, no home and no write access anywhere |

**Test**

```bash
# On the Pi
/opt/mediamtx/mediamtx --version       # the version you downloaded
id mediamtx
```

### Step 38 — Configuration

```bash
# On the Pi
sudo install -m 640 -o root -g mediamtx ~/nvr-setup/mediamtx.yml /opt/mediamtx/mediamtx.yml
sudoedit /opt/mediamtx/mediamtx.yml      # replace <user>:<pass> in the four camera URLs
```

The file holds the camera password, so it is readable only by root and the `mediamtx` group. A password with special characters must be URL-encoded inside the URL: `@` becomes `%40`, `:` becomes `%3A`, `/` becomes `%2F`.

The file is minimal: it only lists what differs from MediaMTX's defaults.

| Setting | Reason |
|---|---|
| `udpMaxPayloadSize: 1200` | The tunnel MTU is 1280 (Step 14). An IPv6 packet of 1280 leaves 1280 − 40 (IPv6) − 8 (UDP) = 1232 bytes of payload. 1200 also leaves room for the SRTP overhead, so WebRTC media never needs fragmentation inside the tunnel |
| `publish` only from `127.0.0.1` and `::1` | Every VPN peer reaches 8889, and WebRTC can also publish (WHIP). Without this, any peer could push a stream into the server |
| `read` for any IP | The router and ufw already decide who reaches the Pi. MediaMTX needs no second login for the app |
| `rtsp`, `rtmp`, `srt: false` | Nothing reads or publishes through these protocols. Pulling RTSP from the cameras is done by a client inside MediaMTX and does not need the RTSP server |
| `webrtcIPsFromInterfaces: false`, `webrtcAdditionalHosts: ['fd00:20::cafe']` | The app receives a single ICE candidate: the Pi's fixed address (Step 16). Without this, MediaMTX would also announce the Pi's IPv4 and link-local addresses, which the app cannot reach through the tunnel |
| `sourceOnDemand: yes` | A camera is pulled only while someone watches it, and released 10 s after the last viewer leaves. Saves the cameras' sessions and the Pi's CPU |
| `rtspTransport: tcp` | The router only allows TCP 554 from the Pi to the cameras (Step 12). With the default (`automatic`), MediaMTX would try UDP first, wait for it to fail, and only then use TCP |
| Paths `cam_10` to `cam_13` | One path per camera, named after the last group of its address (`192.168.90.10` → `cam_10`). The name is part of the app's stream URL. `Channels/101` is the camera's main stream |

### Step 39 — Service and test with the app

```bash
# On the Pi
sudo install -m 644 ~/nvr-setup/mediamtx.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now mediamtx
```

The unit runs MediaMTX as `mediamtx`, restarts it if it fails, and restricts what it can touch: no capabilities, a read-only system, no access to `/home` or to devices. Every port is above 1024 and MediaMTX writes nothing to disk, so none of these limits gets in its way.

**Test: the service** (on the Pi)

```bash
systemctl is-active mediamtx                   # active
ps -o user= -C mediamtx                        # mediamtx
sudo ss -tulpn | grep mediamtx
# Only: tcp 8888, tcp 8889, udp 8189
journalctl -u mediamtx -n 20 --no-pager
# Listeners for HLS and WebRTC only. No RTSP, RTMP or SRT. No configuration error
```

An unknown or misspelled setting in `mediamtx.yml` stops MediaMTX at start, with the line in the log. This is the first thing to check after updating MediaMTX to a new version.

**Test: only the Pi publishes** (on the admin computer)

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/sdp' \
    --data 'v=0' 'http://[fd00:20::cafe]:8889/cam_10/whip'
```

Expected: `401` (any `4xx` means refused). `201` would mean that a peer can publish.

**Test: the app.** On a phone with a **client** profile, set the stream URLs `http://[fd00:20::cafe]:8889/cam_10/whep` to `cam_13/whep` (Step 29). Every camera plays. This is also the test of UDP 8189 (WebRTC media), which Step 30 could not check. While a camera is on screen, its path shows up in the log, and about 10 s after leaving the screen it is closed again:

```bash
# On the Pi
journalctl -u mediamtx -f
```

If a stream stays loading forever, check that the Pi still has its fixed address (`ip -6 addr show eth0 | grep fd00:20::cafe`): it is the only address MediaMTX announces.

**Test: HLS for admins.** On the admin computer, open `http://[fd00:20::cafe]:8888/cam_10/` in a browser: the camera plays. From a client profile the same URL times out (router and ufw).

**Optional test: no oversized media packets.** While a camera plays in the app, no UDP packet from 8189 may be larger than the tunnel allows (an IPv6 payload over 1240 bytes):

```bash
# On the Pi
sudo apt-get install -y tcpdump
sudo timeout 30 tcpdump -ni eth0 'udp src port 8189 and ip6[4:2] > 1240'
# 0 packets captured
```

---

## Part 12 — Recorder (NVR)

The recorder is a small set of scripts around `ffmpeg`. One systemd instance per camera (`nvr@cam_10` to `nvr@cam_13`) records the main stream into 15-minute MP4 files, without re-encoding. A daily timer deletes what is older than the retention, or the oldest files when the disk runs low.

### Step 40 — Run the installer

The installer needs the disk mounted (Step 36) and refuses to run otherwise.

```bash
# On the Pi
cd ~/nvr-setup
sudo ./install.sh
```

| What it does | Why |
|---|---|
| Installs `ffmpeg` | Records the streams |
| Creates the system user `nvr` | Writes the recordings. No shell, no home |
| Creates the group `smb_viewer` | Reads the recordings. Samba users join it in Step 44 |
| `/etc/nvr/nvr.conf`, `root:nvr`, `640` | Holds the camera password: readable only by root and the recorder. An existing file is kept, and the new version is saved next to it as `nvr.conf.new` |
| `/mnt/backup_cams`, `nvr:smb_viewer`, `2750` | `nvr` writes, `smb_viewer` reads, others get nothing. The setgid bit (`2`) makes every new folder inherit the `smb_viewer` group |
| Scripts in `/usr/local/bin` | `nvr-record`, `nvr-cleanup`, `nvr-status` |
| Units in `/etc/systemd/system` | `nvr@.service`, `nvr-cleanup.service` and `.timer`, and the `smbd` drop-in used in Part 13 |
| Enables the daily cleanup and one `nvr@` per camera | The cameras do not start yet: `nvr.conf` still has placeholders |

`nvr@.service` adds three protections:

| Setting | Reason |
|---|---|
| `RequiresMountsFor=/mnt/backup_cams` | The recorder starts only with the disk mounted, and systemd mounts it first if needed. Without the disk, the unit fails instead of writing to the SD card |
| `UMask=0027` | `ffmpeg` creates files `640` and folders `750`: the group reads, others get nothing |
| `ProtectSystem=strict`, `ReadWritePaths=/mnt/backup_cams` | The recorder can only write to the disk |

**Test**

```bash
# On the Pi
id nvr
getent group smb_viewer
stat -c '%U:%G %a' /mnt/backup_cams          # nvr:smb_viewer 2750
sudo stat -c '%U:%G %a' /etc/nvr/nvr.conf    # root:nvr 640
systemctl list-timers nvr-cleanup.timer      # next run around 03:00
systemctl is-enabled nvr@cam_10              # enabled
```

### Step 41 — Camera credentials and start

```bash
# On the Pi
sudoedit /etc/nvr/nvr.conf                   # replace <user>:<pass> in the four URLs
```

Use the same account as in `mediamtx.yml`, with the same URL encoding. `nvr.conf` is also read by bash, where `$`, `` ` ``, `"` and `\` are special inside the double quotes: URL-encode them as well (`%24`, `%60`, `%22`, `%5C`). The names in `CAMERAS` (`cam_10` to `cam_13`) match the MediaMTX paths, so a camera has the same name everywhere.

Size the retention to the disk. A camera recording at **N Mbit/s** fills about **N × 10.8 GB per day**. Four cameras at 4 Mbit/s fill about 173 GB/day, so 20 days need about 3.5 TB.

| Setting | Default | Reason |
|---|---|---|
| `SEGMENT_DURATION` | `900` | 15-minute files. Files start on the clock (`:00`, `:15`, ...), which makes a moment easy to find |
| `RETENTION_DAYS` | `20` | Files older than this are deleted every night |
| `MIN_FREE_SPACE_MB` | `204800` | 200 GB. Below it, the oldest files are deleted even inside the retention, at night and before each new recording session. Keep it above one day of recording |
| `RTSP_TRANSPORT` | `tcp` | The router only allows TCP 554 to the cameras |

Start the four recorders:

```bash
# On the Pi
sudo systemctl start nvr@cam_10 nvr@cam_11 nvr@cam_12 nvr@cam_13
```

### Step 42 — Test the recordings and the missing-disk protection

**Recording** (on the Pi, after a couple of minutes)

Your SSH user is neither `nvr` nor in `smb_viewer`, so it cannot read `nvr.conf` or the recordings: the commands that touch them use `sudo`.

```bash
sudo nvr-status                              # 4 cameras active, files growing
journalctl -u nvr@cam_10 -n 10 --no-pager
# "Stream: rtsp://***@192.168.90.10:554/...": the password never reaches the log
sudo ls -l "/mnt/backup_cams/cam_10/$(date +%Y/%m/%d)/"
# -rw-r----- nvr smb_viewer cam_10_<date>_<time>.mp4
sudo ls -ld "/mnt/backup_cams/cam_10/$(date -d tomorrow +%Y/%m/%d)"
# drwxr-s--- nvr smb_viewer: tomorrow's folder already exists
```

`ffmpeg` does not create folders, so a file that starts on a new day needs that day's folder to exist already. The recorder keeps today's and tomorrow's folders in place for as long as it runs, and recreates them within 10 minutes if the daily cleanup deletes an empty one. Check the next morning that the files continue without a gap at midnight (`sudo ls` the new day's folder: the first file starts at `00-00-00`).

When a 15-minute file is complete, check that it plays:

```bash
# On the Pi
sudo ffprobe -v error -show_entries format=duration -of csv=p=0 "<a complete file>"   # about 900
```

**Cleanup**

```bash
# On the Pi
sudo systemctl start nvr-cleanup.service
journalctl -u nvr-cleanup -n 15 --no-pager   # retention, free space and usage per camera
```

**Missing disk.** Stop the recorders, unmount the disk and **unplug it**:

```bash
# On the Pi
sudo systemctl stop nvr@cam_10 nvr@cam_11 nvr@cam_12 nvr@cam_13
sudo umount /mnt/backup_cams
```

```bash
# On the Pi, with the disk unplugged
sudo systemctl start nvr@cam_10              # fails after ~90 s: dependency failed (no disk)
ls -A /mnt/backup_cams                       # empty: nothing went to the SD card
sudo touch /mnt/backup_cams/test             # Operation not permitted
systemctl is-active mediamtx                 # active: the app still shows the cameras
```

Plug the disk back in and start everything again. Starting a recorder mounts the disk first:

```bash
# On the Pi
sudo systemctl start nvr@cam_10 nvr@cam_11 nvr@cam_12 nvr@cam_13
findmnt /mnt/backup_cams
sudo nvr-status
```

The same happens at boot without the disk: the Pi comes up and MediaMTX serves the app, while the recorders (and Samba, Part 13) fail after about 90 seconds. They do not come back on their own when the disk is plugged in: start them as above (and `sudo systemctl start smbd`), or reboot.

---

## Part 13 — Samba

Samba gives admins read-only access to the recordings, with a user and a password, from the admin computer through the VPN.

### Step 43 — Install and configure

```bash
# On the Pi
sudo apt-get install -y samba smbclient
sudo systemctl mask --now nmbd               # NetBIOS is not used
sudo cp /etc/samba/smb.conf /etc/samba/smb.conf.orig
sudo install -m 644 ~/nvr-setup/smb.conf /etc/samba/smb.conf
testparm -s
```

`testparm` must print `Loaded services file OK` and `Server role: ROLE_STANDALONE`. Newer Samba versions show `smb ports` under its new name, `server smb transports`; both work.

**Why each setting**

| Setting | Reason |
|---|---|
| `disable netbios = yes`, `smb ports = 445` | SMB over TCP 445 only. The router and ufw never open the NetBIOS ports (137–139), so `nmbd` has nothing to do. To reach the Pi by name, use the DNS name from Step 22 |
| `server min protocol = SMB3` | No SMB1 or SMB2. Every current macOS, Windows and Linux client speaks SMB3 |
| `hosts allow = fd00:10::a:0/112 ::1 127.0.0.1`, `hosts deny = ALL` | A third check of the source, after the router and ufw, with the same admin block (`vpn-admin`). Localhost is allowed for the tests on the Pi |
| `security = user`, `passdb backend = tdbsam` | Local Samba users with their own passwords (Step 44) |
| `map to guest = never`, `restrict anonymous = 2` | No guest login and no anonymous session: without a password, not even the list of shares is visible |
| Printing off | No printers on this host. Samba does not look for CUPS |
| Share `backup_cams`, `valid users = @smb_viewer`, `read only = yes` | Only members of `smb_viewer` connect, and only to read. The disk permissions (Step 40) give the group read access only, so the filesystem refuses writes too |
| `veto files = /lost+found/` | Hides the folder that ext4 keeps for `fsck` at the root of the disk. Only root can open it |

The `smbd` drop-in installed in Step 40 (`/etc/systemd/system/smbd.service.d/override.conf`) adds `RequiresMountsFor=/mnt/backup_cams`: without the disk, Samba stays down instead of sharing the empty folder on the SD card.

```bash
# On the Pi
sudo systemctl daemon-reload
sudo systemctl restart smbd
```

**Test**

```bash
# On the Pi
systemctl show smbd -p RequiresMountsFor     # /mnt/backup_cams
systemctl is-active smbd                     # active
systemctl is-active nmbd                     # inactive (masked)
sudo ss -tlnp | grep smbd                    # only 445
```

### Step 44 — Samba users

Create one account per admin. It is a Linux account with no shell, no home and no Linux password, so it cannot log in to the Pi. Its only use is Samba, and its only group with access to the recordings is `smb_viewer`:

```bash
# On the Pi
sudo useradd --no-create-home --shell /usr/sbin/nologin -G smb_viewer <name>
sudo smbpasswd -a <name>                     # the Samba password, asked twice
```

Use a long password: it is the only thing between a lost admin device and the recordings.

**Test**

```bash
# On the Pi
sudo pdbedit -L                              # <name>
id <name>                                    # ... smb_viewer
```

**Revoking access.** Remove the Samba user and the Linux account, together with the peer (see "Revoking access" in Part 8):

```bash
# On the Pi
sudo smbpasswd -x <name>
sudo userdel <name>
```

### Step 45 — Test from the Pi and from the admin computer

**On the Pi** (localhost is in `hosts allow`):

```bash
smbclient -N -L //localhost                                  # refused: no anonymous session
smbclient //localhost/backup_cams -U <name> -c 'ls'          # cam_10 ... cam_13
smbclient //localhost/backup_cams -U <name> -c 'put /etc/hostname test.txt'
# NT_STATUS_ACCESS_DENIED: read only
```

**On the admin computer, with the admin tunnel up**, connect to the share:

| System | Address |
|---|---|
| macOS (Finder → Go → Connect to Server) | `smb://[fd00:20::cafe]/backup_cams` or `smb://<site>.cameras.casa/backup_cams` |
| Windows (File Explorer) | `\\fd00-20--cafe.ipv6-literal.net\backup_cams` |
| Linux | `smbclient //<site>.cameras.casa/backup_cams -U <name> -c 'ls'` (the DNS name from Step 22) |

| Test | Expected |
|---|---|
| Log in with the user from Step 44 | ✅ the four camera folders |
| Log in without a password, or as guest | ❌ |
| Copy a recording to the computer | ✅ no stalls (MSS clamp, Step 20) |
| Delete or create a file on the share | ❌ read only |
| The same address from a **client** profile | ❌ timeout (router and ufw) |

---

## Day-to-day operations

| Task | How |
|---|---|
| Overall status | `sudo nvr-status` |
| Logs | `journalctl -u mediamtx -f`, `journalctl -u nvr@cam_10 -f`, `journalctl -u nvr-cleanup` |
| Change the camera password | Change it in **both** `/opt/mediamtx/mediamtx.yml` and `/etc/nvr/nvr.conf`, then `sudo systemctl restart mediamtx nvr@cam_10 nvr@cam_11 nvr@cam_12 nvr@cam_13` |
| Add a camera (for example `192.168.90.14`) | Fixed lease on the router (Step 13); path `cam_14` in `mediamtx.yml`; `cam_14` in `CAMERAS` plus `CAM_14_URL` and `CAM_14_NAME` in `nvr.conf`; `sudo systemctl restart mediamtx`, `sudo systemctl enable --now nvr@cam_14`; the stream URL in the app |
| Update MediaMTX | Download and check the new release as in Step 37, replace `/opt/mediamtx/mediamtx`, `sudo systemctl restart mediamtx`, and read the log for configuration errors (Step 39) |
| Add or remove a Samba user | Step 44 |
